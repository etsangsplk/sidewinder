# Performance, how it's made possible?
**By Ambud, 12/23/17**

Time series databases have a unique challenge of maintaining high read and write throughput. While microsecond latency is not a pre-requisite; they are also expected to be real-time with less than a minute latency between when data is written to when it can be read for operational dash boarding use cases. Also important is the need to writing once and reading several times concurrently.

Sidewinder is a fast open source TSDB, the benchmarks prove this. If you have been curious as to how this speed is achieved while meeting a lot of the above described expectations. Let's take a look at the internals of Sidewinder.

## Architecture
This database was designed with speed in mind because in my past experience other TSDBs were unable to sustain high throughput workloads in large production environments leading to a very challenging operations experience.

Most time series data specially operations data has logarithmic value i.e. over time it looses it's importance eventually becoming a bunch of bytes. This drives the need to have need to keep recent time series data readily available to be queried, visualized and analyzed by operations teams in a fast manner.

Additionally, high write throughput is also expected due to the sheer volume of entities generating time series data in this IoT connected world. This write throughput could potentially be in the millions of data points per second which demands being ingested in real-time so that it can be accessed right away.

Sidewinder uses a design inspired from Kafka to build a time series database from the ground up that serves the highly demanding read and write requirements mentioned above. It uses a combination of efficient sequential access, fast data structures, columnar storage and operating system managed memory to achieve very high read/write performance.

The database is written purely in Java with it's core written using pure Java libraries to keep the dependency footprint small as well the use stable battle tested libraries. It's designed to be to work as a homogenous system where all nodes in a cluster are identical and self organizing with no dependencies on external systems (like zookeeper). This enables operational ease and reduces deployment complexity, a model similar to Cassandra and Elasticsearch.

## The Core
The core of the database contains the following subsystems:
1. Data Model
2. Storage
3. Functions
4. Predicates/Filters
5. APIs

In this post we will focus on the Storage subsystem, responsible for the performance magic of Sidewinder.

### Storage Components
Storage layer of Sidewinder is where all of the performance magic happens. The database is designed to efficiently use both persistent and ephemeral storage media. The key to it's performance is achieved by cache coherent operations and uses several layers of low-level caching constructs to achieve it's speed.

Sidewinder has the hierarchy of Database -> Measurement -> Series. These concepts are used to represent the various layers of organizing time series data.

|  Component       |                                 Notes                              |
|:-----------------|:------------------------------------------------------------------|
|   Storage Engine |        Storage engine implementation used to physically persist the data                |
| Measurement      |        A category, logically equivalent to a table                 |
|    Series        | A combination of tags and value field that uniquely identifies a time series|
|    Buckets       | Bucket of data for a given window of time in a given time series|
|  Reader/Writer   | Modules to Read and Write the time series data                   |
|  Tag Index       | Modules to Index tag data                                         |

Each of these entities also have corresponding abstract interfaces and concrete implementations making it simple to swap these implementations. Broadly there are two categories of implementations:
1. Memory (ram)
2. Persistent (disk)

### Performance Keys
Append only databases are at an inherent performance advantage since they align with sequential IO capabilities of most memories. Sidewinder is an append only time series database. At the heart of which is a memory allocation system capable of allocating byte buffers on demand to requesting time series. These buffers can be purely in-memory or memory mapped. This memory allocation system works with other key features to provide performance. These features include:

#### Sequential Data Access
Reading data sequential is fast. It's pretty much the best one can do from a data access perspective. Each bucket of time series data in Sidewinder is stored and read sequentially. There's absolutely no reorganization (b-tree or lsm) of datapoints done when data is being written or read.

##### Writing
Once a datapoint (timestamp and value) is received, Storage Engine finds which time series bucket it should be written to using the point's metadata (database, measurement, field and tags). The bucket has a writer which will then persist the datapoint in an append-only fashion to it's associated buffer. Of course a configurable light weight compression algorithm is applied to the datapoint before it written and this is the job of the Writer.

##### Reading
Reading is exactly the opposite of Writing i.e. it involves finding the time series bucket using metadata and then replaying the entire buffer via the decompression algorithm which is the job of the Reader. To ensure system being concurrency ready, the Reader reads the snapshot of the write buffer at the point in time read was invoked. This means that between the time reading is invoked to the time reading is complete Writer is free to write additional data. This design is inspired with an MVCC design and needs to duplicate only the write pointer metadata without needing to duplicate the actual data. This functionality allows reads to scale without causing heavy locking for writes.

Another capability of the Reader is it's ability to execute Predicate pushdown. Predicates in this case refers to filters for timestamp and value which can be applied right after a data point is uncompressed. Allowing it to be dropped before any objects are created. Since the decompression process uses reusable primitive variables. If a data point is filtered by the predicate it doesn't generate any garbage and is simply dropped avoiding any need for processing from the query path.

#### Metadata In-Memory
Sidewinder always keeps all metadata is in-memory data structures for fast access random access. Below is the hierarchy of how this metadata is stored and the corresponding data structures used to store it.

- Database (concurrenthashmap)
  - Measurement (concurrenthashmap)
    - Series (concurrentskiplistmap)
      - Buckets (concurrentskiplistmap)
  - TagIndex (concurrenthashmap)

These data structures are extremely performance critical since they are directly in both write and read paths. The quicker they help locate the target data buffers the faster it is to read and write data. Even though this metadata is kept in-memory, in the case of DiskStorageEngine there is a persistent copy which allows these data structures to be rebuilt in case Sidewinder is restarted.

#### Memory Mapped Files
Disks have been much slower than memory, even with solid state media random access is slow. Just like memory sequential data access is fast. Memory Mapped File provides a cheating sheet that allows passive disk persistence option to applications while giving random access via memory and letting the OS perform efficient sequential writes to the disk. Java exposes Memory Mapped Files via MappedByteBuffer interface which works perfectly with the ByteBuffer interface used by Sidewinder allowing compression and persistence to abstract away from the storage media (memory or disk).

#### Columnar Storage
Sidewinder uses independent persistence buckets for storing individual fields for a given measurement, this allows data efficient access of series by reading only fields you need rather than needing to read everything and then applying filters. Multi-series operations are still made possible using functions which can be used for post processing the query results.

Columnar storage does result in duplication of timestamp data and also allows timestamps to be out of sync which can be resolved during post processing.

#### Query Execution
Queries in Sidewinder mostly involve finding the correct time series buckets to read and then simply replaying the required data from the buffers. The actual process is composed of the following steps:
1. Query parsing
2. Extract time range, predicates, filters and functions
3. Finding buckets (filters and time range)
4. Reading data (with predicates)
5. Post processing data with functions

Reading data is an operation that can be configurably performed in parallel improving the query speed by up to 3x. Additionally, data processed by functions is sped-up by using vectorizable for loops which further help improve performance.

## Summary
This was a brief review of Sidewinder's internals. We saw how it uses the ideas of using sequential access and caching to keep up with the demanding requirements of a Time Series use cases.

Sidewinder is under active development, performance is a key quality criteria for all it's features. Will continue to add features to help users get the most out of their time series data.

Remember it's all open source!

[back](./)

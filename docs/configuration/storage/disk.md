### DiskStorageEngine
Disk Storage Engine is a hybrid system where time series data is stored on disk but the series metadata may be stored (cached) in memory. This engine is meant for production use and provides great performance, scalability and persistent storage. DiskStorageEngine heavily relies on OS Page Cache to provide substantially high throughput without requiring the need for Solid State Media.

### Data Storage
Disk storage engine relies on a custom memory allocator that allocates buffers of memory mapped files (aka segments) to the the requesting time series. Each of these buffer buffers are used to store all or parts of a given time series bucket. Each measurement is stored independently and has it's own allocator. This makes it simple drop measurements if needed as that is simply a recursive directory delete operation. Each buffer buffer is stored in a pointer file that let's us know what time series and bucket this buffer belongs to; this information is used to re-construct the in-memory metadata when the instance restarts. Essentially all operations, GC, Compaction, Allocation, Deletion currently happen on the level of a Measurement providing theoretical performance isolation from IO perspective.

Data is stored in segment files each of which is mapped incrementally using OS Page Cache. Both segment size and increment are tunable parameters as is the buffer buffer allocation size. Being familiar with these 3 control knobs is critical to having a performant and stable Sidewinder installation. Here's how these values effect deployments:

##### Max File Size (measurement.file.max)
This is the size of individual data files, each file can be a maximum of 2GB in size. The bigger the segment files the less number of data files are floating around. So if there are too many measurements it may make sense to have large segment files. However, these file can't be partially deleted so if storage is at a premium segment files should be smaller to allow tighter deletion of data by GC. Once a file fills up, it's rotated and the file sequence id is increased. Now the new file is used to allocate buffers. When the garbage collection determines that all buffers from a given file have been deleted, it will delete that data file.

##### File Increment Size (measurement.file.increment)
Segment(data) files can be mapped in increments as more storage is requested. This implies that is the current allocator has no more space left; it will request increment of segment file which involves resizing the file until it reaches the max segment size defined above. Once the file is resized; memory allocation process can start again and buffers can be allocated for new buckets as data is being written.

Segment Resizing is expensive operation as it requires an OS map call. If the increment size is too small for the ingestion rate, segment buffers will run out of space quickly causing a resizing operation which will impact write performance (slow). Having too large segments will require all storage allocated upfront irrespective of whether or not there's need for data to be written. So depending on the write rate, storage resources, GC expectations this increment size should be configured.

##### Buffer Increment Size (measurement.buf.increment)
This is how big (in bytes) a buffer is given to the TimeSeries requesting more memory. To set this value one must consider how frequently data points are written to a measurement e.g. every 1s or 10s and the type of values they have i.e. floating point or non-floating point. As well as how big is the size of an individual time series bucket. The bigger the buffer, more data can be written to it without needing to request more buffer however, if this value is too big for a given bucket of time; most of this buffer will be empty thereby wasting storage and memory. If the value is too small, there will be several buffers needed to store a single bucket and each time a new buffer is requested it has over head and could be expensive depending on whether or not resizing or rotation is needed.

### Tag Storage
Along with the raw time series data which is timestamp and value, there is accompanying metadata with each data points. This includes database name, measurement name, value field name, tags. Everything but the tags is stored in a combination of database and measurement data structures. Tags however require an indexing system of their own to support queries and lookups. In the case of Disk Storage Engine tags are stored persistently on the disk which means tags won't be lost even after a restart. There are a few different tag index implementations.

```
Note: Tag Index implementation MUST be carefully selected since it's speed is the slowest step of writing a datapoint
```

#### Disk Tag Index (Recommended)
Disk Tag Index is the simplest and fastest implementation however it's also the one that uses the most amount of memory. It uses a purely in-memory tag index with a backup persistence to disk so incase of a restart the entire index can be quickly rebuilt. The limitation however is how many tags and index entries can it store. It requires about 4GB memory to store 1 Million tags with a few bytes of data.

#### MapDB Tag Index
This implementation utilizes MapDB library to store indices and is very similar to Disk Tag Index however, it is slower but doesn't have the pitfall of running out of memory like Disk Tag Index.

#### Lucene Tag Index
This implementation uses the Lucene Indexing library to store and query indices. Complex queries can be supported with this (not currently supported). However, this is implementation is fairly slow.

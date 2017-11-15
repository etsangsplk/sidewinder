The heart of Sidewinder is it's Storage Engine, it's what makes reads/writes possible. There are different Storage Engine implementations which can be chosen depending on the use case and environment requirements. In addition to Storage Engine there are different formats data can be stored each with it's own set of trade-offs.

### Types
There are two main classes of Storage Engines:

##### Memory
Memory Storage Engine keeps ALL data in memory (Java heap/off-heap) including the metadata. Therefore, the storage capacity when using MemStorageEngine is limited to the heap. MemStorageEngine MUST have Sidewinder GC enabled and configured correctly else the instance is bound to crash after running out of memory. Note, MemStorageEngine is ephemeral as in, restarting it truncates all data therefore it should be used either purely for development / experimentation use cases or
where retention policies are very tight and substantial amount of heap/off-heap memory is available.
##### Disk
Disk Storage Engine is a hybrid system where time series data is stored on disk but the series metadata may be stored (cached) in memory. This engine is meant for production use and provides great performance, scalability and persistent storage. DiskStorageEngine heavily relies on OS Page Cache to provide substantially high throughput without requiring the need for Solid State Media.

### Common Storage Engine Configuration

|Property                |Description                        |Default|Available Values|
|------------------------|-----------------------------------|-------|------|
|storage.engine  |Storage engine used for actual disk storage by Sidewinder|memory|memory,disk|
|compression.codec|Compression algorithm to use for timeseries data|byzantine|byzatine, gzip, bzip2|
|default.series.retention.hours |Number of hours time series are retained before collecting garbage|218|0-Integer Max Value|
|default.bucket.size|Size of each timeseries bucket in seconds|32768|0-Integer Max Value|

```
Note: default.bucket.size should be adjusted based on frequency of data points in for given time series. The maximum size of the bucket is 2GB which is limited by the size of the memory mapped byte buffer. 2GB translates to 134,217,728 uncompressed data points. Currently this is can be configured at server level only and not per database or measurement. Also the bigger the bucket, the slower the reads (relative) since currently the entire bucket must be scanned to filter data points.
```

### Compression
Compression algorithm is at the heart of all Sidewinder Storage Engines. It's the mechanism used to persist data to the underlying media (memory or disk). The reasons compression is so critical to Sidewinder is because it helps reduce the physical size of data which helps with improving cost efficiency as well as performance. The speed of ingestion and query is reliantly directly on the compression algorithm used, a slower algorithm may provide better compression ratio however will also reduce the rate at which data can be written. Compression is used sequentially in Sidewinder and it's the conversion layer between time series datapoints and bytes on storage media.

Compression algorithms in Sidewinder are also important because these compression codecs do more than converting data back and froth. They are also required to have a built-in MVCC algorithm which provides thread safety and the ability to read the latest copy of data while writes are still happening. It's these compression algorithms that provide the ultimate "low latency" capabilities of Sidewinder.

**Note: Not all compression algorithms in Sidewinder support MVCC capabilities, those that don't have a substantial penalty if reads and writes are concurrent.**

**Note: Compression algorithms are externally pluggable as long as the code is correctly annotated. Refer to the developer sections for more details on this**

More details on compression algorithm designs can be found [here](http://sidewinder.srotya.com/docs/#/designs/compression)

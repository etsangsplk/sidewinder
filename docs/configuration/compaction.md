### Compaction
Sidewinder Compaction is defined as an operation to consolidate series buckets and apply a different compression algorithm if needed to reduce storage space, memory footprint, remove fragmentation of data files and improving caching.

### Configuration

| Property         | Description       | Default          | Available Values  |
|------------------|-------------------|------------------|-------------------|
| compaction.enabled   |Is compaction enabled| false | true/false |
| compaction.delay         |Initial delay after startup before the first Compaction is triggered. This value is in seconds.| 1800 | 0-Integer Max Value |
| compaction.frequency     |What interval to run the Compaction at. This value is in seconds.|1800 | 0-Integer Max Value |
| compaction.ratio | Ratio of size (in bytes) between the compressed buffers before and after. Buffers above this ratio will not be compacted| 0.8 | 0 - Float Max |
| compaction.onstart   |Is compaction performed on starting a Sidewinder instance| false | true/false |

### Potential Issues
Section below represents pointers to avoid a misconfiguration which may lead to a fatal crash.

#### Too many buffers
There can be at most 128 buffers in a given time series bucket at any time. This limit is extracted from the positive range of a byte which is stored as a metadata with each buffer. This information is used to correctly sequence the buffers when recovering Sidewinder.

In general operations with correct configuration, the number of buffers would always be from 10-20 between two compaction cycles. However, there are several misconfiguration scenarios where you may get more uncompacted buffers.

Number of buffers for a bucket is driven by the following factors:
1. Write rate
2. Buffer size
3. Bucket size
4. Compaction frequency
5. Jitter / compression ratio

The speed at which a single write buffer fills up depends on how compressible the data is (less jitter means better compression) and how frequently writes are coming to the buffer. Therefore, if the data is enough data being written to a bucket and the buffer size is not big enough for it to handle a fair chunk of the bucket's data and compaction's are not running frequently then you could have a large number of buckets if the compression ratio is not good.

Compression ratio is not something that can be controlled manually other than ensuring that source data has minimal jitter. Neither can write rate be controlled since that depends on the use case however, the other variable can be tuned to make sure this is not an issue.

Buffer size is the size of individual buffers that Malloc creates when a Time Series bucket requests a buffer to write data. This value should be kept reasonably high that frequent Malloc is not needed however small enough to not waste storage for the given time series bucket size and the frequency of data. Buffer size should be tuned in conjunction with bucket size i.e. the window of time stored in a given bucket. e.g. if the frequency of data is every 10s, jitter is minimal i.e. we expect data points to arrive exactly 10 seconds apart, then in every hour you should get 360 points. Raw byte size that will cost 5760 without compression, without jitter it should cut the buffer in half at least 2880(v) + 360(t) = 3240 considering values couldn't be compressed (worst case scenario). So for every hour you need at least 3240 bytes, let's add a little more overhead for bytes stored and if compression is too little. 3240 + 120 = 3360; this should be a good number for storing 1 hour's data without having to call another Malloc given the above assumptions are correct. The default buffer size of is 32768 bytes and default time bucket size is also 32768 seconds which is roughly 9.1 hours whereas based on our 3360 byte/hr math; 32768 should have enough size to store 9.75 hours. Practically, these ratios vary therefore producing multiple buffers which get cleaned up using background compaction service.

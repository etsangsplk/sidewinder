Garbage Collection is a common term used in high level languages that refers to cleaning up data/memory that is no longer
needed.

The concept of Garbage Collection (GC for short) in Sidewinder is defined as deleting data that has exceeded the
defined series retention policy.

### Retention Policy
It's the amount of time in hours for which data will be kept in Sidewinder.

Under the hood; retention policy is defined in multiples of time series buckets for a series i.e. the minimum amount of
data that must be preserved is 1 bucket. Where 1 bucket is defined as default.bucket.size or configured explicitly at a database
or measurement level.

### Need for retention policy
Simply because most companies don't have infinite resources to store data i.e. disk and memory capacity is limited.
Additionally, storing infinite data is often unnecessary for most use cases, specially the ones with real-time needs.

### Garbage Collection
Retention policies are enforced through the process of Garbage Collection in Sidewinder. GC is not only responsible for deleting
references to time buckets that have exceeded the retention policy and also reclaiming storage space from data segment files.

Garbage Collection in Sidewinder is triggered by a configurable frequency and delay and is a blocking operation per time series bucket.
In most cases GC is expected to be super fast therefore there shouldn't be significant prolonged impact on write throughput beyond the scope of a few seconds.

**Note: GC is run sequentially and not in parallel**

### Limitations
Currently Sidewinder GC doesn't cleanup tags from tag index therefore tags will perpetually increase in size as more tags or series are added.

### Configuration
Below are the configurations for tuning Sidewinder Garbage Collection.

| Property         | Description       | Default          | Available Values  |
|------------------|-------------------|------------------|-------------------|
| gc.delay         |Initial delay after startup before the first GC is triggered. This value is in milliseconds.| 60000 | 0-Integer Max Value |
| gc.frequency     |What interval to run the GC at. This value is in milliseconds.|500000 | 0-Integer Max Value |
| default.series.retention.hours | What's the minimum number of hours series data should be retained.| 218 | 0 - Integer Max Value |
| archiver.class   |Where to archive the data after GC is completed.| None | - |

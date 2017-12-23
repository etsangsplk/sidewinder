### Compaction
Sidewinder Compaction is defined as an operation to consolidate series buckets and apply a different compression algorithm if needed to reduce storage space, memory footprint, remove fragmentation of data files and improving caching.

#### Enabling Compaction (compaction.enabled)
Compaction is purely optional and can be disabled / enabled with this boolean flag.

#### Compaction Frequency
If compaction is enabled, it's frequency should also be correctly configured. This is the

### MemStorageEngine
Memory Storage Engine keeps ALL data in memory (Java heap) including the metadata. Therefore, the storage capacity when using MemStorageEngine is limited to the heap. MemStorageEngine MUST have Sidewinder GC enabled and configured correctly else the instance is bound to crash after running out of memory. Note, MemStorageEngine is ephemeral as in, restarting it truncates all data therefore it should be used either purely for development / experimentation use cases or
where retention policies are very tight and substantial amount of heap memory is available.

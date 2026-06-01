package com.felix.miraagent.memory;

public interface SerializedMemoryWriter {
    MemoryWriteResult submit(MemoryWriteRequest request);
    void shutdown();
}

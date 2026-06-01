package com.felix.miraagent.memory;

public interface SerializedMemoryWriter {
    MemoryWriteResult submit(MemoryWriteRequest request);
    MemoryWriteResult archive(String userId, String memoryId);
    void shutdown();
}

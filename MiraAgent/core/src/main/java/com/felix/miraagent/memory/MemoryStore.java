package com.felix.miraagent.memory;

public interface MemoryStore {
    MemoryWriteResult write(MemoryWriteRequest request);
    void archive(String userId, String memoryId);
    String readFile(String userId, String fileRelativePath);
}

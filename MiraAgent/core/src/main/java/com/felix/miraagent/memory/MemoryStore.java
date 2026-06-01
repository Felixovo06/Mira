package com.felix.miraagent.memory;

public interface MemoryStore {
    MemoryWriteResult write(MemoryWriteRequest request);
    MemoryWriteResult archive(String userId, String memoryId);
    String readFile(String userId, String fileRelativePath);
}

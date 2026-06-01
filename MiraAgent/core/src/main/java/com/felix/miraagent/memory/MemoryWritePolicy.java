package com.felix.miraagent.memory;

public interface MemoryWritePolicy {
    boolean isAllowed(String userId, MemoryCategory category);
    void banCategory(String userId, MemoryCategory category);
}

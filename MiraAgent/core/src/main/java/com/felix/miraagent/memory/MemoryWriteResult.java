package com.felix.miraagent.memory;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MemoryWriteResult {
    String memoryId;
    String filePath;
    boolean success;
    String error;
}

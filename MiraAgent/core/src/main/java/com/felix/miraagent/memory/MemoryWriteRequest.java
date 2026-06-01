package com.felix.miraagent.memory;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MemoryWriteRequest {
    String memoryId;
    String userId;
    String characterId;
    MemoryScope scope;
    MemoryCategory category;
    String content;
    String sourceSessionId;
    String sourceMessageId;
    String sourceTraceId;
    @Builder.Default
    int confidence = 80;
    boolean archived;
}

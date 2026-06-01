package com.felix.miraagent.agent.compression;

import com.felix.miraagent.memory.MemoryWriteRequest;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class CompressionSummary {
    String checkpointId;
    String sessionId;
    String summaryText;
    List<MemoryWriteRequest> memoryWrites;
    String firstRemovedMessageId;
    String lastRemovedMessageId;
    Instant createdAt;
}

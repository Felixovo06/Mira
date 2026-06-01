package com.felix.miraagent.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class TraceEvent {
    String id;
    String runId;
    String sessionId;
    int stepIndex;
    TraceEventType eventType;
    Map<String, Object> payload;
    @Builder.Default
    Instant createdAt = Instant.now();
}

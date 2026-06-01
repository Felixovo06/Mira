package com.felix.miraagent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class Message {
    String id;
    MessageRole role;
    String content;
    @Singular
    List<ToolCall> toolCalls;
    String toolCallId;
    String toolName;
    @Builder.Default
    Instant createdAt = Instant.now();
}

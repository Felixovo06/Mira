package com.felix.miraagent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ChatResponse {
    Message assistantMessage;
    @Singular
    List<ToolCall> toolCalls;
    String finishReason;
    UsageInfo usage;
    String rawResponse;
    ModelException error;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasError() {
        return error != null;
    }
}

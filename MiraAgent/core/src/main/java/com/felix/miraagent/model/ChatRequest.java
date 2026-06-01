package com.felix.miraagent.model;

import com.felix.miraagent.tools.ToolDefinition;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class ChatRequest {
    @Singular
    List<Message> messages;
    @Singular
    List<ToolDefinition> tools;
    String toolChoice;
    Double temperature;
    Integer maxTokens;
    @Builder.Default
    boolean stream = false;
    Map<String, Object> metadata;
}

package com.felix.miraagent.agent;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class ChatInput {
    String userId;
    String sessionId;
    String characterId;
    String content;
    @Singular
    List<String> enabledTools;
    String model;
    @Builder.Default
    boolean stream = false;
    Map<String, Object> metadata;
}

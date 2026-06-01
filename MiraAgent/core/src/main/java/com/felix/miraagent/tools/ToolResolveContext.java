package com.felix.miraagent.tools;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class ToolResolveContext {
    String userId;
    String sessionId;
    @Singular
    Set<String> enabledToolNames;
}

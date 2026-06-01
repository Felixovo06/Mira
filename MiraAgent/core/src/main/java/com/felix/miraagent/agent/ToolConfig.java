package com.felix.miraagent.agent;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class ToolConfig {
    @Singular
    Set<String> enabledToolNames;
}

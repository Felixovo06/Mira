package com.felix.miraagent.tools;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ToolDefinition {
    String name;
    String description;
    Map<String, Object> inputSchema;
    @Builder.Default
    ToolRiskLevel riskLevel = ToolRiskLevel.LOW;
    @Builder.Default
    boolean enabledByDefault = true;
}

package com.felix.miraagent.tools;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ToolHandler {
    ToolExecutionResult execute(String toolCallId, JsonNode arguments);
}

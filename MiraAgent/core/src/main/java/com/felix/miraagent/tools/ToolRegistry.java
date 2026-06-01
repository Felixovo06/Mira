package com.felix.miraagent.tools;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    void register(ToolDefinition definition, ToolHandler handler);

    List<ToolDefinition> listAvailable(ToolResolveContext context);

    Optional<ToolHandler> findHandler(String toolName);

    Optional<ToolDefinition> findDefinition(String toolName);
}

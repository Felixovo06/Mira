package com.felix.miraagent.tools;

public interface ToolPermissionPolicy {
    boolean isAllowed(ToolDefinition tool, ToolDispatchContext context);
}

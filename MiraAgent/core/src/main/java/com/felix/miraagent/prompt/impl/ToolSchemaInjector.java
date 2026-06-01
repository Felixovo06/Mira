package com.felix.miraagent.prompt.impl;

import com.felix.miraagent.tools.ToolDefinition;

import java.util.List;

public class ToolSchemaInjector {

    public String inject(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("# Available Tools\n\n");
        for (ToolDefinition tool : tools) {
            sb.append("- **").append(tool.getName()).append("**: ").append(tool.getDescription());
            sb.append(" [risk: ").append(tool.getRiskLevel().name().toLowerCase()).append("]");
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}

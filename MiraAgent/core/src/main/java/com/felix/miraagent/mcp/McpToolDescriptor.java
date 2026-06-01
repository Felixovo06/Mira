package com.felix.miraagent.mcp;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 从 MCP server {@code tools/list} 发现的单个工具描述。
 */
@Value
@Builder
public class McpToolDescriptor {
    /** server 端原始工具名。 */
    String name;
    String description;
    /** JSON Schema（已转为 Map，可直接注入模型工具定义）。 */
    Map<String, Object> inputSchema;
}

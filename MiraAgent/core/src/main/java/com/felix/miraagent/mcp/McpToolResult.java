package com.felix.miraagent.mcp;

import lombok.Builder;
import lombok.Value;

/**
 * MCP {@code tools/call} 的归一化结果：拼接后的文本内容 + 是否错误。
 */
@Value
@Builder
public class McpToolResult {
    String text;
    @Builder.Default
    boolean isError = false;

    public static McpToolResult ok(String text) {
        return McpToolResult.builder().text(text).isError(false).build();
    }

    public static McpToolResult error(String text) {
        return McpToolResult.builder().text(text).isError(true).build();
    }
}

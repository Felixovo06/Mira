package com.felix.miraagent.mcp;

/**
 * MCP 传输 / 协议层异常。不允许穿透到 ConversationLoop——
 * 在 {@link McpToolAdapter} 中会被转为 {@code ToolExecutionResult(status=error)}。
 */
public class McpException extends RuntimeException {
    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}

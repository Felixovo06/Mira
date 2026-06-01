package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 单个 MCP server 的客户端门面：握手、列工具、调工具。
 */
public interface McpClient extends AutoCloseable {

    /** 该 client 对应的 server 配置。 */
    McpServerConfig config();

    /** 执行 MCP 握手（initialize + notifications/initialized），幂等。 */
    void initialize() throws McpException;

    /** {@code tools/list}：发现该 server 暴露的全部工具。 */
    List<McpToolDescriptor> listTools() throws McpException;

    /** {@code tools/call}：调用一个工具并返回归一化结果。 */
    McpToolResult callTool(String name, JsonNode arguments) throws McpException;

    @Override
    void close();
}

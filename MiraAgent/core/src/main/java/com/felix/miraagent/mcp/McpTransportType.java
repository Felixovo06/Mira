package com.felix.miraagent.mcp;

/**
 * MCP 传输类型。STDIO 启动子进程经标准输入输出走 JSON-RPC（官方主流），
 * HTTP 经 streamable HTTP 端点走 JSON-RPC。
 */
public enum McpTransportType {
    STDIO,
    HTTP
}

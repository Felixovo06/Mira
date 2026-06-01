package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 传输抽象。实现负责 id 分配、请求/响应配对与底层 IO，
 * 让 {@link McpClient} 与 stdio / HTTP / 内存 等具体传输解耦（便于单测）。
 */
public interface JsonRpcTransport extends AutoCloseable {

    /**
     * 发送一个 JSON-RPC 请求并等待响应。
     *
     * @return 响应中的 {@code result} 节点
     * @throws McpException 传输失败或服务端返回 JSON-RPC error
     */
    JsonNode request(String method, JsonNode params) throws McpException;

    /**
     * 发送一个 JSON-RPC 通知（无 id，不等待响应）。
     */
    void notify(String method, JsonNode params) throws McpException;

    @Override
    void close();
}

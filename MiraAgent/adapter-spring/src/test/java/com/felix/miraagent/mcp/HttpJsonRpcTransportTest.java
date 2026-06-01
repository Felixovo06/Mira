package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpJsonRpcTransportTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void requestSendsConfiguredCustomHeaders() throws IOException {
        // Exa 等远程 MCP 用 x-api-key 鉴权：headers 必须随每次 POST 发出
        AtomicReference<String> seenApiKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            seenApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/mcp";
        HttpJsonRpcTransport transport = new HttpJsonRpcTransport(
                "exa", url, new ObjectMapper(), Map.of("x-api-key", "secret-key"));

        JsonNode result = transport.request("tools/list", null);

        assertEquals("secret-key", seenApiKey.get(), "自定义 x-api-key 头应随请求发出");
        assertEquals(true, result.path("ok").asBoolean());
    }

    @Test
    void requestWithoutHeadersSendsNone() throws IOException {
        AtomicReference<String> seenApiKey = new AtomicReference<>("present");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            seenApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/mcp";
        HttpJsonRpcTransport transport = new HttpJsonRpcTransport("plain", url, new ObjectMapper());

        transport.request("tools/list", null);

        assertNull(seenApiKey.get(), "未配置 headers 时不应发出 x-api-key");
    }

    @Test
    void notifyToleratesEmptyAcceptedResponse() throws IOException {
        // streamable HTTP server 对通知返回 202 空体，notify 不能按结果解析而抛错
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            exchange.sendResponseHeaders(202, -1); // 202, 无响应体
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/mcp";
        HttpJsonRpcTransport transport = new HttpJsonRpcTransport("notif", url, new ObjectMapper());

        assertDoesNotThrow(() -> transport.notify("notifications/initialized", null));
    }

    @Test
    void sessionIdFromResponseIsResentOnNextRequest() throws IOException {
        // 有状态 server：initialize 下发 mcp-session-id，后续请求须回传
        AtomicReference<String> firstSeen = new AtomicReference<>("present");
        AtomicReference<String> secondSeen = new AtomicReference<>();
        boolean[] first = {true};
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            String sid = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (first[0]) {
                first[0] = false;
                firstSeen.set(sid);
                exchange.getResponseHeaders().add("Mcp-Session-Id", "sess-42");
            } else {
                secondSeen.set(sid);
            }
            byte[] resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/mcp";
        HttpJsonRpcTransport transport = new HttpJsonRpcTransport("stateful", url, new ObjectMapper());

        transport.request("initialize", null);
        transport.request("tools/list", null);

        assertNull(firstSeen.get(), "首次请求无 session id");
        assertEquals("sess-42", secondSeen.get(), "拿到 session id 后应在后续请求回传");
    }
}

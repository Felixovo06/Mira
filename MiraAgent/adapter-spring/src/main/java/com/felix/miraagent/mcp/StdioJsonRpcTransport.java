package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * stdio 传输：启动子进程，按行分隔的 JSON-RPC 经 stdin/stdout 通信（MCP 官方主流方式）。
 * <p>请求串行化（synchronized，同一时刻仅一个 in-flight），按 id 配对响应，
 * 跳过服务端穿插的通知；stderr 后台 drain 进日志，避免缓冲区阻塞。
 */
public class StdioJsonRpcTransport implements JsonRpcTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioJsonRpcTransport.class);

    private final ObjectMapper mapper;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final AtomicLong idSeq = new AtomicLong(0);
    private final String serverId;

    public StdioJsonRpcTransport(String serverId, String command, List<String> args,
                                 Map<String, String> env, ObjectMapper mapper) {
        this.serverId = serverId;
        this.mapper = mapper;
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(command);
            if (args != null) {
                cmd.addAll(args);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (env != null && !env.isEmpty()) {
                pb.environment().putAll(env);
            }
            this.process = pb.start();
            this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            drainStderr();
        } catch (IOException e) {
            throw new McpException("Failed to start MCP stdio server '" + serverId + "': " + e.getMessage(), e);
        }
    }

    private void drainStderr() {
        Thread t = new Thread(() -> {
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    log.debug("[mcp:{} stderr] {}", serverId, line);
                }
            } catch (IOException ignored) {
                // process ended
            }
        }, "mcp-stderr-" + serverId);
        t.setDaemon(true);
        t.start();
    }

    @Override
    public synchronized JsonNode request(String method, JsonNode params) throws McpException {
        long id = idSeq.incrementAndGet();
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.set("params", params);
        }
        writeLine(req);

        // 读取直到拿到匹配 id 的响应（跳过通知 / 其它 id）
        while (true) {
            String line = readLine();
            JsonNode node;
            try {
                node = mapper.readTree(line);
            } catch (IOException e) {
                throw new McpException("Invalid JSON from MCP server '" + serverId + "': " + line, e);
            }
            if (!node.has("id") || node.get("id").asLong() != id) {
                continue;
            }
            if (node.has("error") && !node.get("error").isNull()) {
                JsonNode err = node.get("error");
                throw new McpException("MCP server '" + serverId + "' error: "
                        + err.path("message").asText(err.toString()));
            }
            return node.path("result");
        }
    }

    @Override
    public synchronized void notify(String method, JsonNode params) throws McpException {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        if (params != null) {
            req.set("params", params);
        }
        writeLine(req);
    }

    private void writeLine(JsonNode node) {
        try {
            stdin.write(mapper.writeValueAsString(node));
            stdin.write("\n");
            stdin.flush();
        } catch (IOException e) {
            throw new McpException("Failed to write to MCP server '" + serverId + "'", e);
        }
    }

    private String readLine() {
        try {
            String line = stdout.readLine();
            if (line == null) {
                throw new McpException("MCP server '" + serverId + "' closed the connection");
            }
            return line;
        } catch (IOException e) {
            throw new McpException("Failed to read from MCP server '" + serverId + "'", e);
        }
    }

    @Override
    public void close() {
        try {
            stdin.close();
        } catch (IOException ignored) {
        }
        if (process.isAlive()) {
            process.destroy();
        }
    }
}

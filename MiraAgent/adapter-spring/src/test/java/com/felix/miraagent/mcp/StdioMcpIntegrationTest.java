package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.tools.ToolDispatchContext;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolStatus;
import com.felix.miraagent.tools.impl.DefaultToolDispatcher;
import com.felix.miraagent.tools.impl.DefaultToolPermissionPolicy;
import com.felix.miraagent.tools.impl.InMemoryToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实 stdio MCP server 联调：启动 scripts/mcp/echo_mcp_server.py 子进程，
 * 走真 JSON-RPC 完成 initialize/tools/list/tools/call，并经统一 ToolRegistry+Dispatcher 派发。
 * 环境无 python3 时优雅跳过。
 */
class StdioMcpIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Path locateServerScript() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("scripts/mcp/echo_mcp_server.py");
            if (Files.exists(candidate)) {
                return candidate;
            }
            // 兼容从根 reactor 运行：MiraAgent/scripts/...
            Path nested = dir.resolve("MiraAgent/scripts/mcp/echo_mcp_server.py");
            if (Files.exists(nested)) {
                return nested;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private boolean python3Available() {
        try {
            Process p = new ProcessBuilder("python3", "--version").start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private DefaultMcpClient connect(Path script) {
        StdioJsonRpcTransport transport = new StdioJsonRpcTransport(
                "echo", "python3", List.of(script.toString()), null, mapper);
        McpServerConfig config = McpServerConfig.builder().id("echo").build();
        return new DefaultMcpClient(config, transport, mapper);
    }

    @Test
    void realStdioServerDiscoversAndCallsTools() {
        assumeTrue(python3Available(), "python3 not available");
        Path script = locateServerScript();
        assumeTrue(script != null, "echo_mcp_server.py not found");

        DefaultMcpClient client = connect(script);
        try {
            client.initialize();
            List<McpToolDescriptor> tools = client.listTools();
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("echo")));
            assertTrue(tools.stream().anyMatch(t -> t.getName().equals("add")));

            ObjectNode echoArgs = mapper.createObjectNode();
            echoArgs.put("message", "real-link");
            assertEquals("echo: real-link", client.callTool("echo", echoArgs).getText());

            ObjectNode addArgs = mapper.createObjectNode();
            addArgs.put("a", 3);
            addArgs.put("b", 4);
            assertEquals("7", client.callTool("add", addArgs).getText());
        } finally {
            client.close();
        }
    }

    @Test
    void realMcpToolFlowsThroughUnifiedDispatcher() {
        assumeTrue(python3Available(), "python3 not available");
        Path script = locateServerScript();
        assumeTrue(script != null, "echo_mcp_server.py not found");

        DefaultMcpClient client = connect(script);
        try {
            var registry = new InMemoryToolRegistry();
            var registered = new McpToolRegistryBridge().registerAll(List.of(client), registry);
            assertTrue(registered.size() >= 3);

            var dispatcher = new DefaultToolDispatcher(registry);
            ToolCall call = ToolCall.builder()
                    .id("c1").name("mcp__echo__echo")
                    .arguments("{\"message\":\"via-dispatcher\"}")
                    .build();
            ToolDispatchContext ctx = ToolDispatchContext.builder()
                    .runId("r1").permissionPolicy(new DefaultToolPermissionPolicy()).build();

            ToolExecutionResult result = dispatcher.dispatchOne(call, ctx);
            assertEquals(ToolStatus.SUCCESS, result.getStatus());
            assertEquals("echo: via-dispatcher", result.getModelVisibleContent());
        } finally {
            client.close();
        }
    }
}

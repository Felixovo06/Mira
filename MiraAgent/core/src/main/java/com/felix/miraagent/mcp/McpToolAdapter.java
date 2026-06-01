package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把单个 MCP 工具适配为内部 {@link ToolHandler}，注册进统一 ToolRegistry。
 * <p>不做任何旁路：权限、trace、tool_executions 记录、长结果 artifact 外置
 * 都由 DefaultToolDispatcher / ConversationLoop 中央统一处理。本类只负责
 * 把模型给的 JSON 参数转发给 MCP server 并把结果归一化，异常一律转 error 结果。
 */
public class McpToolAdapter implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private final McpClient client;
    /** server 端原始工具名（用于 tools/call）。 */
    private final String remoteToolName;
    /** 暴露给模型/registry 的命名空间化工具名。 */
    private final String exposedName;

    public McpToolAdapter(McpClient client, String remoteToolName, String exposedName) {
        this.client = client;
        this.remoteToolName = remoteToolName;
        this.exposedName = exposedName;
    }

    @Override
    public ToolExecutionResult execute(String toolCallId, JsonNode arguments) {
        try {
            McpToolResult result = client.callTool(remoteToolName, arguments);
            if (result.isError()) {
                return ToolExecutionResult.error(toolCallId, exposedName,
                        result.getText() == null || result.getText().isBlank()
                                ? "MCP tool reported an error" : result.getText());
            }
            return ToolExecutionResult.success(toolCallId, exposedName, result.getText());
        } catch (Exception e) {
            log.warn("MCP tool call failed tool={} remote={}", exposedName, remoteToolName, e);
            return ToolExecutionResult.error(toolCallId, exposedName, e.getMessage());
        }
    }
}

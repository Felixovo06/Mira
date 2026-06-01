package com.felix.miraagent.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import com.felix.miraagent.tools.ToolRiskLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 文档读取工具（MEDIUM，沙箱内只读）：Tika 解析 PDF/Word/Excel/PPT/HTML 等为纯文本。
 * 长结果由 ConversationLoop 中央外置为 artifact。
 */
public class DocumentReadToolHandler implements ToolHandler {

    public static final String NAME = "document_read";
    private static final int MAX_CHARS = 200_000;

    private final FileSandbox sandbox;

    public DocumentReadToolHandler(String baseDir) {
        this.sandbox = new FileSandbox(baseDir);
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Extract text from a document (PDF, Word, Excel, PowerPoint, HTML, RTF, "
                        + "ODF, plain text, etc.) in the sandboxed workspace by relative path.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string",
                                        "description", "Relative path of the document within the workspace")),
                        "required", new String[]{"path"}))
                .riskLevel(ToolRiskLevel.MEDIUM)
                .build();
    }

    @Override
    public ToolExecutionResult execute(String toolCallId, JsonNode arguments) {
        String relative = arguments.path("path").asText("");
        try {
            Path file = sandbox.resolve(relative);
            if (!Files.exists(file) || Files.isDirectory(file)) {
                return ToolExecutionResult.error(toolCallId, NAME, "Document not found: " + relative);
            }
            String text = DocumentCodec.extractText(file, MAX_CHARS);
            if (text.isEmpty()) {
                return ToolExecutionResult.success(toolCallId, NAME,
                        "[No extractable text in " + relative + "]");
            }
            return ToolExecutionResult.success(toolCallId, NAME, text);
        } catch (IllegalArgumentException e) {
            return ToolExecutionResult.error(toolCallId, NAME, e.getMessage());
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, NAME, "Parse failed: " + e.getMessage());
        }
    }
}

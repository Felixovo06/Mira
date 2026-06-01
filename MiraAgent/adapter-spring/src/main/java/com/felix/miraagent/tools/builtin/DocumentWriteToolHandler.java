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
 * 文档写入/编辑工具（MEDIUM）：在沙箱内生成 docx/xlsx/txt/md/csv。
 * <p>风险定为 MEDIUM 而非 file_write 的 HIGH：本工具仅向文档工作区写入受限的文档格式
 * （非脚本/系统文件），且经同一沙箱防穿越，是文档处理 agent 的核心能力，默认放行。
 * 编辑既有文档遵循「先 document_read 取文本 → 修改 → document_write 写回」。
 */
public class DocumentWriteToolHandler implements ToolHandler {

    public static final String NAME = "document_write";

    private final FileSandbox sandbox;

    public DocumentWriteToolHandler(String baseDir) {
        this.sandbox = new FileSandbox(baseDir);
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Create or overwrite a document in the sandboxed workspace. Format is inferred "
                        + "from the extension: .docx (Word; use #/##/### for headings, - for bullets), "
                        + ".xlsx (Excel; provide CSV text, one row per line), or .txt/.md/.csv (written as-is). "
                        + "To edit an existing document, read it first, modify the text, then write it back.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string",
                                        "description", "Relative path with extension (.docx/.xlsx/.txt/.md/.csv)"),
                                "content", Map.of("type", "string",
                                        "description", "Document body. For .xlsx, CSV text (one row per line).")),
                        "required", new String[]{"path", "content"}))
                .riskLevel(ToolRiskLevel.MEDIUM)
                .build();
    }

    @Override
    public ToolExecutionResult execute(String toolCallId, JsonNode arguments) {
        String relative = arguments.path("path").asText("");
        String content = arguments.path("content").asText("");
        try {
            Path file = sandbox.resolve(relative);
            DocumentCodec.WriteFormat format = DocumentCodec.WriteFormat.fromPath(relative);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String summary = DocumentCodec.write(file, format, content);
            return ToolExecutionResult.success(toolCallId, NAME,
                    "Saved '" + relative + "' (" + summary + ")");
        } catch (IllegalArgumentException e) {
            return ToolExecutionResult.error(toolCallId, NAME, e.getMessage());
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, NAME, "Write failed: " + e.getMessage());
        }
    }
}

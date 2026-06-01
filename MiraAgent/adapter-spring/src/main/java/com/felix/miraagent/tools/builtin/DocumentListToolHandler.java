package com.felix.miraagent.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import com.felix.miraagent.tools.ToolRiskLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文档列举工具（LOW）：列出沙箱工作区里可读写的文件，让模型知道有哪些文档可处理。
 */
public class DocumentListToolHandler implements ToolHandler {

    public static final String NAME = "document_list";

    private final FileSandbox sandbox;

    public DocumentListToolHandler(String baseDir) {
        this.sandbox = new FileSandbox(baseDir);
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("List the documents available in the sandboxed workspace (name and size).")
                .inputSchema(Map.of("type", "object", "properties", Map.of()))
                .riskLevel(ToolRiskLevel.LOW)
                .build();
    }

    @Override
    public ToolExecutionResult execute(String toolCallId, JsonNode arguments) {
        try {
            Path base = sandbox.baseDir();
            if (!Files.isDirectory(base)) {
                return ToolExecutionResult.success(toolCallId, NAME, "[workspace is empty]");
            }
            try (Stream<Path> entries = Files.list(base)) {
                String listing = entries
                        .filter(Files::isRegularFile)
                        .sorted()
                        .map(p -> "- " + p.getFileName() + " (" + sizeOf(p) + ")")
                        .collect(Collectors.joining("\n"));
                return ToolExecutionResult.success(toolCallId, NAME,
                        listing.isEmpty() ? "[workspace is empty]" : listing);
            }
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, NAME, "List failed: " + e.getMessage());
        }
    }

    private static String sizeOf(Path p) {
        try {
            long bytes = Files.size(p);
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return (bytes / 1024) + " KB";
            }
            return (bytes / (1024 * 1024)) + " MB";
        } catch (Exception e) {
            return "?";
        }
    }
}

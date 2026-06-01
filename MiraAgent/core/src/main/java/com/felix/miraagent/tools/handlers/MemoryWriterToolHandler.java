package com.felix.miraagent.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryScope;
import com.felix.miraagent.memory.MemoryWriteRequest;
import com.felix.miraagent.memory.MemoryWriteResult;
import com.felix.miraagent.memory.SerializedMemoryWriter;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolDispatchContext;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import com.felix.miraagent.tools.ToolRiskLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MemoryWriterToolHandler implements ToolHandler {

    private final SerializedMemoryWriter writer;

    public MemoryWriterToolHandler(SerializedMemoryWriter writer) {
        this.writer = writer;
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("write_memory")
                .description("Write an important fact or preference to long-term memory. Use after learning something significant about the user.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
	                                "type", Map.of("type", "string",
	                                        "enum", List.of("PROFILE", "PREFERENCE", "EVENT", "GOAL", "RELATIONSHIP"),
	                                        "description", "Memory category"),
	                                "content", Map.of("type", "string",
	                                        "description", "The memory content to store"),
	                                "character_id", Map.of("type", "string",
	                                        "description", "Optional character context")),
	                        "required", new String[]{"type", "content"}))
                .riskLevel(ToolRiskLevel.LOW)
                .build();
    }

    @Override
    public ToolExecutionResult execute(String toolCallId, JsonNode arguments) {
        return execute(toolCallId, arguments, null);
    }

    @Override
    public ToolExecutionResult execute(String toolCallId, JsonNode arguments, ToolDispatchContext context) {
        try {
            String content = arguments.path("content").asText("").trim();
            if (content.isBlank()) {
                return ToolExecutionResult.error(toolCallId, "write_memory", "Missing required field: content");
            }

            String typeStr = arguments.path("type").asText("").trim();
            MemoryCategory category;
            try {
                category = MemoryCategory.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                return ToolExecutionResult.error(toolCallId, "write_memory", "Invalid memory type: " + typeStr);
            }

            String userId = context != null ? context.getUserId() : arguments.path("user_id").asText("");
            if (userId == null || userId.isBlank()) {
                return ToolExecutionResult.error(toolCallId, "write_memory", "Missing user context");
            }
            String characterId = arguments.has("character_id") ? arguments.path("character_id").asText(null) : null;

            MemoryWriteRequest request = MemoryWriteRequest.builder()
                    .memoryId(UUID.randomUUID().toString())
                    .userId(userId)
                    .characterId(characterId)
                    .scope(MemoryScope.GLOBAL)
                    .category(category)
                    .content(content)
                    .sourceSessionId(context != null ? context.getSessionId() : null)
                    .build();

            MemoryWriteResult result = writer.submit(request);

            if (!result.isSuccess()) {
                return ToolExecutionResult.error(toolCallId, "write_memory",
                        result.getError() != null ? result.getError() : "Write failed");
            }

            return ToolExecutionResult.success(toolCallId, "write_memory",
                    "Memory written: " + result.getMemoryId());
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, "write_memory", e.getMessage());
        }
    }
}

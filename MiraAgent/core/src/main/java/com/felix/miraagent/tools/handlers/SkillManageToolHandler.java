package com.felix.miraagent.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.skill.SkillCreateCommand;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.skill.SkillPatch;
import com.felix.miraagent.skill.SkillWriteResult;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolDispatchContext;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import com.felix.miraagent.tools.ToolRiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * skill_manage：创建/修补/归档一个可复用流程技能（housekeeping）。
 * 只存"怎么做事"的流程，绝不存用户隐私事实（隐私物理隔离——本工具无任何用户事实字段）。
 * 创建前由 writer 去重：相似度 > 0.85 自动转为 patch。
 */
public class SkillManageToolHandler implements ToolHandler {

    private final SkillManager skillManager;

    public SkillManageToolHandler(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("skill_manage")
                .description("Create, patch, or archive a reusable procedure skill (how to do a task). Never store user facts here. "
                        + "op=create needs name+description+body; op=patch needs skill_id; op=archive needs skill_id.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "op", Map.of("type", "string", "enum", List.of("create", "patch", "archive"),
                                        "description", "Operation"),
                                "skill_id", Map.of("type", "string", "description", "Target skill id (patch/archive)"),
                                "name", Map.of("type", "string", "description", "Skill name (create)"),
                                "description", Map.of("type", "string", "description", "Short description / when-to-use"),
                                "body", Map.of("type", "string", "description", "SKILL.md body: steps, checklist, failure modes"),
                                "note", Map.of("type", "string", "description", "Patch note for history"),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string"))),
                        "required", new String[]{"op"}))
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
            String op = arguments.path("op").asText("").trim().toLowerCase();
            String traceId = context != null ? context.getRunId() : null;
            String sessionId = context != null ? context.getSessionId() : null;

            return switch (op) {
                case "create" -> doCreate(toolCallId, arguments, traceId, sessionId);
                case "patch" -> doPatch(toolCallId, arguments, traceId, sessionId);
                case "archive" -> doArchive(toolCallId, arguments);
                default -> ToolExecutionResult.error(toolCallId, "skill_manage", "Invalid op: " + op);
            };
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, "skill_manage", e.getMessage());
        }
    }

    private ToolExecutionResult doCreate(String toolCallId, JsonNode args, String traceId, String sessionId) {
        String name = args.path("name").asText("").trim();
        String description = args.path("description").asText("").trim();
        String body = args.path("body").asText("").trim();
        if (name.isBlank() || description.isBlank()) {
            return ToolExecutionResult.error(toolCallId, "skill_manage", "create requires name and description");
        }
        SkillCreateCommand cmd = SkillCreateCommand.builder()
                .name(name).description(description).body(body)
                .tags(parseTags(args))
                .source("housekeeping")
                .sourceTraceId(traceId).sourceSessionId(sessionId)
                .build();
        SkillWriteResult result = skillManager.create(cmd);
        return toResult(toolCallId, result, "Skill created/updated: ");
    }

    private ToolExecutionResult doPatch(String toolCallId, JsonNode args, String traceId, String sessionId) {
        String skillId = args.path("skill_id").asText("").trim();
        if (skillId.isBlank()) {
            return ToolExecutionResult.error(toolCallId, "skill_manage", "patch requires skill_id");
        }
        SkillPatch patch = SkillPatch.builder()
                .skillId(skillId)
                .newDescription(args.has("description") ? args.path("description").asText(null) : null)
                .newBody(args.has("body") ? args.path("body").asText(null) : null)
                .appendBody(args.has("append_body") ? args.path("append_body").asText(null) : null)
                .note(args.path("note").asText(null))
                .sourceTraceId(traceId).sourceSessionId(sessionId)
                .build();
        SkillWriteResult result = skillManager.patch(patch);
        return toResult(toolCallId, result, "Skill patched: ");
    }

    private ToolExecutionResult doArchive(String toolCallId, JsonNode args) {
        String skillId = args.path("skill_id").asText("").trim();
        if (skillId.isBlank()) {
            return ToolExecutionResult.error(toolCallId, "skill_manage", "archive requires skill_id");
        }
        SkillWriteResult result = skillManager.archive(skillId);
        return toResult(toolCallId, result, "Skill archived: ");
    }

    private List<String> parseTags(JsonNode args) {
        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = args.path("tags");
        if (tagsNode.isArray()) {
            tagsNode.forEach(t -> {
                String v = t.asText("").trim();
                if (!v.isBlank()) {
                    tags.add(v);
                }
            });
        }
        return tags;
    }

    private ToolExecutionResult toResult(String toolCallId, SkillWriteResult result, String okPrefix) {
        if (result == null || !result.isSuccess()) {
            return ToolExecutionResult.error(toolCallId, "skill_manage",
                    result != null && result.getError() != null ? result.getError() : "skill write failed");
        }
        return ToolExecutionResult.success(toolCallId, "skill_manage", okPrefix + result.getSkillId());
    }
}

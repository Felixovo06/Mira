package com.felix.miraagent.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolDispatchContext;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import com.felix.miraagent.tools.ToolRiskLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * skill_view：按需加载某个 skill 的完整 SKILL.md（渐进式披露），并记一次 view 统计。
 */
public class SkillViewToolHandler implements ToolHandler {

    private final SkillManager skillManager;

    public SkillViewToolHandler(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("skill_view")
                .description("Load the full content (steps, checklist, failure modes) of a skill by id. Call when a skill from skills_list matches the current task.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "skill_id", Map.of("type", "string", "description", "The skill id to load")),
                        "required", new String[]{"skill_id"}))
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
            String skillId = arguments.path("skill_id").asText("").trim();
            if (skillId.isBlank()) {
                return ToolExecutionResult.error(toolCallId, "skill_view", "Missing required field: skill_id");
            }
            String traceId = context != null ? context.getRunId() : null;
            String sessionId = context != null ? context.getSessionId() : null;
            Optional<Skill> skill = skillManager.view(skillId, traceId, sessionId);
            if (skill.isEmpty() || skill.get().getContent() == null) {
                return ToolExecutionResult.success(toolCallId, "skill_view", "Skill not found: " + skillId);
            }
            String raw = skill.get().getContent().getRaw();
            return ToolExecutionResult.success(toolCallId, "skill_view",
                    raw != null ? raw : skill.get().getContent().getBody());
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, "skill_view", e.getMessage());
        }
    }
}

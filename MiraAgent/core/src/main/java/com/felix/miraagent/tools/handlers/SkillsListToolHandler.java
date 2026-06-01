package com.felix.miraagent.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolDispatchContext;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolHandler;
import com.felix.miraagent.tools.ToolRiskLevel;

import java.util.List;
import java.util.Map;

/**
 * skills_list：列出当前 Active skill 索引（id/name/description），供模型判断是否需要 skill_view 加载完整步骤。
 */
public class SkillsListToolHandler implements ToolHandler {

    private final SkillManager skillManager;

    public SkillsListToolHandler(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    public static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("skills_list")
                .description("List available reusable skills (index only). Use to check if a sedimented procedure matches the current task before doing it from scratch.")
                .inputSchema(Map.of("type", "object", "properties", Map.of()))
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
            List<SkillIndex> skills = skillManager.listActive();
            if (skills.isEmpty()) {
                return ToolExecutionResult.success(toolCallId, "skills_list", "No skills available yet.");
            }
            StringBuilder sb = new StringBuilder();
            for (SkillIndex s : skills) {
                sb.append("- `").append(s.getSkillId()).append("`");
                if (s.getName() != null && !s.getName().isBlank()) {
                    sb.append(" ").append(s.getName());
                }
                if (s.getDescription() != null && !s.getDescription().isBlank()) {
                    sb.append("：").append(s.getDescription());
                }
                sb.append("\n");
            }
            return ToolExecutionResult.success(toolCallId, "skills_list", sb.toString().trim());
        } catch (Exception e) {
            return ToolExecutionResult.error(toolCallId, "skills_list", e.getMessage());
        }
    }
}

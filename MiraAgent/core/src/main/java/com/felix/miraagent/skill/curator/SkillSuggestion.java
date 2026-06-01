package com.felix.miraagent.skill.curator;

import lombok.Builder;
import lombok.Value;

/**
 * 一条整理建议（仅建议，绝不自动执行）。
 */
@Value
@Builder
public class SkillSuggestion {
    String skillId;
    String name;
    String reason;
}

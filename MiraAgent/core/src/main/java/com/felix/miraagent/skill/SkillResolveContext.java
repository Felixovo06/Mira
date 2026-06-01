package com.felix.miraagent.skill;

import java.util.List;
import java.util.Optional;

/**
 * 单轮对话的 skill 解析上下文：本轮可见的 skill 索引快照 + 按需加载完整正文的能力。
 * 渐进式披露——prompt 只放索引，完整 SKILL.md 在 Agent 判断需要时（step4 的 skill_view 工具）才解析。
 */
public class SkillResolveContext {

    private final List<SkillIndex> availableSkills;
    private final SkillLoader loader;

    public SkillResolveContext(List<SkillIndex> availableSkills, SkillLoader loader) {
        this.availableSkills = availableSkills != null ? List.copyOf(availableSkills) : List.of();
        this.loader = loader;
    }

    public List<SkillIndex> getAvailableSkills() {
        return availableSkills;
    }

    public boolean isEmpty() {
        return availableSkills.isEmpty();
    }

    /** 按需加载 SKILL.md 正文。 */
    public Optional<SkillContent> resolveContent(String skillId) {
        return loader.loadContent(skillId);
    }

    /** 按需加载完整 skill（metadata + 正文）。 */
    public Optional<Skill> resolveSkill(String skillId) {
        return loader.loadSkill(skillId);
    }
}

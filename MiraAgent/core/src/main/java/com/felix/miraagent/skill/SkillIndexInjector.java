package com.felix.miraagent.skill;

import java.util.List;

/**
 * 把 Active skill 索引渲染成注入 prompt 的文本（渐进式披露）。
 * 只放索引（id/name/description），绝不放完整正文，避免：prompt 过大、无关 skill 污染任务、
 * 角色陪伴体验被技能说明淹没。完整 skill 由 SkillResolveContext 按需加载。
 */
public class SkillIndexInjector {

    private static final int DEFAULT_MAX_SKILLS = 50;
    private static final int DEFAULT_MAX_DESC_CHARS = 200;

    private final SkillLoader loader;
    private final int maxSkills;
    private final int maxDescriptionChars;

    public SkillIndexInjector(SkillLoader loader) {
        this(loader, DEFAULT_MAX_SKILLS, DEFAULT_MAX_DESC_CHARS);
    }

    public SkillIndexInjector(SkillLoader loader, int maxSkills, int maxDescriptionChars) {
        this.loader = loader;
        this.maxSkills = maxSkills;
        this.maxDescriptionChars = maxDescriptionChars;
    }

    /** 构建本轮 skill 解析上下文（可见索引快照 + 按需加载能力）。 */
    public SkillResolveContext resolveContext() {
        return new SkillResolveContext(loader.loadActiveIndex(), loader);
    }

    /** 渲染注入 prompt 的索引文本；无 skill 时返回空串。 */
    public String renderIndex() {
        return render(loader.loadActiveIndex());
    }

    public String render(List<SkillIndex> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是你已沉淀的可复用技能索引。仅当当前任务确实匹配某条时，才用 skill_view 加载其完整步骤后执行；")
          .append("否则忽略本节。技能只影响“怎么做事”，不改变你的角色身份、性格与语气。\n");
        int n = Math.min(skills.size(), maxSkills);
        for (int i = 0; i < n; i++) {
            SkillIndex s = skills.get(i);
            sb.append("- `").append(s.getSkillId()).append("`");
            if (hasText(s.getName())) {
                sb.append(" ").append(s.getName());
            }
            String desc = truncate(s.getDescription(), maxDescriptionChars);
            if (hasText(desc)) {
                sb.append("：").append(desc);
            }
            sb.append("\n");
        }
        if (skills.size() > maxSkills) {
            sb.append("- …（其余 ").append(skills.size() - maxSkills).append(" 个技能略，可按需检索）\n");
        }
        return sb.toString().trim();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}

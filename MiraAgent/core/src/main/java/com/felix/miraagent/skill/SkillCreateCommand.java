package com.felix.miraagent.skill;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 创建 skill 的命令。提交前必须经去重：description 相似度 > 0.85 时强制改为 patch 已有 skill。
 */
@Value
@Builder
public class SkillCreateCommand {
    String skillId;          // nullable，缺省由 name 派生
    String name;
    String description;
    String body;             // SKILL.md 正文（不含 frontmatter）
    @Singular
    List<String> tags;
    boolean pinned;
    String source;           // user / housekeeping / background_review
    String sourceTraceId;    // nullable
    String sourceSessionId;  // nullable
}

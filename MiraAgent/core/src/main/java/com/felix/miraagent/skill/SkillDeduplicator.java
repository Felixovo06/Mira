package com.felix.miraagent.skill;

import lombok.Value;

import java.util.Optional;

/**
 * Skill 去重：用 description 的 embedding 在现有 Active skill 中找最相似项。
 * 相似度 > 0.85 视为重复，创建时强制改为 patch（见 docs/07 §16 决策 3）。
 */
public interface SkillDeduplicator {

    double DUPLICATE_THRESHOLD = 0.85;

    /** 找到相似度超阈值的已有 skill；无则 empty。 */
    Optional<DuplicateMatch> findDuplicate(String description);

    @Value
    class DuplicateMatch {
        String skillId;
        double similarity;
    }
}

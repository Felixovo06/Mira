package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 经验提炼的固定输出 schema（docs/07 §11.2）。memory_writes 与 skill_writes 互斥：
 * 一个信息要么是用户事实（进 memory），要么是可复用流程（进 skill），不会两边都进。
 */
@Value
@Builder
public class ExperienceReviewResult {
    boolean worthSaving;
    @Singular
    List<MemoryWritePlan> memoryWrites;
    @Singular
    List<SkillWritePlan> skillWrites;

    public static ExperienceReviewResult nothing() {
        return ExperienceReviewResult.builder().worthSaving(false).build();
    }
}

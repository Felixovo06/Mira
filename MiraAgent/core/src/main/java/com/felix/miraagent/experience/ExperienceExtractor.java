package com.felix.miraagent.experience;

/**
 * 经验提炼器：一次强制结构化输出的 LLM 调用，把本轮 trace/session 提炼为固定 schema 的
 * memory_writes / skill_writes（互斥）。见 docs/07 §11.2。
 */
public interface ExperienceExtractor {
    ExperienceReviewResult extract(ExperienceReviewRequest request);
}

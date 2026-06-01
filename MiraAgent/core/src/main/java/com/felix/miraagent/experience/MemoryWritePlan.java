package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Value;

/**
 * 提炼出的一条记忆写入计划。承载"用户事实"——与 SkillWritePlan 互斥（事实只进这里）。
 */
@Value
@Builder
public class MemoryWritePlan {
    String kind;            // fact | preference | relationship | tool_experience
    String content;
    String scope;           // global | character
    double confidence;      // 由来源定死（见 ConfidenceSource）
    String sourceTraceId;
    String dedupKey;        // nullable
}

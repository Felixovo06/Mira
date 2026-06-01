package com.felix.miraagent.skill.curator;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 整理报告（docs/07 §16 决策 5）：三类建议——未使用、过窄（建议归档）、相近（建议合并）。
 * **只提建议，绝不自动改**；pinned skill 不出现在任何建议里。
 */
@Value
@Builder
public class CuratorReport {
    @Singular("unused")
    List<SkillSuggestion> unused;       // last_used_at 早于 N 天
    @Singular("narrow")
    List<SkillSuggestion> narrow;       // use_count < 阈值
    @Singular("similar")
    List<SkillConsolidationProposal> similar; // cosine > 阈值
}

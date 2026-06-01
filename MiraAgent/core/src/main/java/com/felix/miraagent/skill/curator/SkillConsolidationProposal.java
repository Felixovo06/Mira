package com.felix.miraagent.skill.curator;

import lombok.Builder;
import lombok.Value;

/**
 * 合并建议：两个相近 skill（名称/描述 embedding cosine > 阈值）。仅建议，由用户确认。
 */
@Value
@Builder
public class SkillConsolidationProposal {
    String skillIdA;
    String skillIdB;
    double similarity;
}

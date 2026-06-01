package com.felix.miraagent.skill.curator;

import java.util.List;

/**
 * 找出相近 skill 对（cosine > threshold）。pgvector 实现；无 embedding 时降级为空。
 */
public interface SkillSimilarityFinder {
    List<SkillConsolidationProposal> findSimilarPairs(double threshold);
}

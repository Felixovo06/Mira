package com.felix.miraagent.skill.curator;

import java.util.List;

/** 无 embedding/无 DB 时降级：不产生合并建议。 */
public class NoOpSkillSimilarityFinder implements SkillSimilarityFinder {
    @Override
    public List<SkillConsolidationProposal> findSimilarPairs(double threshold) {
        return List.of();
    }
}

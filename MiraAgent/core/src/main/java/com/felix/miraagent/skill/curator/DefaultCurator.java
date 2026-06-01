package com.felix.miraagent.skill.curator;

import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillLoader;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Curator 默认实现。阈值定死（可调初值）：未使用 30 天 → 建议归档；use_count < 3 → 建议归档；
 * cosine > 0.85 → 建议合并。pinned skill 既不被建议归档也不被建议合并。
 */
public class DefaultCurator implements Curator {

    private final SkillLoader skillLoader;
    private final SkillSimilarityFinder similarityFinder;
    private final Duration unusedThreshold;
    private final int minUseCount;
    private final double similarityThreshold;

    public DefaultCurator(SkillLoader skillLoader, SkillSimilarityFinder similarityFinder) {
        this(skillLoader, similarityFinder, Duration.ofDays(30), 3, 0.85);
    }

    public DefaultCurator(SkillLoader skillLoader, SkillSimilarityFinder similarityFinder,
                          Duration unusedThreshold, int minUseCount, double similarityThreshold) {
        this.skillLoader = skillLoader;
        this.similarityFinder = similarityFinder;
        this.unusedThreshold = unusedThreshold;
        this.minUseCount = minUseCount;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public CuratorReport analyze() {
        return analyze(Instant.now());
    }

    public CuratorReport analyze(Instant now) {
        CuratorReport.CuratorReportBuilder report = CuratorReport.builder();
        Instant cutoff = now.minus(unusedThreshold);

        for (SkillIndex s : skillLoader.loadActiveIndex()) {
            if (s.isPinned()) {
                continue; // pinned 受保护
            }
            Instant lastActivity = s.getLastUsedAt() != null ? s.getLastUsedAt() : s.getCreatedAt();
            if (lastActivity != null && lastActivity.isBefore(cutoff)) {
                report.unused(SkillSuggestion.builder()
                        .skillId(s.getSkillId()).name(s.getName())
                        .reason("未使用超过 " + unusedThreshold.toDays() + " 天").build());
            }
            if (s.getUseCount() < minUseCount) {
                report.narrow(SkillSuggestion.builder()
                        .skillId(s.getSkillId()).name(s.getName())
                        .reason("使用次数 " + s.getUseCount() + " < " + minUseCount).build());
            }
        }

        if (similarityFinder != null) {
            for (SkillConsolidationProposal p : similarityFinder.findSimilarPairs(similarityThreshold)) {
                report.similar(p);
            }
        }
        return report.build();
    }
}

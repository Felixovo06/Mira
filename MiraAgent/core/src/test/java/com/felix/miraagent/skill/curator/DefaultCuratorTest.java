package com.felix.miraagent.skill.curator;

import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillContent;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillLoader;
import com.felix.miraagent.skill.SkillStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCuratorTest {

    private final Instant now = Instant.parse("2026-06-01T00:00:00Z");

    private SkillIndex idx(String id, int useCount, boolean pinned, Instant lastUsed, Instant created) {
        return SkillIndex.builder().skillId(id).name(id).status(SkillStatus.ACTIVE)
                .useCount(useCount).pinned(pinned).lastUsedAt(lastUsed).createdAt(created).build();
    }

    private SkillLoader loader(List<SkillIndex> list) {
        return new SkillLoader() {
            @Override public List<SkillIndex> loadActiveIndex() { return list; }
            @Override public Optional<Skill> loadSkill(String id) { return Optional.empty(); }
            @Override public Optional<SkillContent> loadContent(String id) { return Optional.empty(); }
            @Override public String loadResource(String id, String p) { return ""; }
            @Override public List<String> listResources(String id, String p) { return List.of(); }
        };
    }

    @Test
    void flagsUnusedAndNarrow() {
        var old = idx("stale", 5, false, now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(100)));
        var fresh = idx("active", 10, false, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(50)));
        var narrow = idx("rare", 1, false, now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));

        var curator = new DefaultCurator(loader(List.of(old, fresh, narrow)), new NoOpSkillSimilarityFinder());
        CuratorReport report = curator.analyze(now);

        assertEquals(1, report.getUnused().size());
        assertEquals("stale", report.getUnused().get(0).getSkillId());
        assertEquals(1, report.getNarrow().size());
        assertEquals("rare", report.getNarrow().get(0).getSkillId());
    }

    @Test
    void pinnedSkillNeverSuggested() {
        var pinnedOldRare = idx("pinned", 0, true, now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(200)));
        var curator = new DefaultCurator(loader(List.of(pinnedOldRare)), new NoOpSkillSimilarityFinder());
        CuratorReport report = curator.analyze(now);
        assertTrue(report.getUnused().isEmpty());
        assertTrue(report.getNarrow().isEmpty());
    }

    @Test
    void includesSimilarProposals() {
        SkillSimilarityFinder finder = threshold -> List.of(
                SkillConsolidationProposal.builder().skillIdA("a").skillIdB("b").similarity(0.91).build());
        var curator = new DefaultCurator(loader(List.of()), finder);
        CuratorReport report = curator.analyze(now);
        assertEquals(1, report.getSimilar().size());
        assertEquals(0.91, report.getSimilar().get(0).getSimilarity());
    }

    @Test
    void neverUsedOldCountsAsUnusedViaCreatedAt() {
        var neverUsed = idx("ghost", 5, false, null, now.minus(Duration.ofDays(60)));
        var curator = new DefaultCurator(loader(List.of(neverUsed)), new NoOpSkillSimilarityFinder());
        assertEquals(1, curator.analyze(now).getUnused().size());
    }
}

package com.felix.miraagent.skill;

import com.felix.miraagent.skill.curator.SkillConsolidationProposal;
import com.felix.miraagent.skill.curator.SkillSimilarityFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 基于 pgvector 的相近 skill 检索：对 Active 且非 pinned 的 skill 两两比较 cosine。
 * 无 embedding 列时静默降级为空列表。
 */
public class EmbeddingSkillSimilarityFinder implements SkillSimilarityFinder {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSkillSimilarityFinder.class);

    private final JdbcTemplate jdbc;

    public EmbeddingSkillSimilarityFinder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SkillConsolidationProposal> findSimilarPairs(double threshold) {
        try {
            return jdbc.query("""
                    select a.id as ida, b.id as idb, 1 - (a.embedding <=> b.embedding) as sim
                    from skills a
                    join skills b on a.id < b.id
                    where a.embedding is not null and b.embedding is not null
                      and a.status = 'ACTIVE' and b.status = 'ACTIVE'
                      and a.archived_at is null and b.archived_at is null
                      and coalesce(a.pinned, false) = false and coalesce(b.pinned, false) = false
                      and (1 - (a.embedding <=> b.embedding)) > ?
                    order by sim desc
                    limit 50
                    """,
                    (rs, n) -> SkillConsolidationProposal.builder()
                            .skillIdA(rs.getString("ida"))
                            .skillIdB(rs.getString("idb"))
                            .similarity(rs.getDouble("sim"))
                            .build(),
                    threshold);
        } catch (Exception e) {
            log.warn("Skill similarity scan skipped ({})", e.getMessage());
            return List.of();
        }
    }
}

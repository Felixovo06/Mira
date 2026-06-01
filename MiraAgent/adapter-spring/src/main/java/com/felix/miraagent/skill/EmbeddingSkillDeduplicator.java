package com.felix.miraagent.skill;

import com.felix.miraagent.memory.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于 pgvector 的 skill 去重：embed(description) 后在 Active skill 里按 cosine 找最相似项。
 * 相似度 = 1 - (embedding <=> queryvec)。无 embedding/无列时静默降级为不判重。
 */
public class EmbeddingSkillDeduplicator implements SkillDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSkillDeduplicator.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient; // nullable

    public EmbeddingSkillDeduplicator(JdbcTemplate jdbc, EmbeddingClient embeddingClient) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public Optional<DuplicateMatch> findDuplicate(String description) {
        if (embeddingClient == null || description == null || description.isBlank()) {
            return Optional.empty();
        }
        try {
            List<Float> vector = embeddingClient.embed(description);
            String vectorStr = "[" + vector.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
            return jdbc.query("""
                    select id, 1 - (embedding <=> ?::vector) as sim
                    from skills
                    where embedding is not null and status = 'ACTIVE' and archived_at is null
                    order by embedding <=> ?::vector
                    limit 1
                    """,
                    rs -> {
                        if (!rs.next()) {
                            return Optional.<DuplicateMatch>empty();
                        }
                        double sim = rs.getDouble("sim");
                        if (sim > DUPLICATE_THRESHOLD) {
                            return Optional.of(new DuplicateMatch(rs.getString("id"), sim));
                        }
                        return Optional.<DuplicateMatch>empty();
                    },
                    vectorStr, vectorStr);
        } catch (Exception e) {
            log.warn("Skill dedup skipped ({})", e.getMessage());
            return Optional.empty();
        }
    }
}

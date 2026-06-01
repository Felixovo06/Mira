package com.felix.miraagent.skill;

import com.felix.miraagent.memory.EmbeddingClient;
import com.felix.miraagent.memory.EmbeddingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 异步把 skill 的 description 写成向量到 skills.embedding（镜像 AsyncEmbeddingIndexer）。
 * 仅当 pgvector 已装、skills.embedding 列存在时生效；否则静默降级。供 SkillDeduplicator 去重检索用。
 */
public class SkillEmbeddingIndexer {

    private static final Logger log = LoggerFactory.getLogger(SkillEmbeddingIndexer.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient; // nullable

    public SkillEmbeddingIndexer(JdbcTemplate jdbc, EmbeddingClient embeddingClient) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
    }

    public void indexAsync(String skillId, String description) {
        if (embeddingClient == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                List<Float> vector = embeddingClient.embed(description);
                String vectorStr = "[" + vector.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
                jdbc.update("UPDATE skills SET embedding = ?::vector WHERE id = ?", vectorStr, skillId);
            } catch (EmbeddingException e) {
                log.warn("Failed to compute embedding for skill {}: {}", skillId, e.getMessage());
            } catch (Exception e) {
                log.warn("Skill embedding index skipped for {} ({})", skillId, e.getMessage());
            }
        });
    }
}

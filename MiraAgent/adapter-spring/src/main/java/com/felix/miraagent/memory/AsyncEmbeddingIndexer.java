package com.felix.miraagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

public class AsyncEmbeddingIndexer {

    private static final Logger log = LoggerFactory.getLogger(AsyncEmbeddingIndexer.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient;

    public AsyncEmbeddingIndexer(JdbcTemplate jdbc, EmbeddingClient embeddingClient) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
    }

    public void indexAsync(String memoryId, String content) {
        if (embeddingClient == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                List<Float> vector = embeddingClient.embed(content);
                String vectorStr = toVectorString(vector);
                jdbc.update(
                        "UPDATE memory_index SET embedding = ?::vector WHERE id = ?",
                        vectorStr, memoryId
                );
            } catch (EmbeddingException e) {
                log.warn("Failed to compute embedding for memory {}: {}", memoryId, e.getMessage());
            } catch (Exception e) {
                log.warn("Unexpected error indexing embedding for memory {}", memoryId, e);
            }
        });
    }

    private String toVectorString(List<Float> vec) {
        return "[" + vec.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
    }
}

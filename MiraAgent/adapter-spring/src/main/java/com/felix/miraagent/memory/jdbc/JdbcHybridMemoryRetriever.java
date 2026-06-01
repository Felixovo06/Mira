package com.felix.miraagent.memory.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.memory.EmbeddingClient;
import com.felix.miraagent.memory.EmbeddingException;
import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryIndex;
import com.felix.miraagent.memory.MemoryRetrieveRequest;
import com.felix.miraagent.memory.MemoryRetrieveResult;
import com.felix.miraagent.memory.MemoryRetriever;
import com.felix.miraagent.memory.MemoryScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class JdbcHybridMemoryRetriever implements MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(JdbcHybridMemoryRetriever.class);

    private final JdbcMemoryRetriever lexicalRetriever;
    private final EmbeddingClient embeddingClient;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcHybridMemoryRetriever(JdbcMemoryRetriever lexicalRetriever,
                                     EmbeddingClient embeddingClient,
                                     JdbcTemplate jdbc,
                                     ObjectMapper objectMapper) {
        this.lexicalRetriever = lexicalRetriever;
        this.embeddingClient = embeddingClient;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemoryRetrieveResult retrieve(MemoryRetrieveRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return MemoryRetrieveResult.builder()
                    .hits(Collections.emptyList())
                    .queryUsed(request.getQuery())
                    .build();
        }

        int limit = request.getLimit() > 0 ? request.getLimit() : 10;

        CompletableFuture<List<MemoryIndex>> lexicalFuture = CompletableFuture
                .supplyAsync(() -> lexicalRetriever.retrieve(request).getHits())
                .exceptionally(e -> {
                    log.warn("Lexical search failed", e);
                    return Collections.emptyList();
                });

        CompletableFuture<List<MemoryIndex>> vectorFuture = embeddingClient != null
                ? CompletableFuture.supplyAsync(() -> vectorHits(request, limit))
                : CompletableFuture.completedFuture(Collections.emptyList());

        List<MemoryIndex> merged = rerank(rrf(lexicalFuture.join(), vectorFuture.join(), limit * 2), request, limit);

        return MemoryRetrieveResult.builder()
                .hits(merged)
                .queryUsed(request.getQuery())
                .build();
    }

    private List<MemoryIndex> vectorHits(MemoryRetrieveRequest request, int limit) {
        try {
            List<Float> queryVector = embeddingClient.embed(request.getQuery());
            return fetchVectorHits(queryVector, request.getUserId(), limit * 2);
        } catch (EmbeddingException e) {
            log.warn("Embedding failed, falling back to lexical only: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Vector search failed, falling back to lexical only", e);
        }
        return Collections.emptyList();
    }

    private List<MemoryIndex> fetchVectorHits(List<Float> queryVector, String userId, int fetchLimit) {
        String vectorStr = toVectorString(queryVector);
        String sql = """
                SELECT m.id, m.user_id, m.character_id, m.scope, m.category,
                    m.content_preview, m.source_uri, m.confidence,
                    m.source_session_id, m.source_message_id,
                    m.retrieval_terms, m.embedding_ref, m.archived_at,
                    m.created_at, m.updated_at,
                    (m.embedding <=> ?::vector) as vec_distance
                FROM memory_index m
                WHERE m.user_id = ? AND m.archived_at IS NULL AND m.embedding IS NOT NULL
                ORDER BY m.embedding <=> ?::vector
                LIMIT ?
                """;
        try {
            return jdbc.query(sql, vectorRowMapper(), vectorStr, userId, vectorStr, fetchLimit);
        } catch (Exception e) {
            log.warn("Vector SQL failed (pgvector may not be available), falling back to lexical only", e);
            return Collections.emptyList();
        }
    }

    private List<MemoryIndex> rrf(List<MemoryIndex> lexicalHits, List<MemoryIndex> vectorHits, int limit) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, MemoryIndex> byId = new LinkedHashMap<>();

        for (int i = 0; i < lexicalHits.size(); i++) {
            String id = lexicalHits.get(i).getId();
            scores.merge(id, 1.0 / (60 + i + 1), Double::sum);
            byId.put(id, lexicalHits.get(i));
        }

        for (int i = 0; i < vectorHits.size(); i++) {
            String id = vectorHits.get(i).getId();
            scores.merge(id, 1.0 / (60 + i + 1), Double::sum);
            byId.putIfAbsent(id, vectorHits.get(i));
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> byId.get(e.getKey()))
                .toList();
    }

    private List<MemoryIndex> rerank(List<MemoryIndex> merged, MemoryRetrieveRequest request, int limit) {
        Map<String, Integer> originalRank = new LinkedHashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            originalRank.put(merged.get(i).getId(), i);
        }
        return merged.stream()
                .sorted(Comparator
                        .comparingDouble((MemoryIndex index) -> rerankScore(index, request)).reversed()
                        .thenComparingInt(index -> originalRank.getOrDefault(index.getId(), Integer.MAX_VALUE)))
                .limit(limit)
                .toList();
    }

    private double rerankScore(MemoryIndex index, MemoryRetrieveRequest request) {
        double score = 0.0;
        if (request.getCharacterId() != null && request.getCharacterId().equals(index.getCharacterId())) {
            score += 3.0;
        }
        score += categoryWeight(index.getCategory());
        Instant recency = index.getUpdatedAt() != null ? index.getUpdatedAt() : index.getCreatedAt();
        if (recency != null) {
            score += recency.toEpochMilli() / 1_000_000_000_000.0;
        }
        return score;
    }

    private double categoryWeight(MemoryCategory category) {
        if (category == null) {
            return 0.0;
        }
        return switch (category) {
            case PROFILE -> 1.0;
            case PREFERENCE -> 0.9;
            case RELATIONSHIP -> 0.8;
            case GOAL -> 0.6;
            case EVENT -> 0.4;
            case SUMMARY -> 0.2;
        };
    }

    private String toVectorString(List<Float> vec) {
        return "[" + vec.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
    }

    private RowMapper<MemoryIndex> vectorRowMapper() {
        return (rs, rowNum) -> {
            List<String> retrievalTerms = Collections.emptyList();
            String termsJson = rs.getString("retrieval_terms");
            if (termsJson != null) {
                try {
                    retrievalTerms = objectMapper.readValue(termsJson, new TypeReference<>() {});
                } catch (Exception e) {
                    log.warn("Failed to deserialize retrieval_terms for index {}", rs.getString("id"), e);
                }
            }

            String scopeStr = rs.getString("scope");
            MemoryScope scope = scopeStr != null ? MemoryScope.valueOf(scopeStr) : null;

            String categoryStr = rs.getString("category");
            MemoryCategory category = categoryStr != null ? MemoryCategory.valueOf(categoryStr) : null;

            Timestamp archivedTs = rs.getTimestamp("archived_at");
            Timestamp createdTs = rs.getTimestamp("created_at");
            Timestamp updatedTs = rs.getTimestamp("updated_at");

            return MemoryIndex.builder()
                    .id(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .characterId(rs.getString("character_id"))
                    .scope(scope)
                    .category(category)
                    .contentPreview(rs.getString("content_preview"))
                    .sourceUri(rs.getString("source_uri"))
                    .confidence(rs.getInt("confidence"))
                    .sourceSessionId(rs.getString("source_session_id"))
                    .sourceMessageId(rs.getString("source_message_id"))
                    .retrievalTerms(retrievalTerms)
                    .embeddingRef(rs.getString("embedding_ref"))
                    .archivedAt(archivedTs != null ? archivedTs.toInstant() : null)
                    .createdAt(createdTs != null ? createdTs.toInstant() : Instant.now())
                    .updatedAt(updatedTs != null ? updatedTs.toInstant() : Instant.now())
                    .build();
        };
    }
}

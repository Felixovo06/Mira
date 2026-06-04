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
import com.felix.miraagent.memory.RerankClient;
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

    private static final int RRF_K = 60;
    // 向量是可靠的语义信号;pg_trgm 词法对短中文查询多为噪声,故向量在融合中加权更高。
    private static final double VECTOR_WEIGHT = 3.0;
    private static final double LEXICAL_WEIGHT = 1.0;
    // 次级加成:在「相关性」主信号之上做轻微调整,可盖过小幅相关性差距,但不会压过强相关性。
    // 不做 recency 衰减:长期记忆中旧的稳定事实不应因年龄被压低(否则"10天前的过敏"会被今天的琐事埋掉)。
    private static final double CHAR_BOOST = 0.15;
    private static final double CATEGORY_BOOST = 0.05;

    private final JdbcMemoryRetriever lexicalRetriever;
    private final EmbeddingClient embeddingClient;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    /** 可选的重排模型（百炼 rerank）；为 null 时退回归一化 RRF 融合排序。 */
    private final RerankClient rerankClient;
    /** 送进 rerank 的候选上限（取融合分 top-N），控制成本与延迟。 */
    private final int rerankTopN;

    public JdbcHybridMemoryRetriever(JdbcMemoryRetriever lexicalRetriever,
                                     EmbeddingClient embeddingClient,
                                     JdbcTemplate jdbc,
                                     ObjectMapper objectMapper) {
        this(lexicalRetriever, embeddingClient, jdbc, objectMapper, null, 20);
    }

    public JdbcHybridMemoryRetriever(JdbcMemoryRetriever lexicalRetriever,
                                     EmbeddingClient embeddingClient,
                                     JdbcTemplate jdbc,
                                     ObjectMapper objectMapper,
                                     RerankClient rerankClient,
                                     int rerankTopN) {
        this.lexicalRetriever = lexicalRetriever;
        this.embeddingClient = embeddingClient;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.rerankClient = rerankClient;
        this.rerankTopN = rerankTopN > 0 ? rerankTopN : 20;
    }

    @Override
    public MemoryRetrieveResult retrieve(MemoryRetrieveRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return MemoryRetrieveResult.builder()
                    .hits(Collections.emptyList())
                    .queryUsed(request.getQuery())
                    .build();
        }

        int limit = request.getLimit() > 0 ? request.getLimit() : 5;

        CompletableFuture<List<MemoryIndex>> lexicalFuture = CompletableFuture
                .supplyAsync(() -> lexicalRetriever.retrieve(request).getHits())
                .exceptionally(e -> {
                    log.warn("Lexical search failed", e);
                    return Collections.emptyList();
                });

        CompletableFuture<List<MemoryIndex>> vectorFuture = embeddingClient != null
                ? CompletableFuture.supplyAsync(() -> vectorHits(request, limit))
                : CompletableFuture.completedFuture(Collections.emptyList());

        Map<String, Double> fused = new LinkedHashMap<>();
        Map<String, MemoryIndex> byId = new LinkedHashMap<>();
        accumulateRrf(fused, byId, lexicalFuture.join(), LEXICAL_WEIGHT);
        accumulateRrf(fused, byId, vectorFuture.join(), VECTOR_WEIGHT);

        List<MemoryIndex> merged = rerankAndCut(byId, fused, request, limit);

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

    /** 加权 RRF 累加:把一路检索结果按排名贡献分数到 fused,权重区分词法/向量。 */
    private void accumulateRrf(Map<String, Double> fused, Map<String, MemoryIndex> byId,
                               List<MemoryIndex> hits, double weight) {
        for (int i = 0; i < hits.size(); i++) {
            MemoryIndex m = hits.get(i);
            fused.merge(m.getId(), weight / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(m.getId(), m);
        }
    }

    /**
     * 重排 + 截断:以「主信号相关性」为基准,角色/类目作为次级加成。
     * 主信号:有 rerank 模型则用模型打分(语义重排),否则用归一化的 RRF 融合分。
     * 角色/类目加成量级小(0.05/0.15),只能盖过小幅相关性差距,不会压过强相关性。
     */
    private List<MemoryIndex> rerankAndCut(Map<String, MemoryIndex> byId, Map<String, Double> fused,
                                           MemoryRetrieveRequest request, int limit) {
        Map<String, Double> relevance = buildRelevance(byId, fused, request);
        return byId.values().stream()
                .sorted(Comparator.comparingDouble((MemoryIndex index) ->
                        finalScore(index, relevance.getOrDefault(index.getId(), 0.0), request)).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 计算每条候选的主信号相关性 [0,1]。
     * 有 rerank 模型且候选>1:取融合分 top-N 候选送模型打分,模型分即相关性(未被打分的候选记 0);
     * 模型调用失败则优雅退回归一化 RRF 融合分,绝不阻断检索。
     */
    private Map<String, Double> buildRelevance(Map<String, MemoryIndex> byId, Map<String, Double> fused,
                                               MemoryRetrieveRequest request) {
        if (rerankClient != null && byId.size() > 1) {
            try {
                List<MemoryIndex> candidates = byId.values().stream()
                        .sorted(Comparator.comparingDouble(
                                (MemoryIndex m) -> fused.getOrDefault(m.getId(), 0.0)).reversed())
                        .limit(rerankTopN)
                        .toList();
                List<String> docs = candidates.stream()
                        .map(m -> m.getContentPreview() == null ? "" : m.getContentPreview())
                        .toList();
                double[] scores = rerankClient.rerank(request.getQuery(), docs);
                Map<String, Double> rel = new LinkedHashMap<>();
                for (int i = 0; i < candidates.size() && i < scores.length; i++) {
                    rel.put(candidates.get(i).getId(), scores[i]);
                }
                return rel;
            } catch (Exception e) {
                log.warn("Rerank model failed, falling back to RRF fusion ranking: {}", e.getMessage());
            }
        }
        return normalizeFused(fused);
    }

    /** 归一化融合分到 [0,1]（主信号回退路径）。 */
    private Map<String, Double> normalizeFused(Map<String, Double> fused) {
        double maxRel = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double norm = maxRel > 0 ? maxRel : 1.0;
        Map<String, Double> rel = new LinkedHashMap<>();
        fused.forEach((id, v) -> rel.put(id, v / norm));
        return rel;
    }

    private double finalScore(MemoryIndex index, double relevanceNorm, MemoryRetrieveRequest request) {
        double score = relevanceNorm; // 主信号:归一化融合相关性 [0,1]
        if (request.getCharacterId() != null && request.getCharacterId().equals(index.getCharacterId())) {
            score += CHAR_BOOST;
        }
        score += CATEGORY_BOOST * categoryWeight(index.getCategory());
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

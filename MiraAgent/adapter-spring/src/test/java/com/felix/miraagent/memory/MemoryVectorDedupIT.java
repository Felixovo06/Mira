package com.felix.miraagent.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量语义去重的真实库验证：直接用手工构造的 1536 维向量打 {@code findMostSimilarByVector}
 * 那条 pgvector SQL，以及 {@link AsyncEmbeddingIndexer#persist} 同步落库。
 * 不依赖 embedding 模型语义，结果确定（同向量 cosine=1，正交 cosine=0）。
 *
 * <pre>
 * JAVA_HOME=... ./mvnw -pl adapter-spring test -Dmira.it.postgres=true \
 *   -Dtest=MemoryVectorDedupIT -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfSystemProperty(named = "mira.it.postgres", matches = "true")
@SpringBootTest(properties = {
        "memory.base-dir=target/test-memory-vec-dedup-it",
        "mira.weixin.enabled=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("local")
class MemoryVectorDedupIT {

    private static final String USER = "mem-vecdedup-it-user";
    private static final int DIM = 1536;

    @Autowired MemoryIndexRepository indexRepository;
    @Autowired(required = false) AsyncEmbeddingIndexer embeddingIndexer;

    @AfterEach
    void cleanup() {
        try {
            indexRepository.deleteAll(USER);
        } catch (Exception ignored) {
        }
    }

    /** 单位向量：仅第 idx 维为 1，其余为 0；两个不同 idx 的向量正交（cosine=0）。 */
    private static List<Float> unitVector(int idx) {
        List<Float> v = new ArrayList<>(DIM);
        for (int i = 0; i < DIM; i++) {
            v.add(i == idx ? 1f : 0f);
        }
        return v;
    }

    @Test
    void findMostSimilarByVectorMatchesSameVectorAndRejectsOrthogonal() {
        indexRepository.deleteAll(USER);
        Instant now = Instant.now();
        // 一张已落库的卡片，带向量 vecA（仅维度 0 为 1）
        indexRepository.save(MemoryIndex.builder()
                .id("vd-1").userId(USER)
                .scope(MemoryScope.GLOBAL)
                .category(MemoryCategory.PREFERENCE)
                .contentPreview("用户很喜欢喝乌龙茶")
                .sourceUri("memory/vecdedup/vd-1")
                .retrievalTerms(List.of())
                .createdAt(now).updatedAt(now)
                .build());
        List<Float> vecA = unitVector(0);
        List<Float> vecB = unitVector(1); // 与 vecA 正交

        // 同步落库（被测：AsyncEmbeddingIndexer.persist）
        embeddingIndexer.persist("vd-1", vecA);

        // 1) 同向量：cosine=1，应命中且 sim≈1
        Optional<SimilarMemory> same = indexRepository.findMostSimilarByVector(
                USER, null, MemoryCategory.PREFERENCE, vecA, 0.9);
        assertTrue(same.isPresent(), "同向量应命中语义去重");
        assertEquals("vd-1", same.get().getMemory().getId());
        assertTrue(same.get().getSimilarity() >= 0.99,
                "同向量 cosine 应≈1，实际=" + same.get().getSimilarity());

        // 2) 正交向量：cosine=0 < 0.9 阈值，应判为非重复
        Optional<SimilarMemory> orthogonal = indexRepository.findMostSimilarByVector(
                USER, null, MemoryCategory.PREFERENCE, vecB, 0.9);
        assertTrue(orthogonal.isEmpty(), "正交向量不应判重，实际命中=" + orthogonal);

        // 3) 类目隔离：同向量但不同 category，不应命中（去重限定同 category）
        Optional<SimilarMemory> otherCategory = indexRepository.findMostSimilarByVector(
                USER, null, MemoryCategory.EVENT, vecA, 0.9);
        assertTrue(otherCategory.isEmpty(), "不同 category 不应判重");
    }
}

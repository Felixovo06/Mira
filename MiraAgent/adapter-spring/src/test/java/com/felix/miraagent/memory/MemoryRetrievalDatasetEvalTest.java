package com.felix.miraagent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.memory.jdbc.JdbcHybridMemoryRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 记忆检索数据集级评测（组件级）。
 *
 * <p>方法论：用一份<b>固定、人工标注、可复用</b>的语料 + 查询集（resources/eval/*.json）评估混合检索质量。
 * 设计上刻意避免"面向结果"：
 * <ul>
 *   <li>查询是自然口语，<b>不照抄</b>记忆原文（消除同义改写泄漏）；</li>
 *   <li>分级相关性 2/1/0，且允许一个查询有多条相关（Recall 才有部分召回、nDCG 才有意义）；</li>
 *   <li>干扰项是真实会发生的同话题/同实体记忆，按其真实相关度标注，而非为压低某分数手搓。</li>
 * </ul>
 * 报全套指标（Recall@5 / Precision@5 / MRR / nDCG@5 / Top-1-hit），不设迁就数字的 gate，
 * 只留极松的健全性断言。依赖真实 embedding(DashScope) + 云 PG，按需运行：
 *
 * <pre>
 * JAVA_HOME=... ./mvnw -pl adapter-spring test -Dmira.it.postgres=true \
 *   -Dtest=MemoryRetrievalDatasetEvalTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfSystemProperty(named = "mira.it.postgres", matches = "true")
@SpringBootTest(properties = {
        "memory.base-dir=target/test-memory-dataset-eval",
        "mira.weixin.enabled=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("local")
class MemoryRetrievalDatasetEvalTest {

    private static final String USER = "mem-dataset-eval-user";
    private static final int K = 5;

    @Autowired MemoryRetriever retriever;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemoryIndexRepository indexRepository;
    @Autowired(required = false) EmbeddingClient embeddingClient;

    record CorpusItem(String id, int day, String category, String content) {}

    record EvalQuery(String id, String query, Map<String, Integer> relevant, String note) {}

    @AfterEach
    void cleanup() {
        try {
            indexRepository.deleteAll(USER);
        } catch (Exception ignored) {
        }
    }

    @Test
    void datasetRetrievalQuality() throws Exception {
        Assumptions.assumeTrue(embeddingClient != null,
                "需要配置 embedding(mira.embedding.*) 才能评向量检索");

        List<CorpusItem> corpus = load("/eval/memory-corpus.json", new TypeReference<>() {});
        List<EvalQuery> queries = load("/eval/memory-queries.json", new TypeReference<>() {});

        // 1) 播种语料 + 真实 embedding，按 day 回填时间戳
        indexRepository.deleteAll(USER);
        Instant now = Instant.now();
        for (CorpusItem c : corpus) {
            Instant ts = now.minusSeconds((long) c.day() * 24 * 3600);
            indexRepository.save(MemoryIndex.builder()
                    .id(c.id()).userId(USER)
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.valueOf(c.category()))
                    .contentPreview(c.content())
                    .sourceUri("memory/eval/" + c.id())
                    .retrievalTerms(List.of())
                    .createdAt(ts).updatedAt(ts)
                    .build());
            String vec = "[" + embeddingClient.embed(c.content()).stream()
                    .map(Object::toString).collect(Collectors.joining(",")) + "]";
            jdbc.update("UPDATE memory_index SET embedding = ?::vector, created_at = ?, updated_at = ? WHERE id = ?",
                    vec, java.sql.Timestamp.from(ts), java.sql.Timestamp.from(ts), c.id());
        }
        Integer embedded = jdbc.queryForObject(
                "SELECT count(*) FROM memory_index WHERE user_id = ? AND embedding IS NOT NULL",
                Integer.class, USER);

        // 2) 逐查询算指标（检索 top-10 以便算 MRR/排名；@K 指标取前 K）
        double sumR = 0, sumP = 0, sumMrr = 0, sumNdcg = 0;
        int top1Hits = 0;
        System.out.printf("%n===== 记忆检索数据集评测 (语料 %d 条, 查询 %d 条, K=%d) =====%n",
                corpus.size(), queries.size(), K);
        System.out.println("  查询                          | R@5  | P@5  | nDCG@5 | 首个相关排名 | Top1命中");
        for (EvalQuery q : queries) {
            List<String> got = retriever.retrieve(MemoryRetrieveRequest.builder()
                            .userId(USER).query(q.query()).limit(10).build())
                    .getHits().stream().map(MemoryIndex::getId).toList();

            Set<String> relSet = q.relevant().entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue() > 0)
                    .map(Map.Entry::getKey).collect(Collectors.toSet());

            long hit5 = got.stream().limit(K).filter(relSet::contains).count();
            double recall = relSet.isEmpty() ? 0 : (double) hit5 / relSet.size();
            double precision = (double) hit5 / K;

            int firstRelRank = -1;
            double rr = 0;
            for (int i = 0; i < got.size(); i++) {
                if (relSet.contains(got.get(i))) {
                    firstRelRank = i + 1;
                    rr = 1.0 / (i + 1);
                    break;
                }
            }
            boolean top1 = !got.isEmpty() && relSet.contains(got.get(0));
            if (top1) top1Hits++;
            double ndcg = ndcgAtK(got, q.relevant(), K);

            sumR += recall;
            sumP += precision;
            sumMrr += rr;
            sumNdcg += ndcg;
            System.out.printf("  %-26s | %.2f | %.2f |  %.2f  |     %-4s    |  %s%n",
                    truncate(q.query(), 26), recall, precision, ndcg,
                    firstRelRank < 0 ? ">10" : String.valueOf(firstRelRank), top1 ? "✓" : "✗");
        }

        int nq = queries.size();
        System.out.println("  ------------------------------------------------------------");
        System.out.printf("  宏平均: Recall@%d=%.3f  Precision@%d=%.3f  MRR=%.3f  nDCG@%d=%.3f  Top-1命中率=%.3f%n",
                K, sumR / nq, K, sumP / nq, sumMrr / nq, K, sumNdcg / nq, (double) top1Hits / nq);
        System.out.println("  (P@5 上限受'每查询相关数<5'约束，重点看 Recall/MRR/nDCG；数据集与标注见 resources/eval/*.json)");

        // 极松健全性 gate：仅防"检索整体崩坏"，不对质量阈值硬断言。
        assertTrue(retriever instanceof JdbcHybridMemoryRetriever, "应为向量+词法混合检索器");
        assertTrue(embedded != null && embedded == corpus.size(), "所有语料都应成功写入 embedding");
        assertTrue(sumR / nq >= 0.5, "宏平均 Recall@" + K + " 过低，疑似检索链路异常: " + (sumR / nq));
    }

    /** nDCG@k：分级增益 (2^rel-1)/log2(rank+1)，对理想排序归一化。 */
    private double ndcgAtK(List<String> got, Map<String, Integer> relevant, int k) {
        double dcg = 0;
        for (int i = 0; i < Math.min(k, got.size()); i++) {
            int g = relevant.getOrDefault(got.get(i), 0);
            if (g > 0) {
                dcg += (Math.pow(2, g) - 1) / (Math.log(i + 2) / Math.log(2));
            }
        }
        List<Integer> ideal = relevant.values().stream()
                .filter(v -> v != null && v > 0)
                .sorted(Comparator.reverseOrder()).limit(k).toList();
        double idcg = 0;
        for (int i = 0; i < ideal.size(); i++) {
            idcg += (Math.pow(2, ideal.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return idcg > 0 ? dcg / idcg : 0;
    }

    private <T> List<T> load(String resource, TypeReference<List<T>> type) throws Exception {
        ObjectMapper om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (var in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("找不到评测资源: " + resource);
            }
            return om.readValue(in, type);
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

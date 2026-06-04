package com.felix.miraagent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里百炼(DashScope)文本重排客户端。默认模型 qwen3-rerank，走 Cohere 风格的扁平 reranks 接口
 * （baseUrl 形如 https://dashscope.aliyuncs.com/compatible-api/v1，POST /reranks）。
 * 与 embedding 共用同一把 DashScope api-key。返回分数与入参 documents 顺序对齐。
 */
public class DashScopeRerankClient implements RerankClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankClient.class);

    private final RerankProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeRerankClient(RerankProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public double[] rerank(String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return new double[0];
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getModel());
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", documents.size());

            String responseBody = restClient.post()
                    .uri("/reranks")
                    .body(objectMapper.writeValueAsBytes(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            // 兼容两种返回形态：扁平 results（qwen3-rerank）/ output.results（gte-rerank-v2 原生 API）
            JsonNode results = root.path("results");
            if (results.isMissingNode() || !results.isArray()) {
                results = root.path("output").path("results");
            }

            double[] scores = new double[documents.size()];
            for (JsonNode r : results) {
                int idx = r.path("index").asInt(-1);
                double score = r.path("relevance_score").asDouble(Double.NaN);
                if (idx >= 0 && idx < scores.length && !Double.isNaN(score)) {
                    scores[idx] = score;
                }
            }
            return scores;
        } catch (Exception e) {
            throw new RerankException("Failed to rerank via DashScope", e);
        }
    }
}

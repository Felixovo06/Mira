package com.felix.miraagent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAICompatibleEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleEmbeddingClient.class);

    private final EmbeddingProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAICompatibleEmbeddingClient(EmbeddingProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        try {
            // 显式指定输出维度以匹配 memory_index.embedding 列(如百炼 text-embedding-v4 需 dimensions=1536)
            Map<String, Object> requestBody = props.getDimensions() > 0
                    ? Map.of("model", props.getModel(), "input", text, "dimensions", props.getDimensions())
                    : Map.of("model", props.getModel(), "input", text);

            String responseBody = restClient.post()
                    .uri("/embeddings")
                    .body(objectMapper.writeValueAsBytes(requestBody))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode embeddingArray = root.path("data").path(0).path("embedding");

            List<Float> result = new ArrayList<>(embeddingArray.size());
            for (JsonNode node : embeddingArray) {
                result.add(node.floatValue());
            }
            return result;
        } catch (Exception e) {
            throw new EmbeddingException("Failed to get embedding from API", e);
        }
    }
}

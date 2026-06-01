package com.felix.miraagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ModelProperties.class)
public class ModelAutoConfig {

    // ObjectMapper 由 Spring Boot JacksonAutoConfiguration 提供(含 JavaTimeModule 等),
    // 不再自建裸 new ObjectMapper(), 否则会顶替全局 mapper 导致 Web 层 Instant 序列化失败。

    @Bean
    public RestClient modelRestClient(ModelProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public ModelClient modelClient(RestClient modelRestClient, ModelProperties props, ObjectMapper objectMapper) {
        return new OpenAICompatibleModelClient(modelRestClient, props, objectMapper);
    }
}

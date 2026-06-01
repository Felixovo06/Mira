package com.felix.miraagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ModelProperties.class)
public class ModelAutoConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

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

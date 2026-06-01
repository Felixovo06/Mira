package com.felix.miraagent.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mira.model")
public class ModelProperties {
    private String baseUrl = "https://api.xiaomimimo.com/v1";
    private String apiKey = "";
    private String name = "mimov2.5flash";
    private double temperature = 0.7;
    private int maxTokens = 2048;
}

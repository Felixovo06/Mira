package com.felix.miraagent.agent;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModelConfig {
    String modelName;
    @Builder.Default
    double temperature = 0.7;
    @Builder.Default
    int maxTokens = 2048;
}

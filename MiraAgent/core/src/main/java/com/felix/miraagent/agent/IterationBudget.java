package com.felix.miraagent.agent;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class IterationBudget {
    @Builder.Default
    int maxModelCalls = 10;
    @Builder.Default
    int maxToolCalls = 20;
    Long maxTokens;

    public static IterationBudget defaultBudget() {
        return IterationBudget.builder().build();
    }
}

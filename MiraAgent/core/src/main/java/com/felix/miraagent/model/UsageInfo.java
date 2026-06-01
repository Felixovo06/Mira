package com.felix.miraagent.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UsageInfo {
    int inputTokens;
    int outputTokens;
    int reasoningTokens;
}

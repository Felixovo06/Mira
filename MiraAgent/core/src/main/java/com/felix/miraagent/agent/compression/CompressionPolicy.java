package com.felix.miraagent.agent.compression;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CompressionPolicy {
    double highWatermark;
    double lowWatermark;
    int maxContextTokens;
    int protectRecentRounds;
    int protectFirstMessages;

    public static CompressionPolicy defaultPolicy() {
        return CompressionPolicy.builder()
                .highWatermark(0.75)
                .lowWatermark(0.40)
                .maxContextTokens(128000)
                .protectRecentRounds(4)
                .protectFirstMessages(2)
                .build();
    }
}

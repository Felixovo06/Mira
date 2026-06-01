package com.felix.miraagent.agent.compression;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CompressResult {
    boolean compressed;
    CompressionSummary summary;
}

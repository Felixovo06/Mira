package com.felix.miraagent.memory;

import lombok.Builder;
import lombok.Value;

/**
 * 写入前去重的相似命中：最接近的一条已存记忆及其相似度（0-1）。
 */
@Value
@Builder
public class SimilarMemory {
    MemoryIndex memory;
    double similarity;
}

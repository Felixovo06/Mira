package com.felix.miraagent.memory;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MemoryWriteResult {
    String memoryId;
    String filePath;
    boolean success;
    String error;
    /** true 表示命中写入前去重：未新增卡片，而是合并/强化了既有记忆。 */
    boolean deduplicated;
}

package com.felix.miraagent.memory;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class MemoryIndex {
    String id;
    String userId;
    String characterId;        // nullable
    MemoryScope scope;
    MemoryCategory category;
    String contentPreview;     // 前 500 字
    String sourceUri;          // e.g. "memory/USER.md"
    int confidence;            // 0-100
    String sourceSessionId;    // nullable
    String sourceMessageId;    // nullable
    List<String> retrievalTerms; // 检索关键词
    String embeddingRef;       // nullable，留给 Step 5
    Instant archivedAt;        // nullable，逻辑删除
    Instant createdAt;
    Instant updatedAt;
}

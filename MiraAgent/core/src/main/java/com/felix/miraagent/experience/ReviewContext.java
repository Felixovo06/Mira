package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Value;

/**
 * Background Review 的输入上下文。
 */
@Value
@Builder
public class ReviewContext {
    String userId;
    String characterId;     // nullable
    String sessionId;
    String sourceTraceId;
    String transcript;      // 渲染好的本轮对话/trace
    ReviewSignals signals;
}

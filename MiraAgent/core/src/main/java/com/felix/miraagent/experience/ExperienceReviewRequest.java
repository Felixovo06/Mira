package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Value;

/**
 * 经验提炼输入：本轮 trace/session 的可读转录 + 来源/上下文。
 */
@Value
@Builder
public class ExperienceReviewRequest {
    String userId;
    String characterId;     // nullable
    String sessionId;
    String sourceTraceId;
    String transcript;      // 渲染好的对话/trace 文本
    String focusHint;       // nullable，如"用户说记住这个方法→重点查 skill/memory 写入"
    @Builder.Default
    ConfidenceSource confidenceSource = ConfidenceSource.AGENT_INFERRED;
}

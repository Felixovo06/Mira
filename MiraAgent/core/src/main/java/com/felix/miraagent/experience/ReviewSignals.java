package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Value;

/**
 * 触发 Background Review 门控所需的本轮信号。
 */
@Value
@Builder
public class ReviewSignals {
    int toolCallCount;       // 本轮工具调用次数
    int turnCount;           // 本任务已跨对话轮数（user 轮）
    String userMessageText;  // 本轮用户消息（用于命中信号词）
}

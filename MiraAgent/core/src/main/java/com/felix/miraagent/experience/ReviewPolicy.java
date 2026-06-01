package com.felix.miraagent.experience;

import java.util.List;
import java.util.Optional;

/**
 * Background Review 触发门控（docs/07 §16 决策 2）。满足任一即触发，否则跳过省 token：
 *   - 本轮工具调用 ≥ minToolCalls(默认3)
 *   - 用户消息命中信号词（"记住"/"以后都这样"/"别再"/"下次"）
 *   - 本任务已跨 ≥ minTurns(默认5) 轮
 */
public class ReviewPolicy {

    private static final List<String> DEFAULT_SIGNAL_WORDS = List.of("记住", "以后都这样", "别再", "下次");

    private final int minToolCalls;
    private final int minTurns;
    private final List<String> signalWords;

    public ReviewPolicy() {
        this(3, 5, DEFAULT_SIGNAL_WORDS);
    }

    public ReviewPolicy(int minToolCalls, int minTurns, List<String> signalWords) {
        this.minToolCalls = minToolCalls;
        this.minTurns = minTurns;
        this.signalWords = signalWords != null ? signalWords : DEFAULT_SIGNAL_WORDS;
    }

    /** 命中则返回触发原因（review_triggered_by），否则 empty。信号词优先（决定 confidence 来源）。 */
    public Optional<String> shouldTrigger(ReviewSignals signals) {
        if (signals == null) {
            return Optional.empty();
        }
        String matched = matchSignalWord(signals.getUserMessageText());
        if (matched != null) {
            return Optional.of("signal_word:" + matched);
        }
        if (signals.getToolCallCount() >= minToolCalls) {
            return Optional.of("tool_calls>=" + minToolCalls);
        }
        if (signals.getTurnCount() >= minTurns) {
            return Optional.of("turns>=" + minTurns);
        }
        return Optional.empty();
    }

    /** 信号词触发 → 用户显式要求 → confidence 用 USER_EXPLICIT；否则 AGENT_INFERRED。 */
    public ConfidenceSource confidenceSourceFor(String triggeredBy) {
        return triggeredBy != null && triggeredBy.startsWith("signal_word:")
                ? ConfidenceSource.USER_EXPLICIT
                : ConfidenceSource.AGENT_INFERRED;
    }

    private String matchSignalWord(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String word : signalWords) {
            if (text.contains(word)) {
                return word;
            }
        }
        return null;
    }
}

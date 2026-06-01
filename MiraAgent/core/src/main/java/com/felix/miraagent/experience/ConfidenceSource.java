package com.felix.miraagent.experience;

/**
 * confidence 按来源定死（docs/07 §16 决策 1）：用户明说 1.0 / 工具确认 0.8 / Agent 抽取 0.6。
 * 不让 LLM 自由设置 confidence，由来源决定。
 */
public enum ConfidenceSource {
    USER_EXPLICIT(1.0),
    TOOL_CONFIRMED(0.8),
    AGENT_INFERRED(0.6);

    private final double value;

    ConfidenceSource(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }
}

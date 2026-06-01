package com.felix.miraagent.agent.compression;

public class ContextPressureMeter {

    private ContextPressureMeter() {
    }

    public static double measure(int lastRealInputTokens, int maxContextTokens) {
        if (maxContextTokens <= 0 || lastRealInputTokens <= 0) return 0.0;
        return (double) lastRealInputTokens / maxContextTokens;
    }
}

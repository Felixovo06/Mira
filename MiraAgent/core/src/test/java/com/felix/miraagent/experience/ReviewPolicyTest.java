package com.felix.miraagent.experience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviewPolicyTest {

    private final ReviewPolicy policy = new ReviewPolicy();

    private ReviewSignals signals(int tools, int turns, String text) {
        return ReviewSignals.builder().toolCallCount(tools).turnCount(turns).userMessageText(text).build();
    }

    @Test
    void triggersOnThreeToolCalls() {
        assertEquals("tool_calls>=3", policy.shouldTrigger(signals(3, 1, "随便聊")).orElseThrow());
    }

    @Test
    void triggersOnSignalWord() {
        assertEquals("signal_word:记住", policy.shouldTrigger(signals(0, 1, "记住我喜欢早睡")).orElseThrow());
        assertTrue(policy.shouldTrigger(signals(0, 1, "以后都这样做")).isPresent());
        assertTrue(policy.shouldTrigger(signals(0, 1, "别再这么干")).isPresent());
        assertTrue(policy.shouldTrigger(signals(0, 1, "下次记得")).isPresent());
    }

    @Test
    void triggersOnFiveTurns() {
        assertEquals("turns>=5", policy.shouldTrigger(signals(0, 5, "继续")).orElseThrow());
    }

    @Test
    void skipsSimpleChat() {
        assertTrue(policy.shouldTrigger(signals(1, 2, "今天天气不错")).isEmpty());
    }

    @Test
    void signalWordTakesPriorityAndMapsUserExplicitConfidence() {
        String trigger = policy.shouldTrigger(signals(5, 9, "记住这个方法")).orElseThrow();
        assertTrue(trigger.startsWith("signal_word:"));
        assertEquals(ConfidenceSource.USER_EXPLICIT, policy.confidenceSourceFor(trigger));
        assertEquals(ConfidenceSource.AGENT_INFERRED, policy.confidenceSourceFor("tool_calls>=3"));
    }
}

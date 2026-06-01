package com.felix.miraagent.agent;

import com.felix.miraagent.agent.impl.ConversationLoop;
import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.experience.BackgroundReview;
import com.felix.miraagent.experience.ExperienceApplier;
import com.felix.miraagent.experience.ExperienceExtractor;
import com.felix.miraagent.experience.ExperienceReviewRequest;
import com.felix.miraagent.experience.ExperienceReviewResult;
import com.felix.miraagent.experience.ReviewPolicy;
import com.felix.miraagent.fake.FakeModelClient;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.prompt.impl.DefaultPromptBuilder;
import com.felix.miraagent.session.impl.InMemorySessionStore;
import com.felix.miraagent.tools.builtin.BuiltinTools;
import com.felix.miraagent.tools.impl.DefaultToolDispatcher;
import com.felix.miraagent.tools.impl.DefaultToolPermissionPolicy;
import com.felix.miraagent.tools.impl.InMemoryToolExecutionStore;
import com.felix.miraagent.tools.impl.InMemoryToolRegistry;
import com.felix.miraagent.trace.TraceEventType;
import com.felix.miraagent.trace.impl.InMemoryTraceStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConversationLoopReviewTest {

    static class CountingExtractor implements ExperienceExtractor {
        final AtomicInteger calls = new AtomicInteger();
        volatile ExperienceReviewRequest lastRequest;
        @Override public ExperienceReviewResult extract(ExperienceReviewRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            return ExperienceReviewResult.nothing();
        }
    }

    private ConversationLoop loop(InMemoryTraceStore traceStore, BackgroundReview review) {
        var toolRegistry = new InMemoryToolRegistry();
        BuiltinTools.registerAll(toolRegistry);
        return new ConversationLoop(
                new FakeModelClient().thenReply("好的~"),
                new DefaultPromptBuilder(), toolRegistry, new DefaultToolDispatcher(toolRegistry),
                new InMemorySessionStore(), traceStore, new InMemoryToolExecutionStore(),
                null, null, null, null, null, null, null, null, review);
    }

    private AgentRunRequest request(String runId, String userText) {
        return AgentRunRequest.builder()
                .runId(runId).userId("u1").sessionId("s1")
                .characterProfile(CharacterProfile.defaultProfile())
                .messages(List.of(Message.builder().id(UUID.randomUUID().toString())
                        .role(MessageRole.USER).content(userText).build()))
                .modelConfig(ModelConfig.builder().modelName("fake").build())
                .iterationBudget(IterationBudget.defaultBudget())
                .permissionPolicy(new DefaultToolPermissionPolicy())
                .build();
    }

    private BackgroundReview review(CountingExtractor extractor) {
        return new BackgroundReview(new ReviewPolicy(), extractor, new ExperienceApplier(null, null));
    }

    @Test
    void signalWordTriggersReviewAndTrace() throws Exception {
        var trace = new InMemoryTraceStore();
        var extractor = new CountingExtractor();
        String runId = UUID.randomUUID().toString();

        loop(trace, review(extractor)).run(request(runId, "记住我喜欢早睡"));

        long deadline = System.currentTimeMillis() + 2000;
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            found = trace.findByRunId(runId).stream()
                    .anyMatch(e -> e.getEventType() == TraceEventType.BACKGROUND_REVIEW_FINISHED);
            if (found) break;
            Thread.sleep(10);
        }
        assertTrue(found, "expected BACKGROUND_REVIEW_FINISHED trace");
        assertEquals(1, extractor.calls.get());
        assertNotNull(extractor.lastRequest);
        assertTrue(extractor.lastRequest.getTranscript().contains("[ASSISTANT]: 好的~"));
    }

    @Test
    void simpleChatDoesNotTriggerReview() throws Exception {
        var trace = new InMemoryTraceStore();
        var extractor = new CountingExtractor();
        String runId = UUID.randomUUID().toString();

        loop(trace, review(extractor)).run(request(runId, "今天天气不错"));

        Thread.sleep(150);
        assertEquals(0, extractor.calls.get());
        assertTrue(trace.findByRunId(runId).stream()
                .noneMatch(e -> e.getEventType() == TraceEventType.BACKGROUND_REVIEW_FINISHED));
    }
}

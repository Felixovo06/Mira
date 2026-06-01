package com.felix.miraagent.experience;

import com.felix.miraagent.memory.MemoryWriteRequest;
import com.felix.miraagent.memory.MemoryWriteResult;
import com.felix.miraagent.memory.SerializedMemoryWriter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundReviewTest {

    static class FakeMemoryWriter implements SerializedMemoryWriter {
        int count = 0;
        @Override public MemoryWriteResult submit(MemoryWriteRequest r) {
            count++; return MemoryWriteResult.builder().memoryId("m").success(true).build();
        }
        @Override public MemoryWriteResult archive(String u, String id) { return null; }
        @Override public void shutdown() { }
    }

    private ReviewContext ctx(int tools, int turns, String userText) {
        return ReviewContext.builder()
                .userId("u1").sessionId("s1").sourceTraceId("trace-1")
                .transcript("[USER] " + userText)
                .signals(ReviewSignals.builder().toolCallCount(tools).turnCount(turns).userMessageText(userText).build())
                .build();
    }

    @Test
    void skipsWhenGateNotMet() {
        var extractor = new CapturingExtractor(ExperienceReviewResult.nothing());
        var review = new BackgroundReview(new ReviewPolicy(), extractor,
                new ExperienceApplier(new FakeMemoryWriter(), null));
        ReviewResult r = review.review(ctx(0, 1, "随便聊"));
        assertFalse(r.isTriggered());
        assertNull(extractor.lastRequest); // 未触发 → 不调用 LLM，省 token
    }

    @Test
    void triggeredRunsExtractAndApply() {
        var mem = new FakeMemoryWriter();
        var extractor = new CapturingExtractor(ExperienceReviewResult.builder()
                .worthSaving(true)
                .memoryWrite(MemoryWritePlan.builder().kind("fact").content("用户在准备考研").scope("global").build())
                .build());
        var review = new BackgroundReview(new ReviewPolicy(), extractor, new ExperienceApplier(mem, null));

        ReviewResult r = review.review(ctx(3, 1, "帮我做点事"));
        assertTrue(r.isTriggered());
        assertEquals("tool_calls>=3", r.getTriggeredBy());
        assertEquals(1, r.getMemoriesWritten());
        assertEquals(1, mem.count);
    }

    @Test
    void signalWordSetsUserExplicitConfidence() {
        var extractor = new CapturingExtractor(ExperienceReviewResult.nothing());
        var review = new BackgroundReview(new ReviewPolicy(), extractor,
                new ExperienceApplier(new FakeMemoryWriter(), null));
        review.review(ctx(0, 1, "记住我喜欢简洁"));
        assertNotNull(extractor.lastRequest);
        assertEquals(ConfidenceSource.USER_EXPLICIT, extractor.lastRequest.getConfidenceSource());
        assertNotNull(extractor.lastRequest.getFocusHint());
    }

    @Test
    void reviewAsyncDoesNotBlockAndEventuallyCompletes() throws Exception {
        var done = new AtomicReference<ReviewResult>();
        var extractor = new CapturingExtractor(ExperienceReviewResult.nothing());
        var review = new BackgroundReview(new ReviewPolicy(), extractor,
                new ExperienceApplier(new FakeMemoryWriter(), null));

        review.reviewAsync(ctx(3, 1, "x"), done::set);
        // 不阻塞：立即返回；轮询等待后台完成
        long deadline = System.currentTimeMillis() + 2000;
        while (done.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull(done.get());
        assertTrue(done.get().isTriggered());
    }

    @Test
    void reviewAsyncSkipsThreadWhenGateNotMet() throws Exception {
        var done = new AtomicReference<ReviewResult>();
        var review = new BackgroundReview(new ReviewPolicy(), new CapturingExtractor(ExperienceReviewResult.nothing()),
                new ExperienceApplier(new FakeMemoryWriter(), null));
        review.reviewAsync(ctx(0, 1, "闲聊"), done::set);
        Thread.sleep(100);
        assertNull(done.get()); // 未触发 → 回调不会被调用
    }

    static class CapturingExtractor implements ExperienceExtractor {
        final ExperienceReviewResult canned;
        volatile ExperienceReviewRequest lastRequest;
        CapturingExtractor(ExperienceReviewResult canned) { this.canned = canned; }
        @Override public ExperienceReviewResult extract(ExperienceReviewRequest request) {
            this.lastRequest = request;
            return canned;
        }
    }
}

package com.felix.miraagent.experience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 后台经验复盘：门控触发 → 一次 LLM 提炼 → 落地 memory/skill。与主回复解耦、异步不阻塞。
 * 安全边界：能力白名单 = ExperienceApplier 只能写 memory + skill（无普通工具执行面）；
 * 所有写入带 source_trace_id；不弹确认打断用户。
 */
public class BackgroundReview {

    private static final Logger log = LoggerFactory.getLogger(BackgroundReview.class);

    private final ReviewPolicy policy;
    private final ExperienceExtractor extractor;
    private final ExperienceApplier applier;

    public BackgroundReview(ReviewPolicy policy, ExperienceExtractor extractor, ExperienceApplier applier) {
        this.policy = policy;
        this.extractor = extractor;
        this.applier = applier;
    }

    /** 同步执行（便于测试）。未触发返回 skipped()。 */
    public ReviewResult review(ReviewContext ctx) {
        Optional<String> trigger = policy.shouldTrigger(ctx != null ? ctx.getSignals() : null);
        if (trigger.isEmpty()) {
            return ReviewResult.skipped();
        }
        String triggeredBy = trigger.get();
        try {
            ExperienceReviewRequest request = ExperienceReviewRequest.builder()
                    .userId(ctx.getUserId())
                    .characterId(ctx.getCharacterId())
                    .sessionId(ctx.getSessionId())
                    .sourceTraceId(ctx.getSourceTraceId())
                    .transcript(ctx.getTranscript())
                    .confidenceSource(policy.confidenceSourceFor(triggeredBy))
                    .focusHint(focusHintFor(triggeredBy))
                    .build();
            ExperienceReviewResult extracted = extractor.extract(request);
            ExperienceApplier.ApplyResult applied = applier.apply(extracted, request);
            return ReviewResult.builder()
                    .triggered(true)
                    .triggeredBy(triggeredBy)
                    .memoriesWritten(applied.getMemoriesWritten())
                    .skillsWritten(applied.getSkillsWritten())
                    .build();
        } catch (Exception e) {
            log.warn("Background review failed (triggeredBy={}): {}", triggeredBy, e.getMessage());
            return ReviewResult.builder().triggered(true).triggeredBy(triggeredBy).build();
        }
    }

    /** 异步执行：在虚拟线程上跑，绝不阻塞/抛出到主回复。完成后回调（用于落 trace）。 */
    public void reviewAsync(ReviewContext ctx, Consumer<ReviewResult> onComplete) {
        // 先廉价地做门控判断，未触发就完全不开线程
        if (policy.shouldTrigger(ctx != null ? ctx.getSignals() : null).isEmpty()) {
            return;
        }
        Thread.ofVirtual().name("background-review").start(() -> {
            try {
                ReviewResult result = review(ctx);
                if (onComplete != null) {
                    onComplete.accept(result);
                }
            } catch (Exception e) {
                log.warn("Async background review error: {}", e.getMessage());
            }
        });
    }

    private String focusHintFor(String triggeredBy) {
        if (triggeredBy != null && triggeredBy.startsWith("signal_word:")) {
            return "用户显式要求记住/调整流程，重点检查 memory 与 skill 写入";
        }
        return null;
    }
}

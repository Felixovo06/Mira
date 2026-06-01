package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Value;

/**
 * 一次 Background Review 的结果（也用于落 trace）。
 */
@Value
@Builder
public class ReviewResult {
    boolean triggered;
    String triggeredBy;      // nullable，未触发时为空
    int memoriesWritten;
    int skillsWritten;

    public static ReviewResult skipped() {
        return ReviewResult.builder().triggered(false).build();
    }
}

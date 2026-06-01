package com.felix.miraagent.tools;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ToolExecutionResult {
    String toolCallId;
    String toolName;
    ToolStatus status;
    String modelVisibleContent;
    String artifactRef;
    String error;
    @Builder.Default
    Instant startedAt = Instant.now();
    Instant finishedAt;

    public static ToolExecutionResult success(String toolCallId, String toolName, String content) {
        return ToolExecutionResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .status(ToolStatus.SUCCESS)
                .modelVisibleContent(content)
                .finishedAt(Instant.now())
                .build();
    }

    public static ToolExecutionResult error(String toolCallId, String toolName, String errorMessage) {
        return ToolExecutionResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .status(ToolStatus.ERROR)
                .modelVisibleContent("Tool execution failed: " + errorMessage)
                .error(errorMessage)
                .finishedAt(Instant.now())
                .build();
    }

    public static ToolExecutionResult denied(String toolCallId, String toolName, String reason) {
        return ToolExecutionResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .status(ToolStatus.DENIED)
                .modelVisibleContent("Permission denied: " + reason)
                .error(reason)
                .finishedAt(Instant.now())
                .build();
    }
}

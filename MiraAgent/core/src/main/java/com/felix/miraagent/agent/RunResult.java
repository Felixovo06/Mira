package com.felix.miraagent.agent;

import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.UsageInfo;
import com.felix.miraagent.tools.ToolExecutionResult;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RunResult {
    String runId;
    String sessionId;
    RunStatus status;
    Message finalMessage;
    @Singular
    List<ToolExecutionResult> toolExecutions;
    String traceId;
    UsageInfo usage;
    String error;

    public boolean isSuccess() {
        return status == RunStatus.SUCCESS;
    }
}

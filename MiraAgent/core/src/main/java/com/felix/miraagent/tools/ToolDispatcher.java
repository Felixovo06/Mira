package com.felix.miraagent.tools;

import com.felix.miraagent.model.ToolCall;

import java.util.List;

public interface ToolDispatcher {
    List<ToolExecutionResult> dispatchAll(List<ToolCall> calls, ToolDispatchContext context);

    ToolExecutionResult dispatchOne(ToolCall call, ToolDispatchContext context);
}

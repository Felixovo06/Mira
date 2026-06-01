package com.felix.miraagent.agent;

public interface AgentRuntime {
    RunResult chat(ChatInput input);

    RunResult runConversation(AgentRunRequest request);

    void interrupt(String runId);
}

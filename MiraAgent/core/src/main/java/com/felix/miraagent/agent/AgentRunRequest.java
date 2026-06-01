package com.felix.miraagent.agent;

import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.StreamCallback;
import com.felix.miraagent.tools.ToolPermissionPolicy;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentRunRequest {
    String runId;
    String userId;
    String sessionId;
    CharacterProfile characterProfile;
    @Singular
    List<Message> messages;
    ModelConfig modelConfig;
    ToolConfig toolConfig;
    @Builder.Default
    IterationBudget iterationBudget = IterationBudget.defaultBudget();
    ToolPermissionPolicy permissionPolicy;
    StreamCallback streamCallback;
    @Builder.Default
    InterruptSignal interruptSignal = new InterruptSignal();
}

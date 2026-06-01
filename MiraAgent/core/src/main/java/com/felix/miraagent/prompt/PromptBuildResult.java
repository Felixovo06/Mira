package com.felix.miraagent.prompt;

import com.felix.miraagent.model.Message;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PromptBuildResult {
    String stableSystemPrompt;
    String ephemeralPrompt;
    @Singular
    List<Message> messages;
    int tokenEstimate;
    @Singular
    List<String> includedMemoryRefs;
    @Singular
    List<String> includedSkillRefs;
}

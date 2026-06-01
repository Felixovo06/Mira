package com.felix.miraagent.prompt;

import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.tools.ToolDefinition;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PromptBuildRequest {
    CharacterProfile characterProfile;
    String userProfileSummary;
    String relationshipMemory;
    @Singular("retrievedMemory")
    List<String> retrievedMemories;
    @Singular("sessionHistoryItem")
    List<Message> sessionHistory;
    @Singular("toolDefinition")
    List<ToolDefinition> toolDefinitions;
    String skillIndex;
    String temporaryInstructions;
    Integer contextBudget;
}

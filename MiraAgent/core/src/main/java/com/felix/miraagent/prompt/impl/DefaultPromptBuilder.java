package com.felix.miraagent.prompt.impl;

import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.prompt.PromptBuildRequest;
import com.felix.miraagent.prompt.PromptBuildResult;
import com.felix.miraagent.prompt.PromptBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DefaultPromptBuilder implements PromptBuilder {

    private final CharacterPromptComposer characterComposer;
    private final ToolSchemaInjector toolInjector;

    public DefaultPromptBuilder() {
        this.characterComposer = new CharacterPromptComposer();
        this.toolInjector = new ToolSchemaInjector();
    }

    @Override
    public PromptBuildResult build(PromptBuildRequest request) {
        String stableSystemPrompt = buildStableSystemPrompt(request);
        String ephemeralPrompt = buildEphemeralPrompt(request);

        List<Message> messages = buildMessages(stableSystemPrompt, ephemeralPrompt, request);

        return PromptBuildResult.builder()
                .stableSystemPrompt(stableSystemPrompt)
                .ephemeralPrompt(ephemeralPrompt)
                .messages(messages)
                .tokenEstimate(estimateTokens(stableSystemPrompt, ephemeralPrompt, request))
                .build();
    }

    private String buildStableSystemPrompt(PromptBuildRequest request) {
        var parts = new ArrayList<String>();

        String characterSection = characterComposer.compose(request.getCharacterProfile());
        if (!characterSection.isBlank()) {
            parts.add(characterSection);
        }

        if (hasText(request.getUserProfileSummary())) {
            parts.add("# User Profile\n\n" + request.getUserProfileSummary());
        }

        if (hasText(request.getRelationshipMemory())) {
            parts.add("# Relationship\n\n" + request.getRelationshipMemory());
        }

        if (!request.getRetrievedMemories().isEmpty()) {
            parts.add("# Relevant Memories\n\n" + String.join("\n", request.getRetrievedMemories()));
        }

        if (hasText(request.getSkillIndex())) {
            parts.add("# Skills\n\n" + request.getSkillIndex());
        }

        String toolSection = toolInjector.inject(request.getToolDefinitions());
        if (!toolSection.isBlank()) {
            parts.add(toolSection);
        }

        return String.join("\n\n---\n\n", parts);
    }

    private String buildEphemeralPrompt(PromptBuildRequest request) {
        return hasText(request.getTemporaryInstructions()) ? request.getTemporaryInstructions() : "";
    }

    private List<Message> buildMessages(String stableSystemPrompt, String ephemeralPrompt, PromptBuildRequest request) {
        var messages = new ArrayList<Message>();

        String systemContent = stableSystemPrompt;
        if (hasText(ephemeralPrompt)) {
            systemContent = hasText(systemContent)
                    ? systemContent + "\n\n---\n\n" + ephemeralPrompt
                    : ephemeralPrompt;
        }

        if (hasText(systemContent)) {
            messages.add(Message.builder()
                    .id(UUID.randomUUID().toString())
                    .role(MessageRole.SYSTEM)
                    .content(systemContent)
                    .build());
        }

        messages.addAll(request.getSessionHistory());
        return messages;
    }

    private int estimateTokens(String stable, String ephemeral, PromptBuildRequest request) {
        int chars = (stable != null ? stable.length() : 0)
                + (ephemeral != null ? ephemeral.length() : 0);
        for (Message m : request.getSessionHistory()) {
            if (m.getContent() != null) chars += m.getContent().length();
        }
        return chars / 4;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

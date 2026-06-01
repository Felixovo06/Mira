package com.felix.miraagent.prompt;

import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.prompt.impl.DefaultPromptBuilder;
import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolRiskLevel;
import com.felix.miraagent.tools.builtin.BuiltinTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DefaultPromptBuilder();
    }

    @Test
    void differentCharacterProfilesProduceDifferentSystemPrompt() {
        var mira = CharacterProfile.builder().id("mira").name("Mira").personality("Warm and caring").build();
        var kai = CharacterProfile.builder().id("kai").name("Kai").personality("Sharp and analytical").build();

        var requestMira = PromptBuildRequest.builder().characterProfile(mira).build();
        var requestKai = PromptBuildRequest.builder().characterProfile(kai).build();

        var resultMira = builder.build(requestMira);
        var resultKai = builder.build(requestKai);

        assertNotEquals(resultMira.getStableSystemPrompt(), resultKai.getStableSystemPrompt());
        assertTrue(resultMira.getStableSystemPrompt().contains("Mira"));
        assertTrue(resultKai.getStableSystemPrompt().contains("Kai"));
    }

    @Test
    void toolDefinitionsChangeToolSection() {
        var noTools = PromptBuildRequest.builder().build();
        var withTools = PromptBuildRequest.builder()
                .toolDefinition(BuiltinTools.noteDefinition())
                .toolDefinition(BuiltinTools.todoDefinition())
                .build();

        var resultNoTools = builder.build(noTools);
        var resultWithTools = builder.build(withTools);

        assertTrue(resultWithTools.getStableSystemPrompt().contains("note"));
        assertFalse(resultNoTools.getStableSystemPrompt().contains("note"));
    }

    @Test
    void temporaryInstructionsDoNotAffectStablePrompt() {
        var request = PromptBuildRequest.builder()
                .characterProfile(CharacterProfile.defaultProfile())
                .temporaryInstructions("TEMP: focus on brevity this turn")
                .build();

        var result = builder.build(request);

        assertFalse(result.getStableSystemPrompt().contains("TEMP:"),
                "temporary instructions must not appear in stableSystemPrompt");
        assertTrue(result.getEphemeralPrompt().contains("TEMP:"));
    }

    @Test
    void sessionHistoryIsIncludedInMessages() {
        var history = List.of(
                Message.builder().id(UUID.randomUUID().toString()).role(MessageRole.USER).content("hello").build(),
                Message.builder().id(UUID.randomUUID().toString()).role(MessageRole.ASSISTANT).content("hi").build()
        );
        var request = PromptBuildRequest.builder()
                .sessionHistoryItem(history.get(0))
                .sessionHistoryItem(history.get(1))
                .build();

        var result = builder.build(request);
        var messages = result.getMessages();

        assertEquals(MessageRole.USER, messages.get(messages.size() - 2).getRole());
        assertEquals(MessageRole.ASSISTANT, messages.get(messages.size() - 1).getRole());
    }

    @Test
    void firstMessageIsSystemWhenCharacterPresent() {
        var request = PromptBuildRequest.builder()
                .characterProfile(CharacterProfile.defaultProfile())
                .build();
        var result = builder.build(request);
        assertFalse(result.getMessages().isEmpty());
        assertEquals(MessageRole.SYSTEM, result.getMessages().get(0).getRole());
    }
}

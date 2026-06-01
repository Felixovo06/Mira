package com.felix.miraagent.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageSequenceTest {

    private final MessageSequenceValidator validator = new MessageSequenceValidator();

    private Message user(String content) {
        return Message.builder().id(UUID.randomUUID().toString()).role(MessageRole.USER).content(content).build();
    }

    private Message assistant(String content) {
        return Message.builder().id(UUID.randomUUID().toString()).role(MessageRole.ASSISTANT).content(content).build();
    }

    private Message assistantWithToolCalls(ToolCall... calls) {
        return Message.builder().id(UUID.randomUUID().toString()).role(MessageRole.ASSISTANT).toolCalls(List.of(calls)).build();
    }

    private Message toolResult(String toolCallId, String toolName, String content) {
        return Message.builder().id(UUID.randomUUID().toString()).role(MessageRole.TOOL)
                .toolCallId(toolCallId).toolName(toolName).content(content).build();
    }

    private ToolCall toolCall(String id, String name) {
        return ToolCall.builder().id(id).name(name).arguments("{}").build();
    }

    @Test
    void validUserAssistant() {
        var messages = List.of(user("hello"), assistant("hi"));
        assertTrue(validator.validate(messages).valid());
    }

    @Test
    void validToolCallChain() {
        ToolCall tc = toolCall("tc1", "note");
        var messages = List.of(
                user("write a note"),
                assistantWithToolCalls(tc),
                toolResult("tc1", "note", "saved"),
                assistant("Done, I saved the note.")
        );
        assertTrue(validator.validate(messages).valid());
    }

    @Test
    void invalidConsecutiveUser() {
        var messages = List.of(user("hello"), user("are you there?"));
        var result = validator.validate(messages);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("USER"));
    }

    @Test
    void invalidConsecutiveAssistant() {
        var messages = List.of(user("hello"), assistant("hi"), assistant("how are you?"));
        var result = validator.validate(messages);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("ASSISTANT"));
    }

    @Test
    void invalidToolWithoutPrecedingToolCall() {
        var messages = List.of(user("hello"), toolResult("tc1", "note", "saved"));
        var result = validator.validate(messages);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("TOOL"));
    }
}

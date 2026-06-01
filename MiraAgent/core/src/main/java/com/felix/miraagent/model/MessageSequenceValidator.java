package com.felix.miraagent.model;

import java.util.List;

public class MessageSequenceValidator {

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }

    public ValidationResult validate(List<Message> messages) {
        for (int i = 1; i < messages.size(); i++) {
            Message prev = messages.get(i - 1);
            Message curr = messages.get(i);

            if (prev.getRole() == MessageRole.USER && curr.getRole() == MessageRole.USER) {
                return ValidationResult.fail("Consecutive USER messages at index " + (i - 1) + " and " + i);
            }

            if (prev.getRole() == MessageRole.ASSISTANT
                    && (prev.getToolCalls() == null || prev.getToolCalls().isEmpty())
                    && curr.getRole() == MessageRole.ASSISTANT) {
                return ValidationResult.fail("Consecutive ASSISTANT messages (no tool call) at index " + (i - 1) + " and " + i);
            }

            if (curr.getRole() == MessageRole.TOOL) {
                if (prev.getRole() != MessageRole.ASSISTANT
                        || prev.getToolCalls() == null
                        || prev.getToolCalls().isEmpty()) {
                    return ValidationResult.fail("TOOL message at index " + i + " is not preceded by ASSISTANT with tool_calls");
                }
            }
        }
        return ValidationResult.ok();
    }
}

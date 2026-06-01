package com.felix.miraagent.api.dto;

import com.felix.miraagent.model.Message;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
public class MessageDto {
    String id;
    String role;
    String content;
    String toolCallId;
    String toolName;
    List<ToolCallDto> toolCalls;
    Instant createdAt;

    @Value
    public static class ToolCallDto {
        String id;
        String name;
        String arguments;
    }

    public static MessageDto from(Message msg) {
        List<ToolCallDto> tcs = msg.getToolCalls() == null ? null :
                msg.getToolCalls().stream()
                        .map(tc -> new ToolCallDto(tc.getId(), tc.getName(), tc.getArguments()))
                        .toList();
        return new MessageDto(
                msg.getId(),
                msg.getRole().name().toLowerCase(),
                msg.getContent(),
                msg.getToolCallId(),
                msg.getToolName(),
                tcs,
                msg.getCreatedAt()
        );
    }
}

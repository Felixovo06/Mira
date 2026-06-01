package com.felix.miraagent.api.controller;

import com.felix.miraagent.agent.AgentRuntime;
import com.felix.miraagent.agent.ChatInput;
import com.felix.miraagent.agent.RunResult;
import com.felix.miraagent.api.dto.ChatApiRequest;
import com.felix.miraagent.api.dto.ChatApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentRuntime agentRuntime;

    public ChatController(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatApiResponse> chat(@RequestBody ChatApiRequest req) {
        ChatInput input = buildInput(req);
        RunResult result = agentRuntime.chat(input);
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatApiRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);
        ChatInput input = buildInput(req);

        Thread.ofVirtual().start(() -> {
            try {
                String sessionId = input.getSessionId();
                String runId = UUID.randomUUID().toString();

                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("{\"runId\":\"" + runId + "\",\"sessionId\":\"" + sessionId + "\"}"));

                RunResult result = agentRuntime.chat(input);
                ChatApiResponse response = toResponse(result);

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(response));

                emitter.complete();
            } catch (IOException e) {
                log.debug("SSE client disconnected: {}", e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("SSE stream error", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"" + e.getMessage() + "\"}"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private ChatInput buildInput(ChatApiRequest req) {
        String sessionId = req.getSessionId() != null ? req.getSessionId() : UUID.randomUUID().toString();
        String userId = req.getUserId() != null ? req.getUserId() : "anonymous";
        return ChatInput.builder()
                .userId(userId)
                .sessionId(sessionId)
                .characterId(req.getCharacterId())
                .content(req.getContent())
                .enabledTools(req.getEnabledTools() != null ? req.getEnabledTools() : List.of())
                .stream(req.isStream())
                .build();
    }

    private ChatApiResponse toResponse(RunResult result) {
        List<ChatApiResponse.ToolExecutionDto> toolDtos = result.getToolExecutions() == null ? List.of() :
                result.getToolExecutions().stream()
                        .map(t -> ChatApiResponse.ToolExecutionDto.builder()
                                .toolCallId(t.getToolCallId())
                                .toolName(t.getToolName())
                                .status(t.getStatus().name())
                                .content(t.getModelVisibleContent())
                                .build())
                        .toList();

        ChatApiResponse.UsageDto usageDto = null;
        if (result.getUsage() != null) {
            usageDto = ChatApiResponse.UsageDto.builder()
                    .inputTokens(result.getUsage().getInputTokens())
                    .outputTokens(result.getUsage().getOutputTokens())
                    .build();
        }

        String content = result.getFinalMessage() != null ? result.getFinalMessage().getContent() : null;

        return ChatApiResponse.builder()
                .runId(result.getRunId())
                .sessionId(result.getSessionId())
                .content(content)
                .status(result.getStatus().name())
                .toolExecutions(toolDtos)
                .usage(usageDto)
                .error(result.getError())
                .build();
    }
}

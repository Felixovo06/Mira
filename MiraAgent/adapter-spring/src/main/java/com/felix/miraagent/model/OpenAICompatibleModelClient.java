package com.felix.miraagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.tools.ToolDefinition;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

public class OpenAICompatibleModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleModelClient.class);

    private final RestClient restClient;
    private final ModelProperties props;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleModelClient(RestClient restClient, ModelProperties props, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var body = buildRequestBody(request);
        try {
            var oaiResponse = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(OAIResponse.class);

            return convertResponse(oaiResponse);
        } catch (RestClientException e) {
            log.error("Model API call failed: {}", e.getMessage());
            throw new ModelException("Model API call failed: " + e.getMessage(), props.getName(), -1, e);
        }
    }

    @Override
    public StreamHandle streamChat(ChatRequest request, StreamCallback callback) {
        var body = buildRequestBody(request);
        body.put("stream", true);

        var aborted = new boolean[]{false};

        Thread.ofVirtual().start(() -> {
            try {
                restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        .onStatus(status -> !status.is2xxSuccessful(), (req, resp) -> {
                            throw new ModelException("Stream failed: " + resp.getStatusCode(), props.getName(), resp.getStatusCode().value());
                        })
                        .toBodilessEntity();
            } catch (Exception e) {
                if (!aborted[0]) {
                    log.error("Streaming failed", e);
                    callback.onDelta(StreamDelta.builder().done(true).finishReason("error").build());
                }
            }
        });

        return new StreamHandle() {
            public void abort() { aborted[0] = true; }
            public boolean isComplete() { return false; }
        };
    }

    @Override
    public boolean supports(ModelCapability capability) {
        return capability == ModelCapability.TOOL_CALLING || capability == ModelCapability.STREAMING;
    }

    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getName());
        body.put("temperature", request.getTemperature() != null ? request.getTemperature() : props.getTemperature());
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : props.getMaxTokens());
        body.put("messages", convertMessages(request.getMessages()));

        if (!request.getTools().isEmpty()) {
            body.put("tools", convertTools(request.getTools()));
            if (request.getToolChoice() != null) {
                body.put("tool_choice", request.getToolChoice());
            }
        }
        return body;
    }

    private List<Map<String, Object>> convertMessages(List<com.felix.miraagent.model.Message> messages) {
        var result = new ArrayList<Map<String, Object>>();
        for (var msg : messages) {
            var m = new LinkedHashMap<String, Object>();
            m.put("role", msg.getRole().name().toLowerCase());

            if (msg.getContent() != null) {
                m.put("content", msg.getContent());
            }

            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                var tcs = msg.getToolCalls().stream().map(tc -> {
                    var tcMap = new LinkedHashMap<String, Object>();
                    tcMap.put("id", tc.getId());
                    tcMap.put("type", "function");
                    tcMap.put("function", Map.of("name", tc.getName(), "arguments", tc.getArguments()));
                    return tcMap;
                }).toList();
                m.put("tool_calls", tcs);
            }

            if (msg.getToolCallId() != null) {
                m.put("tool_call_id", msg.getToolCallId());
            }

            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> convertTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var tool : tools) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tool.getName());
            func.put("description", tool.getDescription());
            if (tool.getInputSchema() != null) {
                func.put("parameters", tool.getInputSchema());
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", "function");
            wrapper.put("function", func);
            result.add(wrapper);
        }
        return result;
    }

    private ChatResponse convertResponse(OAIResponse oaiResp) {
        if (oaiResp == null || oaiResp.getChoices() == null || oaiResp.getChoices().isEmpty()) {
            return ChatResponse.builder()
                    .error(new ModelException("Empty response from model", props.getName(), -1))
                    .build();
        }

        var choice = oaiResp.getChoices().get(0);
        var oaiMsg = choice.getMessage();

        var msgBuilder = com.felix.miraagent.model.Message.builder()
                .id(UUID.randomUUID().toString())
                .role(MessageRole.ASSISTANT)
                .content(oaiMsg.getContent());

        var responseBuilder = ChatResponse.builder()
                .finishReason(choice.getFinishReason());

        if (oaiMsg.getToolCalls() != null && !oaiMsg.getToolCalls().isEmpty()) {
            for (var oaiTc : oaiMsg.getToolCalls()) {
                var tc = ToolCall.builder()
                        .id(oaiTc.getId())
                        .name(oaiTc.getFunction().getName())
                        .arguments(oaiTc.getFunction().getArguments())
                        .build();
                msgBuilder.toolCall(tc);
                responseBuilder.toolCall(tc);
            }
        }

        if (oaiResp.getUsage() != null) {
            responseBuilder.usage(UsageInfo.builder()
                    .inputTokens(oaiResp.getUsage().getPromptTokens())
                    .outputTokens(oaiResp.getUsage().getCompletionTokens())
                    .build());
        }

        return responseBuilder.assistantMessage(msgBuilder.build()).build();
    }

    // ---- OpenAI response DTOs ----

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class OAIResponse {
        private String id;
        private List<Choice> choices;
        private Usage usage;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        private OAIMessage message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class OAIMessage {
        private String role;
        private String content;
        @JsonProperty("tool_calls")
        private List<OAIToolCall> toolCalls;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class OAIToolCall {
        private String id;
        private String type;
        private OAIFunction function;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class OAIFunction {
        private String name;
        private String arguments;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("completion_tokens")
        private int completionTokens;
    }
}

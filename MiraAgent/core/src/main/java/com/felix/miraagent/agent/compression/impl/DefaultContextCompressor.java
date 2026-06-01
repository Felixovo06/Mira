package com.felix.miraagent.agent.compression.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.agent.compression.*;
import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryScope;
import com.felix.miraagent.memory.MemoryStore;
import com.felix.miraagent.memory.MemoryWriteRequest;
import com.felix.miraagent.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

public class DefaultContextCompressor implements ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextCompressor.class);

    private final String summaryBaseDir;
    private final ObjectMapper objectMapper;

    public DefaultContextCompressor(String summaryBaseDir, ObjectMapper objectMapper) {
        this.summaryBaseDir = summaryBaseDir;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean shouldCompress(int lastRealInputTokens, CompressionPolicy policy) {
        return ContextPressureMeter.measure(lastRealInputTokens, policy.getMaxContextTokens()) >= policy.getHighWatermark();
    }

    @Override
    public CompressResult compress(
            List<Message> conversationHistory,
            String sessionId,
            String userId,
            String characterId,
            CompressionPolicy policy,
            ModelClient modelClient,
            MemoryStore memoryStore) {

        int historySize = conversationHistory.size();
        int protectedFirst = Math.min(policy.getProtectFirstMessages(), historySize);
        int protectedTailCount = policy.getProtectRecentRounds() * 2;

        int midStart = protectedFirst;
        int midEnd = historySize - protectedTailCount;

        if (midEnd <= midStart) {
            return CompressResult.builder().compressed(false).build();
        }

        // Adjust start boundary: don't begin mid with a TOOL message (would orphan a tool result)
        while (midStart < midEnd && conversationHistory.get(midStart).getRole() == MessageRole.TOOL) {
            midStart++;
        }

        // Adjust end boundary: don't end mid with an ASSISTANT that has tool calls (would orphan tool results)
        while (midEnd > midStart) {
            Message lastMid = conversationHistory.get(midEnd - 1);
            if (lastMid.getRole() == MessageRole.ASSISTANT
                    && lastMid.getToolCalls() != null
                    && !lastMid.getToolCalls().isEmpty()) {
                midEnd--;
            } else {
                break;
            }
        }

        if (midEnd <= midStart) {
            return CompressResult.builder().compressed(false).build();
        }

        List<Message> midMessages = conversationHistory.subList(midStart, midEnd);
        String firstRemovedId = midMessages.get(0).getId();
        String lastRemovedId = midMessages.get(midMessages.size() - 1).getId();

        String prompt = buildCompressionPrompt(midMessages);

        ChatRequest chatRequest = ChatRequest.builder()
                .message(Message.builder()
                        .id(UUID.randomUUID().toString())
                        .role(MessageRole.USER)
                        .content(prompt)
                        .build())
                .temperature(0.2)
                .maxTokens(2048)
                .stream(false)
                .build();

        ChatResponse response;
        try {
            response = modelClient.chat(chatRequest);
        } catch (Exception e) {
            log.warn("Compression LLM call failed, skipping compression. sessionId={}", sessionId, e);
            return CompressResult.builder().compressed(false).build();
        }

        String rawContent = response.getAssistantMessage() != null
                ? response.getAssistantMessage().getContent()
                : "";

        String summaryText;
        List<MemoryWriteRequest> memoryWrites = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(rawContent);
            summaryText = root.path("summary").asText(rawContent);

            JsonNode writesNode = root.path("memory_writes");
            if (writesNode.isArray()) {
                for (JsonNode writeNode : writesNode) {
                    String category = writeNode.path("category").asText("");
                    String content = writeNode.path("content").asText("");
                    if (!category.isEmpty() && !content.isEmpty()) {
                        try {
                            MemoryCategory mc = MemoryCategory.valueOf(category);
                            memoryWrites.add(MemoryWriteRequest.builder()
                                    .memoryId(UUID.randomUUID().toString())
                                    .userId(userId)
                                    .characterId(characterId)
                                    .scope(MemoryScope.GLOBAL)
                                    .category(mc)
                                    .content(content)
                                    .build());
                        } catch (IllegalArgumentException ex) {
                            log.warn("Unknown memory category '{}', skipping write", category);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse compression JSON response, using raw text as summary. sessionId={}", sessionId, e);
            summaryText = rawContent;
        }

        if (memoryStore != null) {
            for (MemoryWriteRequest req : memoryWrites) {
                try {
                    memoryStore.write(req);
                } catch (Exception e) {
                    log.warn("Failed to write memory during compression. sessionId={}", sessionId, e);
                }
            }
        }

        String checkpointId = UUID.randomUUID().toString();
        writeSummaryFile(sessionId, checkpointId, summaryText);

        Message summaryMessage = Message.builder()
                .id(UUID.randomUUID().toString())
                .role(MessageRole.USER)
                .content("[Context Summary]\n" + summaryText)
                .build();

        List<Message> protectedFirstList = new ArrayList<>(conversationHistory.subList(0, midStart));
        List<Message> protectedTailList = new ArrayList<>(conversationHistory.subList(midEnd, historySize));

        conversationHistory.clear();
        conversationHistory.addAll(protectedFirstList);
        conversationHistory.add(summaryMessage);
        conversationHistory.addAll(protectedTailList);

        CompressionSummary summary = CompressionSummary.builder()
                .checkpointId(checkpointId)
                .sessionId(sessionId)
                .summaryText(summaryText)
                .memoryWrites(memoryWrites)
                .firstRemovedMessageId(firstRemovedId)
                .lastRemovedMessageId(lastRemovedId)
                .createdAt(Instant.now())
                .build();

        return CompressResult.builder().compressed(true).summary(summary).build();
    }

    private String buildCompressionPrompt(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是一段对话历史，请提炼摘要。输出格式必须是合法 JSON，无任何多余文字：\n");
        sb.append("{\n");
        sb.append("  \"memory_writes\": [\n");
        sb.append("    {\"category\": \"EVENT|GOAL|PREFERENCE|RELATIONSHIP|PROFILE|SUMMARY\", \"content\": \"...\"}\n");
        sb.append("  ],\n");
        sb.append("  \"summary\": \"对话摘要，包含：当前目标、关键决定、未决项、重要上下文\"\n");
        sb.append("}\n");
        sb.append("只把可跨会话的持久事实放入 memory_writes，不要把用户隐私放入 summary 的持久部分。\n");
        sb.append("对话历史：\n");
        for (Message msg : messages) {
            String roleLabel = switch (msg.getRole()) {
                case USER -> "[USER]";
                case ASSISTANT -> "[ASSISTANT]";
                case TOOL -> "[TOOL]";
                case SYSTEM -> "[SYSTEM]";
            };
            String content = msg.getContent() != null ? msg.getContent() : "";
            sb.append(roleLabel).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    private void writeSummaryFile(String sessionId, String checkpointId, String summaryText) {
        try {
            Path dir = Paths.get(summaryBaseDir, sessionId);
            Files.createDirectories(dir);
            Path file = dir.resolve(checkpointId + ".md");
            Files.writeString(file, summaryText);
        } catch (IOException e) {
            log.warn("Failed to write summary file. sessionId={}, checkpointId={}", sessionId, checkpointId, e);
        }
    }
}

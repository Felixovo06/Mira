package com.felix.miraagent.agent.impl;

import com.felix.miraagent.agent.*;
import com.felix.miraagent.agent.compression.*;
import com.felix.miraagent.experience.BackgroundReview;
import com.felix.miraagent.experience.ReviewContext;
import com.felix.miraagent.experience.ReviewResult;
import com.felix.miraagent.experience.ReviewSignals;
import com.felix.miraagent.memory.MemoryRetriever;
import com.felix.miraagent.memory.MemoryStore;
import com.felix.miraagent.memory.SerializedMemoryWriter;
import com.felix.miraagent.model.*;
import com.felix.miraagent.prompt.PromptBuildRequest;
import com.felix.miraagent.prompt.PromptBuildResult;
import com.felix.miraagent.prompt.PromptBuilder;
import com.felix.miraagent.session.SessionStore;
import com.felix.miraagent.skill.SkillIndexInjector;
import com.felix.miraagent.tools.ToolDispatchContext;
import com.felix.miraagent.tools.ToolDispatcher;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolExecutionStore;
import com.felix.miraagent.tools.ToolRegistry;
import com.felix.miraagent.tools.ToolResolveContext;
import com.felix.miraagent.tools.artifact.ToolResultArtifact;
import com.felix.miraagent.tools.artifact.ToolResultBudget;
import com.felix.miraagent.tools.artifact.ToolResultCache;
import com.felix.miraagent.trace.TraceEvent;
import com.felix.miraagent.trace.TraceEventType;
import com.felix.miraagent.trace.TraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConversationLoop {

    private static final Logger log = LoggerFactory.getLogger(ConversationLoop.class);
    private static final Pattern CONTEXT_SUMMARY_PATTERN =
            Pattern.compile("^\\[Context Summary checkpoint=([^\\s]+) first=([^\\s]+) last=([^\\]]+)]", Pattern.MULTILINE);

    private final ModelClient modelClient;
    private final PromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final SessionStore sessionStore;
    private final TraceStore traceStore;
    private final ToolExecutionStore toolExecutionStore;
    private final MemoryStore memoryStore;
    private final MemoryRetriever memoryRetriever;
    private final SerializedMemoryWriter memoryWriter;
    private final ToolResultCache toolResultCache;
    private final ContextCompressor compressor;
    private final CompressionPolicy compressionPolicy;
    private final String summaryBaseDir;
    private final SkillIndexInjector skillIndexInjector;
    private final BackgroundReview backgroundReview;

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, null, null);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, memoryStore, memoryRetriever, null);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever,
                            ToolResultCache toolResultCache) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, memoryStore, memoryRetriever, null, toolResultCache, null, null);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever,
                            ToolResultCache toolResultCache,
                            ContextCompressor compressor, CompressionPolicy compressionPolicy) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, memoryStore, memoryRetriever, null, toolResultCache, compressor, compressionPolicy);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever,
                            SerializedMemoryWriter memoryWriter,
                            ToolResultCache toolResultCache,
                            ContextCompressor compressor, CompressionPolicy compressionPolicy) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, memoryStore, memoryRetriever, memoryWriter, toolResultCache, compressor, compressionPolicy, null);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever,
                            SerializedMemoryWriter memoryWriter,
                            ToolResultCache toolResultCache,
                            ContextCompressor compressor, CompressionPolicy compressionPolicy,
                            String summaryBaseDir) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, memoryStore, memoryRetriever, memoryWriter, toolResultCache,
                compressor, compressionPolicy, summaryBaseDir, null);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever,
                            SerializedMemoryWriter memoryWriter,
                            ToolResultCache toolResultCache,
                            ContextCompressor compressor, CompressionPolicy compressionPolicy,
                            String summaryBaseDir, SkillIndexInjector skillIndexInjector) {
        this(modelClient, promptBuilder, toolRegistry, toolDispatcher, sessionStore, traceStore,
                toolExecutionStore, memoryStore, memoryRetriever, memoryWriter, toolResultCache,
                compressor, compressionPolicy, summaryBaseDir, skillIndexInjector, null);
    }

    public ConversationLoop(ModelClient modelClient, PromptBuilder promptBuilder,
                            ToolRegistry toolRegistry, ToolDispatcher toolDispatcher,
                            SessionStore sessionStore, TraceStore traceStore,
                            ToolExecutionStore toolExecutionStore,
                            MemoryStore memoryStore, MemoryRetriever memoryRetriever,
                            SerializedMemoryWriter memoryWriter,
                            ToolResultCache toolResultCache,
                            ContextCompressor compressor, CompressionPolicy compressionPolicy,
                            String summaryBaseDir, SkillIndexInjector skillIndexInjector,
                            BackgroundReview backgroundReview) {
        this.modelClient = modelClient;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.toolDispatcher = toolDispatcher;
        this.sessionStore = sessionStore;
        this.traceStore = traceStore;
        this.toolExecutionStore = toolExecutionStore;
        this.memoryStore = memoryStore;
        this.memoryRetriever = memoryRetriever;
        this.memoryWriter = memoryWriter;
        this.toolResultCache = toolResultCache;
        this.compressor = compressor;
        this.compressionPolicy = compressionPolicy;
        this.summaryBaseDir = summaryBaseDir;
        this.skillIndexInjector = skillIndexInjector;
        this.backgroundReview = backgroundReview;
    }

    public RunResult run(AgentRunRequest request) {
        String runId = request.getRunId();
        String sessionId = request.getSessionId();
        IterationBudget budget = request.getIterationBudget();

        emitTrace(request, runId, sessionId, 0, TraceEventType.RUN_STARTED, Map.of("userId", request.getUserId()));

        List<Message> conversationHistory = new ArrayList<>(request.getMessages());
        conversationHistory = applyCompressionSummaries(conversationHistory);
        List<ToolExecutionResult> allToolResults = new ArrayList<>();
        int modelCallCount = 0;
        int toolCallCount = 0;
        int stepIndex = 1;
        int lastRealInputTokens = 0; // 由模型返回的真实输入 token 数，0 表示尚无数据

        while (true) {
            if (request.getInterruptSignal().isInterrupted()) {
                emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.RUN_FAILED, Map.of("reason", "interrupted"));
                streamDone(request, "interrupted");
                return RunResult.builder().runId(runId).sessionId(sessionId)
                        .status(RunStatus.INTERRUPTED).toolExecutions(allToolResults).build();
            }

            if (modelCallCount >= budget.getMaxModelCalls()) {
                emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.RUN_FAILED, Map.of("reason", "budget_exceeded_model_calls"));
                streamDone(request, "budget_exceeded");
                return RunResult.builder().runId(runId).sessionId(sessionId)
                        .status(RunStatus.BUDGET_EXCEEDED).error("Max model calls exceeded: " + budget.getMaxModelCalls())
                        .toolExecutions(allToolResults).build();
            }

            var resolveCtx = ToolResolveContext.builder()
                    .userId(request.getUserId())
                    .sessionId(sessionId)
                    .enabledToolNames(request.getToolConfig() != null
                            ? request.getToolConfig().getEnabledToolNames() : Set.of())
                    .build();

            String userProfileSummary = "";
            String relationshipMemory = "";
            String latestSummary = readLatestSummary(sessionId);
            if (memoryStore != null) {
                userProfileSummary = truncate(memoryStore.readFile(request.getUserId(), "USER.md"), 500);
                String charId = request.getCharacterProfile() != null ? request.getCharacterProfile().getId() : null;
                if (charId != null) {
                    relationshipMemory = truncate(memoryStore.readFile(request.getUserId(), "characters/" + charId + "/RELATIONSHIP.md"), 500);
                }
            }

            var promptRequestBuilder = PromptBuildRequest.builder()
                    .characterProfile(request.getCharacterProfile())
                    .userProfileSummary(userProfileSummary)
                    .relationshipMemory(relationshipMemory)
                    .sessionHistory(conversationHistory)
                    .toolDefinitions(toolRegistry.listAvailable(resolveCtx))
                    .contextBudget(lastRealInputTokens);
            if (!latestSummary.isBlank()) {
                promptRequestBuilder.retrievedMemory("[Latest Context Summary]\n" + truncate(latestSummary, 1000));
            }
            // 渐进式披露：只把 Active skill 索引注入稳定 prompt，完整 SKILL.md 按需加载
            if (skillIndexInjector != null) {
                String skillIndex = skillIndexInjector.renderIndex();
                if (skillIndex != null && !skillIndex.isBlank()) {
                    promptRequestBuilder.skillIndex(skillIndex);
                }
            }
            PromptBuildRequest promptRequest = promptRequestBuilder.build();

            PromptBuildResult promptResult = promptBuilder.build(promptRequest);
            emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.PROMPT_BUILT,
                    Map.of("tokenEstimate", promptResult.getTokenEstimate()));

            var chatRequest = ChatRequest.builder()
                    .messages(promptResult.getMessages())
                    .tools(toolRegistry.listAvailable(resolveCtx))
                    .temperature(request.getModelConfig() != null ? request.getModelConfig().getTemperature() : 0.7)
                    .maxTokens(request.getModelConfig() != null ? request.getModelConfig().getMaxTokens() : 2048)
                    .stream(request.getStreamCallback() != null)
                    .build();

            emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.MODEL_REQUESTED,
                    Map.of("modelCallCount", modelCallCount));

            ChatResponse response;
            try {
                if (request.getStreamCallback() != null) {
                    response = streamModel(request, chatRequest);
                } else {
                    response = modelClient.chat(chatRequest);
                }
                modelCallCount++;
            } catch (RunInterruptedException e) {
                emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.RUN_FAILED, Map.of("reason", "interrupted"));
                streamDone(request, "interrupted");
                return RunResult.builder().runId(runId).sessionId(sessionId)
                        .status(RunStatus.INTERRUPTED).toolExecutions(allToolResults).build();
            } catch (Exception e) {
                log.error("Model call failed runId={}", runId, e);
                streamError(request, e.getMessage());
                emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.RUN_FAILED, Map.of("error", e.getMessage()));
                return RunResult.builder().runId(runId).sessionId(sessionId)
                        .status(RunStatus.FAILED).error(e.getMessage()).toolExecutions(allToolResults).build();
            }

            if (response.getUsage() != null && response.getUsage().getInputTokens() > 0) {
                lastRealInputTokens = response.getUsage().getInputTokens();
            }
            emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.MODEL_RESPONDED,
                    Map.of("finishReason", String.valueOf(response.getFinishReason()),
                            "inputTokens", lastRealInputTokens,
                            "outputTokens", response.getUsage() != null ? response.getUsage().getOutputTokens() : 0));

            if (response.hasError()) {
                streamError(request, response.getError().getMessage());
                emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.RUN_FAILED,
                        Map.of("error", response.getError().getMessage()));
                return RunResult.builder().runId(runId).sessionId(sessionId)
                        .status(RunStatus.FAILED).error(response.getError().getMessage())
                        .toolExecutions(allToolResults).build();
            }

            if (response.hasToolCalls()) {
                emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.TOOL_CALL_RECEIVED,
                        Map.of("count", response.getToolCalls().size()));
                emitToolCalls(request, response.getToolCalls());

                Message assistantMsg = response.getAssistantMessage() != null
                        ? response.getAssistantMessage()
                        : Message.builder()
                                .id(UUID.randomUUID().toString())
                                .role(MessageRole.ASSISTANT)
                                .toolCalls(response.getToolCalls())
                                .build();

                sessionStore.appendMessage(sessionId, assistantMsg);
                conversationHistory.add(assistantMsg);

                if (toolCallCount + response.getToolCalls().size() > budget.getMaxToolCalls()) {
                    emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.RUN_FAILED,
                            Map.of("reason", "budget_exceeded_tool_calls"));
                    streamDone(request, "budget_exceeded");
                    return RunResult.builder().runId(runId).sessionId(sessionId)
                            .status(RunStatus.BUDGET_EXCEEDED).error("Max tool calls exceeded")
                            .toolExecutions(allToolResults).build();
                }

                var dispatchCtx = ToolDispatchContext.builder()
                        .runId(runId).sessionId(sessionId).userId(request.getUserId())
                        .permissionPolicy(request.getPermissionPolicy())
                        .build();

                for (ToolCall tc : response.getToolCalls()) {
                    emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.TOOL_EXECUTION_STARTED,
                            Map.of("tool", tc.getName(), "callId", tc.getId()));
                }

                List<ToolExecutionResult> results = toolDispatcher.dispatchAll(response.getToolCalls(), dispatchCtx);
                toolCallCount += results.size();
                allToolResults.addAll(results);

                for (int i = 0; i < results.size(); i++) {
                    ToolExecutionResult result = results.get(i);
                    ToolCall call = response.getToolCalls().get(i);
                    toolExecutionStore.record(runId, sessionId, call, result);
                    if (result.getStatus() == com.felix.miraagent.tools.ToolStatus.DENIED) {
                        emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.PERMISSION_DENIED,
                                Map.of("tool", result.getToolName(), "callId", result.getToolCallId()));
                    }
                    emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.TOOL_EXECUTION_FINISHED,
                            Map.of("tool", result.getToolName(), "status", result.getStatus().name()));
                    emitToolResult(request, result);

                    String modelVisibleContent = result.getModelVisibleContent();
                    if (toolResultCache != null && ToolResultBudget.shouldExternalize(modelVisibleContent)) {
                        ToolResultArtifact artifact = ToolResultArtifact.builder()
                                .artifactId(UUID.randomUUID().toString())
                                .toolCallId(result.getToolCallId())
                                .toolName(result.getToolName())
                                .content(modelVisibleContent)
                                .contentType("text/plain")
                                .sizeBytes(modelVisibleContent != null ? modelVisibleContent.getBytes().length : 0)
                                .createdAt(Instant.now())
                                .build();
                        Optional<String> uri = toolResultCache.store(artifact);
                        if (uri.isPresent()) {
                            String preview = modelVisibleContent != null && modelVisibleContent.length() > 200
                                    ? modelVisibleContent.substring(0, 200) + "..."
                                    : modelVisibleContent;
                            modelVisibleContent = "[artifact: " + uri.get() + "]\n" + preview;
                        }
                    }

                    Message toolMsg = Message.builder()
                            .id(UUID.randomUUID().toString())
                            .role(MessageRole.TOOL)
                            .toolCallId(result.getToolCallId())
                            .toolName(result.getToolName())
                            .content(modelVisibleContent)
                            .build();
                    sessionStore.appendMessage(sessionId, toolMsg);
                    conversationHistory.add(toolMsg);
                }

                continue;
            }

            Message finalMsg = response.getAssistantMessage();
            if (finalMsg == null) {
                finalMsg = Message.builder()
                        .id(UUID.randomUUID().toString())
                        .role(MessageRole.ASSISTANT)
                        .content("")
                        .build();
            }

            if (compressor != null && compressor.shouldCompress(lastRealInputTokens, compressionPolicy)) {
                CompressResult cr = compressor.compress(
                        conversationHistory, sessionId, request.getUserId(),
                        request.getCharacterProfile() != null ? request.getCharacterProfile().getId() : null,
                        compressionPolicy, modelClient, memoryWriter
                );
                if (cr.isCompressed()) {
                    Message summaryMsg = findSummaryMessage(conversationHistory, cr.getSummary().getCheckpointId());
                    if (summaryMsg != null) {
                        sessionStore.appendMessage(sessionId, summaryMsg);
                    }
                    emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.CONTEXT_COMPRESSED,
                            Map.of("checkpointId", cr.getSummary().getCheckpointId(),
                                    "memoryWrites", cr.getSummary().getMemoryWrites().size()));
                }
            }

            sessionStore.appendMessage(sessionId, finalMsg);
            sessionStore.updateLastMessageAt(sessionId);

            emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.FINAL_RESPONSE,
                    Map.of("contentLength", finalMsg.getContent() != null ? finalMsg.getContent().length() : 0));
            emitTrace(request, runId, sessionId, stepIndex++, TraceEventType.SESSION_PERSISTED, Map.of());
            streamDone(request, response.getFinishReason());

            // 主回复已返回，异步触发 Background Review（门控不满足则不开线程），不阻塞用户
            triggerBackgroundReview(request, runId, sessionId, conversationHistory, toolCallCount);

            return RunResult.builder()
                    .runId(runId).sessionId(sessionId)
                    .status(RunStatus.SUCCESS)
                    .finalMessage(finalMsg)
                    .toolExecutions(allToolResults)
                    .build();
        }
    }

    private void triggerBackgroundReview(AgentRunRequest request, String runId, String sessionId,
                                         List<Message> conversationHistory, int toolCallCount) {
        if (backgroundReview == null) {
            return;
        }
        try {
            int userTurns = 0;
            String latestUserText = "";
            for (Message m : conversationHistory) {
                if (m.getRole() == MessageRole.USER && parseSummaryMarker(m) == null) {
                    userTurns++;
                    if (m.getContent() != null) {
                        latestUserText = m.getContent();
                    }
                }
            }
            ReviewSignals signals = ReviewSignals.builder()
                    .toolCallCount(toolCallCount)
                    .turnCount(userTurns)
                    .userMessageText(latestUserText)
                    .build();
            ReviewContext ctx = ReviewContext.builder()
                    .userId(request.getUserId())
                    .characterId(request.getCharacterProfile() != null ? request.getCharacterProfile().getId() : null)
                    .sessionId(sessionId)
                    .sourceTraceId(runId)
                    .transcript(renderTranscript(conversationHistory))
                    .signals(signals)
                    .build();
            backgroundReview.reviewAsync(ctx, result -> recordReviewTrace(runId, sessionId, result));
        } catch (Exception e) {
            log.warn("Failed to trigger background review runId={}", runId, e);
        }
    }

    private void recordReviewTrace(String runId, String sessionId, ReviewResult result) {
        if (result == null || !result.isTriggered()) {
            return;
        }
        // 后台线程只落 trace，不推流（主回复流已结束）
        traceStore.record(TraceEvent.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId).sessionId(sessionId)
                .stepIndex(0)
                .eventType(TraceEventType.BACKGROUND_REVIEW_FINISHED)
                .payload(Map.of(
                        "review_triggered_by", result.getTriggeredBy() != null ? result.getTriggeredBy() : "",
                        "memoryWrites", result.getMemoriesWritten(),
                        "skillWrites", result.getSkillsWritten()))
                .build());
    }

    private String renderTranscript(List<Message> history) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : history) {
            String role = switch (msg.getRole()) {
                case USER -> "[USER]";
                case ASSISTANT -> "[ASSISTANT]";
                case TOOL -> "[TOOL]";
                case SYSTEM -> "[SYSTEM]";
            };
            String content = msg.getContent() != null ? msg.getContent() : "";
            sb.append(role).append(": ").append(content).append("\n");
        }
        String text = sb.toString();
        return text.length() > 8000 ? text.substring(text.length() - 8000) : text;
    }

    private void emitTrace(AgentRunRequest request, String runId, String sessionId, int stepIndex,
                           TraceEventType type, Map<String, Object> payload) {
        TraceEvent event = TraceEvent.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId).sessionId(sessionId)
                .stepIndex(stepIndex).eventType(type)
                .payload(payload)
                .build();
        traceStore.record(event);
        if (request.getStreamCallback() != null) {
            request.getStreamCallback().onDelta(StreamDelta.builder().traceEvent(event).build());
        }
    }

    private void emitToolCalls(AgentRunRequest request, List<ToolCall> toolCalls) {
        if (request.getStreamCallback() == null) {
            return;
        }
        for (int i = 0; i < toolCalls.size(); i++) {
            request.getStreamCallback().onDelta(StreamDelta.builder()
                    .toolCallDelta(toolCalls.get(i))
                    .toolCallIndex(i)
                    .build());
        }
    }

    private void emitToolResult(AgentRunRequest request, ToolExecutionResult result) {
        if (request.getStreamCallback() != null) {
            request.getStreamCallback().onDelta(StreamDelta.builder()
                    .toolExecutionResult(result)
                    .build());
        }
    }

    private void streamError(AgentRunRequest request, String message) {
        if (request.getStreamCallback() != null) {
            request.getStreamCallback().onDelta(StreamDelta.builder()
                    .error(message)
                    .done(true)
                    .finishReason("error")
                    .build());
        }
    }

    private void streamDone(AgentRunRequest request, String finishReason) {
        if (request.getStreamCallback() != null) {
            request.getStreamCallback().onDelta(StreamDelta.builder()
                    .done(true)
                    .finishReason(finishReason != null ? finishReason : "stop")
                    .build());
        }
    }

    private ChatResponse streamModel(AgentRunRequest request, ChatRequest chatRequest) {
        StreamCallback callback = delta -> {
            if (request.getInterruptSignal().isInterrupted()) {
                throw new RunInterruptedException();
            }
            request.getStreamCallback().onDelta(delta);
        };
        var handle = modelClient.streamChat(chatRequest, callback);
        while (!handle.isComplete()) {
            if (request.getInterruptSignal().isInterrupted()) {
                handle.abort();
                throw new RunInterruptedException();
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handle.abort();
                throw new RunInterruptedException();
            }
        }
        return handle.await();
    }

    private List<Message> applyCompressionSummaries(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message summary = history.get(i);
            SummaryMarker marker = parseSummaryMarker(summary);
            if (marker == null) {
                continue;
            }
            int firstIdx = indexOfMessage(history, marker.firstMessageId());
            int lastIdx = indexOfMessage(history, marker.lastMessageId());
            if (firstIdx < 0 || lastIdx < firstIdx || lastIdx >= i) {
                continue;
            }
            List<Message> collapsed = new ArrayList<>();
            collapsed.addAll(history.subList(0, firstIdx));
            collapsed.add(summary);
            for (int j = lastIdx + 1; j < history.size(); j++) {
                if (j != i) {
                    collapsed.add(history.get(j));
                }
            }
            return collapsed;
        }
        return history;
    }

    private Message findSummaryMessage(List<Message> history, String checkpointId) {
        for (Message message : history) {
            SummaryMarker marker = parseSummaryMarker(message);
            if (marker != null && marker.checkpointId().equals(checkpointId)) {
                return message;
            }
        }
        return null;
    }

    private String readLatestSummary(String sessionId) {
        if (summaryBaseDir == null || summaryBaseDir.isBlank() || sessionId == null || sessionId.isBlank()) {
            return "";
        }
        Path dir = Paths.get(summaryBaseDir, sessionId);
        if (!Files.isDirectory(dir)) {
            return "";
        }
        try (var stream = Files.list(dir)) {
            Optional<Path> latest = stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }));
            return latest.isPresent() ? Files.readString(latest.get(), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            log.warn("Failed to read latest summary for session {}", sessionId, e);
            return "";
        }
    }

    private SummaryMarker parseSummaryMarker(Message message) {
        if (message == null || message.getContent() == null) {
            return null;
        }
        Matcher matcher = CONTEXT_SUMMARY_PATTERN.matcher(message.getContent());
        if (!matcher.find()) {
            return null;
        }
        return new SummaryMarker(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private int indexOfMessage(List<Message> history, String messageId) {
        for (int i = 0; i < history.size(); i++) {
            if (Objects.equals(history.get(i).getId(), messageId)) {
                return i;
            }
        }
        return -1;
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text != null ? text : "";
        return text.substring(0, maxChars);
    }

    private record SummaryMarker(String checkpointId, String firstMessageId, String lastMessageId) {}

    private static class RunInterruptedException extends RuntimeException {
    }
}

package com.felix.miraagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.agent.compression.CompressionPolicy;
import com.felix.miraagent.agent.compression.impl.DefaultContextCompressor;
import com.felix.miraagent.agent.impl.ConversationLoop;
import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.fake.FakeModelClient;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.StreamDelta;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.prompt.PromptBuildRequest;
import com.felix.miraagent.prompt.PromptBuildResult;
import com.felix.miraagent.prompt.PromptBuilder;
import com.felix.miraagent.prompt.impl.DefaultPromptBuilder;
import com.felix.miraagent.session.Session;
import com.felix.miraagent.session.impl.InMemorySessionStore;
import com.felix.miraagent.tools.ToolStatus;
import com.felix.miraagent.tools.builtin.BuiltinTools;
import com.felix.miraagent.tools.impl.DefaultToolDispatcher;
import com.felix.miraagent.tools.impl.DefaultToolPermissionPolicy;
import com.felix.miraagent.tools.impl.InMemoryToolExecutionStore;
import com.felix.miraagent.tools.impl.InMemoryToolRegistry;
import com.felix.miraagent.trace.TraceEventType;
import com.felix.miraagent.trace.impl.InMemoryTraceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ConversationLoopTest {

    private FakeModelClient fakeModel;
    private InMemorySessionStore sessionStore;
    private InMemoryTraceStore traceStore;
    private InMemoryToolExecutionStore toolExecutionStore;
    private InMemoryToolRegistry toolRegistry;
    private ConversationLoop loop;

    @BeforeEach
    void setUp() {
        fakeModel = new FakeModelClient();
        sessionStore = new InMemorySessionStore();
        traceStore = new InMemoryTraceStore();
        toolExecutionStore = new InMemoryToolExecutionStore();
        toolRegistry = new InMemoryToolRegistry();
        BuiltinTools.registerAll(toolRegistry);

        loop = new ConversationLoop(
                fakeModel,
                new DefaultPromptBuilder(),
                toolRegistry,
                new DefaultToolDispatcher(toolRegistry),
                sessionStore,
                traceStore,
                toolExecutionStore
        );
    }

    private AgentRunRequest buildRequest(String sessionId, List<Message> messages) {
        return AgentRunRequest.builder()
                .runId(UUID.randomUUID().toString())
                .userId("u1")
                .sessionId(sessionId)
                .characterProfile(CharacterProfile.defaultProfile())
                .messages(messages)
                .modelConfig(ModelConfig.builder().modelName("fake").build())
                .iterationBudget(IterationBudget.defaultBudget())
                .permissionPolicy(new DefaultToolPermissionPolicy())
                .build();
    }

    private Message userMessage(String content) {
        return Message.builder().id(UUID.randomUUID().toString())
                .role(MessageRole.USER).content(content).build();
    }

    @Test
    void shouldReturnFinalResponseWhenModelHasNoToolCall() {
        fakeModel.thenReply("Hello! How can I help you?");
        var request = buildRequest("s1", List.of(userMessage("Hi")));
        var result = loop.run(request);

        assertEquals(RunStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getFinalMessage());
        assertEquals("Hello! How can I help you?", result.getFinalMessage().getContent());
        assertTrue(result.getToolExecutions().isEmpty());
    }

    @Test
    void shouldAggregateUsageIntoRunResult() {
        // 回归:成功 RunResult 必须带上 token usage(评测发现此前为 null)
        fakeModel.thenReplyWithUsage("Hi there", 123);
        var result = loop.run(buildRequest("s-usage", List.of(userMessage("Hi"))));

        assertEquals(RunStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getUsage(), "RunResult 应携带 usage");
        assertEquals(123, result.getUsage().getInputTokens());
    }

    @Test
    void shouldDispatchToolAndContinueLoop() {
        fakeModel.thenCallTool("tc1", "note", "{\"content\":\"important note\"}")
                 .thenReply("I saved that note for you.");

        var request = buildRequest("s2", List.of(userMessage("Save a note")));
        var result = loop.run(request);

        assertEquals(RunStatus.SUCCESS, result.getStatus());
        assertEquals(1, result.getToolExecutions().size());
        assertEquals(ToolStatus.SUCCESS, result.getToolExecutions().get(0).getStatus());
        assertEquals("I saved that note for you.", result.getFinalMessage().getContent());
        assertEquals(1, toolExecutionStore.findByRunId(request.getRunId()).size());
    }

    @Test
    void shouldPreserveToolResultOrderWhenExecutingMultipleTools() {
        var tc1 = ToolCall.builder().id("tc1").name("note").arguments("{\"content\":\"first\"}").build();
        var tc2 = ToolCall.builder().id("tc2").name("todo").arguments("{\"task\":\"second\"}").build();
        var tc3 = ToolCall.builder().id("tc3").name("note").arguments("{\"content\":\"third\"}").build();

        fakeModel.thenCallTools(tc1, tc2, tc3)
                 .thenReply("Done! All three actions completed.");

        var request = buildRequest("s3", List.of(userMessage("Do three things")));
        var result = loop.run(request);

        assertEquals(RunStatus.SUCCESS, result.getStatus());
        assertEquals(3, result.getToolExecutions().size());
        assertEquals("tc1", result.getToolExecutions().get(0).getToolCallId());
        assertEquals("tc2", result.getToolExecutions().get(1).getToolCallId());
        assertEquals("tc3", result.getToolExecutions().get(2).getToolCallId());
    }

    @Test
    void shouldReturnErrorResultWhenToolFails() {
        fakeModel.thenCallTool("tc1", "note", "{}")
                 .thenReply("I see the note tool had an issue.");

        var request = buildRequest("s4", List.of(userMessage("Save empty note")));
        var result = loop.run(request);

        assertEquals(RunStatus.SUCCESS, result.getStatus());
        assertEquals(1, result.getToolExecutions().size());
        assertEquals(ToolStatus.ERROR, result.getToolExecutions().get(0).getStatus());
        assertEquals("I see the note tool had an issue.", result.getFinalMessage().getContent());
    }

    @Test
    void shouldReturnBudgetExceededWhenMaxModelCallsReached() {
        var tinyBudget = IterationBudget.builder().maxModelCalls(2).maxToolCalls(10).build();

        fakeModel.thenCallTool("tc1", "note", "{\"content\":\"a\"}")
                 .thenCallTool("tc2", "note", "{\"content\":\"b\"}")
                 .thenReply("done");

        var request = AgentRunRequest.builder()
                .runId(UUID.randomUUID().toString())
                .userId("u1").sessionId("s5")
                .characterProfile(CharacterProfile.defaultProfile())
                .messages(List.of(userMessage("loop")))
                .modelConfig(ModelConfig.builder().modelName("fake").build())
                .iterationBudget(tinyBudget)
                .permissionPolicy(new DefaultToolPermissionPolicy())
                .build();

        var result = loop.run(request);
        assertEquals(RunStatus.BUDGET_EXCEEDED, result.getStatus());
    }

    @Test
    void shouldRecordTraceEvents() {
        fakeModel.thenReply("Hello!");
        var request = buildRequest("s6", List.of(userMessage("Hi")));
        loop.run(request);

        var events = traceStore.findByRunId(request.getRunId());
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == TraceEventType.RUN_STARTED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == TraceEventType.FINAL_RESPONSE));
    }

    @Test
    void shouldPersistMessagesToSessionStore() {
        fakeModel.thenReply("Got it!");
        var session = "s7";
        sessionStore.createSession(com.felix.miraagent.session.Session.builder()
                .id(session).userId("u1").build());

        var request = buildRequest(session, List.of(userMessage("Hello")));
        loop.run(request);

        var messages = sessionStore.loadMessages(session);
        assertTrue(messages.stream().anyMatch(m -> m.getRole() == MessageRole.ASSISTANT));
    }

    @Test
    void shouldEmitStreamingDeltasTraceAndToolResults() {
        fakeModel.thenCallTool("tc1", "note", "{\"content\":\"stream note\"}")
                .thenReply("Done streaming.");

        var deltas = new CopyOnWriteArrayList<StreamDelta>();
        var request = AgentRunRequest.builder()
                .runId(UUID.randomUUID().toString())
                .userId("u1")
                .sessionId("s8")
                .characterProfile(CharacterProfile.defaultProfile())
                .message(userMessage("stream please"))
                .modelConfig(ModelConfig.builder().modelName("fake").build())
                .iterationBudget(IterationBudget.defaultBudget())
                .permissionPolicy(new DefaultToolPermissionPolicy())
                .streamCallback(deltas::add)
                .build();

        var result = loop.run(request);

        assertEquals(RunStatus.SUCCESS, result.getStatus());
        assertTrue(deltas.stream().anyMatch(d -> d.getTextDelta() != null && d.getTextDelta().contains("Done streaming.")));
        assertTrue(deltas.stream().anyMatch(d -> d.getToolCallDelta() != null && d.getToolCallDelta().getName().equals("note")));
        assertTrue(deltas.stream().anyMatch(d -> d.getToolExecutionResult() != null));
        assertTrue(deltas.stream().anyMatch(d -> d.getTraceEvent() != null));
        assertTrue(deltas.stream().anyMatch(StreamDelta::isDone));
    }

    @Test
    void compressedSummaryIsPersistedAndReusedAsActiveContext(@TempDir Path tempDir) {
        var policy = CompressionPolicy.builder()
                .highWatermark(0.1)
                .lowWatermark(0.05)
                .maxContextTokens(10)
                .protectFirstMessages(1)
                .protectRecentRounds(1)
                .build();
        var compressor = new DefaultContextCompressor(tempDir.toString(), new ObjectMapper());
        var sessionId = "compress-session";

        Message first = userMessage("first instruction");
        Message oldAssistant = Message.builder().id("old-a").role(MessageRole.ASSISTANT).content("old answer").build();
        Message oldUser = Message.builder().id("old-u").role(MessageRole.USER).content("old detail").build();
        Message recentAssistant = Message.builder().id("recent-a").role(MessageRole.ASSISTANT).content("recent answer").build();
        Message currentUser = userMessage("current question");

        sessionStore.createSession(Session.builder().id(sessionId).userId("u1").build());
        for (Message m : List.of(first, oldAssistant, oldUser, recentAssistant, currentUser)) {
            sessionStore.appendMessage(sessionId, m);
        }

        fakeModel.thenReplyWithUsage("final answer", 2)
                .thenReply("{\"memory_writes\":[],\"summary\":\"compressed middle\"}");
        var compressingLoop = new ConversationLoop(
                fakeModel,
                new DefaultPromptBuilder(),
                toolRegistry,
                new DefaultToolDispatcher(toolRegistry),
                sessionStore,
                traceStore,
                toolExecutionStore,
                null,
                null,
                null,
                null,
                compressor,
                policy
        );

        compressingLoop.run(buildRequest(sessionId, List.of(first, oldAssistant, oldUser, recentAssistant, currentUser)));

        assertTrue(sessionStore.loadMessages(sessionId).stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("[Context Summary checkpoint=")));

        CapturingPromptBuilder capturingPromptBuilder = new CapturingPromptBuilder();
        var nextModel = new FakeModelClient().thenReply("next answer");
        var nextLoop = new ConversationLoop(
                nextModel,
                capturingPromptBuilder,
                toolRegistry,
                new DefaultToolDispatcher(toolRegistry),
                sessionStore,
                traceStore,
                toolExecutionStore,
                null,
                null,
                null,
                null,
                null,
                null,
                tempDir.toString()
        );
        Message nextUser = userMessage("next question");
        sessionStore.appendMessage(sessionId, nextUser);
        List<Message> nextMessages = new ArrayList<>(sessionStore.loadMessages(sessionId));

        nextLoop.run(buildRequest(sessionId, nextMessages));

        assertTrue(capturingPromptBuilder.capturedHistory.stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("compressed middle")));
        assertTrue(capturingPromptBuilder.capturedRetrievedMemories.stream()
                .anyMatch(memory -> memory.contains("compressed middle")));
        assertFalse(capturingPromptBuilder.capturedHistory.stream()
                .anyMatch(m -> "old-a".equals(m.getId())));
        assertFalse(capturingPromptBuilder.capturedHistory.stream()
                .anyMatch(m -> "old-u".equals(m.getId())));
        assertTrue(capturingPromptBuilder.capturedHistory.stream()
                .anyMatch(m -> "recent-a".equals(m.getId())));
    }

    private static class CapturingPromptBuilder implements PromptBuilder {
        private final PromptBuilder delegate = new DefaultPromptBuilder();
        private List<Message> capturedHistory = List.of();
        private List<String> capturedRetrievedMemories = List.of();

        @Override
        public PromptBuildResult build(PromptBuildRequest request) {
            capturedHistory = request.getSessionHistory();
            capturedRetrievedMemories = request.getRetrievedMemories();
            return delegate.build(request);
        }
    }
}

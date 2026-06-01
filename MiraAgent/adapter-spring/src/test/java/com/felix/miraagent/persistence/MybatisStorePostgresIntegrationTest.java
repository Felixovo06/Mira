package com.felix.miraagent.persistence;

import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.persistence.mybatis.MybatisSessionStore;
import com.felix.miraagent.persistence.mybatis.MybatisToolExecutionStore;
import com.felix.miraagent.persistence.mybatis.MybatisTraceStore;
import com.felix.miraagent.session.Session;
import com.felix.miraagent.session.SessionStore;
import com.felix.miraagent.tools.ToolExecutionRecord;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolExecutionStore;
import com.felix.miraagent.tools.ToolStatus;
import com.felix.miraagent.trace.TraceEvent;
import com.felix.miraagent.trace.TraceEventType;
import com.felix.miraagent.trace.TraceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 PostgreSQL 上验证 MyBatis-Plus 简单表迁移: sessions/messages/agent_traces/tool_executions
 * 的写入读出, 重点验证 jsonb 字段(tool_calls / payload / arguments)经 JsonbTypeHandler 的往返。
 * 仅在 -Dmira.it.postgres=true 时运行, 使用 local profile 的真实库凭据。
 */
@EnabledIfSystemProperty(named = "mira.it.postgres", matches = "true")
@SpringBootTest(properties = {
        "memory.base-dir=target/test-memory-mp-it",
        "mira.artifact.base-dir=target/test-artifacts-mp-it",
        "mira.summary.base-dir=target/test-summary-mp-it",
        "mira.weixin.enabled=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("local")
class MybatisStorePostgresIntegrationTest {

    @Autowired
    SessionStore sessionStore;
    @Autowired
    TraceStore traceStore;
    @Autowired
    ToolExecutionStore toolExecutionStore;
    @Autowired
    JdbcTemplate jdbc;

    private String sessionId;
    private String runId;
    private String userId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        sessionId = "mp-it-session-" + suffix;
        runId = "mp-it-run-" + suffix;
        userId = "mp-it-user-" + suffix;
    }

    @AfterEach
    void cleanDb() {
        jdbc.update("delete from tool_executions where session_id = ?", sessionId);
        jdbc.update("delete from agent_traces where session_id = ?", sessionId);
        jdbc.update("delete from messages where session_id = ?", sessionId);
        jdbc.update("delete from sessions where id = ?", sessionId);
    }

    @Test
    void storesAreBackedByMyBatis() {
        assertInstanceOf(MybatisSessionStore.class, sessionStore, "无 DB 时回退到了 InMemory, 检查 local profile 与 datasource.url");
        assertInstanceOf(MybatisTraceStore.class, traceStore);
        assertInstanceOf(MybatisToolExecutionStore.class, toolExecutionStore);
    }

    @Test
    void sessionAndMessagesRoundTripWithJsonbToolCalls() {
        sessionStore.createSession(Session.builder()
                .id(sessionId).userId(userId).characterId("char-1")
                .title("PG 联调").source("it").build());

        // 重复创建应幂等(on conflict do nothing)
        sessionStore.createSession(Session.builder().id(sessionId).userId(userId).build());

        assertTrue(sessionStore.findById(sessionId).isPresent());
        assertEquals(userId, sessionStore.findById(sessionId).get().getUserId());

        sessionStore.appendMessage(sessionId, Message.builder()
                .id(UUID.randomUUID().toString()).role(MessageRole.USER).content("你好").build());

        ToolCall toolCall = ToolCall.builder().id("tc-1").name("note")
                .arguments("{\"content\":\"记一笔\"}").build();
        sessionStore.appendMessage(sessionId, Message.builder()
                .id(UUID.randomUUID().toString()).role(MessageRole.ASSISTANT)
                .toolCall(toolCall).build());

        List<Message> messages = sessionStore.loadMessages(sessionId);
        assertEquals(2, messages.size());
        assertEquals(MessageRole.USER, messages.get(0).getRole());
        assertEquals("你好", messages.get(0).getContent());
        // jsonb tool_calls 往返
        assertEquals(1, messages.get(1).getToolCalls().size());
        assertEquals("note", messages.get(1).getToolCalls().get(0).getName());
        assertTrue(messages.get(1).getToolCalls().get(0).getArguments().contains("记一笔"));

        sessionStore.updateLastMessageAt(sessionId);
        Integer touched = jdbc.queryForObject(
                "select count(*) from sessions where id = ? and last_message_at is not null", Integer.class, sessionId);
        assertEquals(1, touched);
    }

    @Test
    void traceRoundTripWithJsonbPayload() {
        traceStore.record(TraceEvent.builder()
                .id(UUID.randomUUID().toString()).runId(runId).sessionId(sessionId)
                .stepIndex(0).eventType(TraceEventType.MODEL_REQUESTED)
                .payload(Map.of("model", "mimo", "tokens", 42)).build());

        List<TraceEvent> byRun = traceStore.findByRunId(runId);
        assertEquals(1, byRun.size());
        assertEquals(TraceEventType.MODEL_REQUESTED, byRun.get(0).getEventType());
        assertEquals("mimo", byRun.get(0).getPayload().get("model"));
        assertEquals(1, traceStore.findBySessionId(sessionId).size());
    }

    @Test
    void toolExecutionRoundTripWithJsonbArguments() {
        ToolCall call = ToolCall.builder().id("tc-x").name("note")
                .arguments("{\"content\":\"hello\"}").build();
        toolExecutionStore.record(runId, sessionId, call, ToolExecutionResult.success("tc-x", "note", "ok"));

        List<ToolExecutionRecord> byRun = toolExecutionStore.findByRunId(runId);
        assertEquals(1, byRun.size());
        assertEquals(ToolStatus.SUCCESS, byRun.get(0).getStatus());
        assertTrue(byRun.get(0).getArguments().contains("hello"));
        assertEquals(1, toolExecutionStore.findBySessionId(sessionId).size());
    }
}

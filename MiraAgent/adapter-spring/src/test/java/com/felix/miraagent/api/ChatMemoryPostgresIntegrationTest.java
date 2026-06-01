package com.felix.miraagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.api.dto.ChatApiRequest;
import com.felix.miraagent.model.ChatResponse;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.ModelClient;
import com.felix.miraagent.model.ToolCall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfSystemProperty(named = "mira.it.postgres", matches = "true")
@SpringBootTest(properties = {
        "memory.base-dir=target/test-memory-pg-it",
        "mira.artifact.base-dir=target/test-artifacts-pg-it",
        "mira.summary.base-dir=target/test-summary-pg-it",
        "mira.weixin.enabled=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class ChatMemoryPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ModelClient modelClient;

    private String userId;
    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        userId = "pg-it-user-" + suffix;
        sessionId = "pg-it-session-" + suffix;
        deleteIfExists(Path.of("target/test-memory-pg-it"));
        deleteIfExists(Path.of("target/test-artifacts-pg-it"));
        deleteIfExists(Path.of("target/test-summary-pg-it"));
    }

    @AfterEach
    void cleanDb() throws Exception {
        jdbc.update("delete from tool_executions where session_id = ?", sessionId);
        jdbc.update("delete from agent_traces where session_id = ?", sessionId);
        jdbc.update("delete from messages where session_id = ?", sessionId);
        jdbc.update("delete from sessions where id = ?", sessionId);
        jdbc.update("delete from memory_index where user_id = ?", userId);
        deleteIfExists(Path.of("target/test-memory-pg-it"));
        deleteIfExists(Path.of("target/test-artifacts-pg-it"));
        deleteIfExists(Path.of("target/test-summary-pg-it"));
    }

    @Test
    void chatWriteMemoryPersistsSessionAndMemoryIndexInPostgres() throws Exception {
        stubWriteMemoryThenFinal("真实PG联调记忆", "已写入真实库");

        ChatApiRequest request = new ChatApiRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setContent("请记住：真实PG联调记忆");
        request.setEnabledTools(List.of("write_memory"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.content").value("已写入真实库"))
                .andExpect(jsonPath("$.toolExecutions[0].status").value("SUCCESS"));

        Integer sessionRows = jdbc.queryForObject(
                "select count(*) from sessions where id = ? and user_id = ?",
                Integer.class,
                sessionId,
                userId);
        assertEquals(1, sessionRows);

        Integer memoryRows = jdbc.queryForObject(
                "select count(*) from memory_index where user_id = ? and source_session_id = ? and content_preview like ? and archived_at is null",
                Integer.class,
                userId,
                sessionId,
                "%真实PG联调记忆%");
        assertEquals(1, memoryRows);

        String memory = Files.readString(Path.of("target/test-memory-pg-it", userId, "PREFERENCES.md"));
        assertTrue(memory.contains("真实PG联调记忆"));
    }

    private void stubWriteMemoryThenFinal(String content, String finalAnswer) {
        AtomicInteger calls = new AtomicInteger();
        when(modelClient.chat(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                ToolCall toolCall = ToolCall.builder()
                        .id("write-pg-1")
                        .name("write_memory")
                        .arguments("{\"type\":\"PREFERENCE\",\"content\":\"" + content + "\"}")
                        .build();
                return ChatResponse.builder()
                        .assistantMessage(Message.builder()
                                .id(UUID.randomUUID().toString())
                                .role(MessageRole.ASSISTANT)
                                .toolCall(toolCall)
                                .build())
                        .toolCall(toolCall)
                        .finishReason("tool_calls")
                        .build();
            }
            return ChatResponse.builder()
                    .assistantMessage(Message.builder()
                            .id(UUID.randomUUID().toString())
                            .role(MessageRole.ASSISTANT)
                            .content(finalAnswer)
                            .build())
                    .finishReason("stop")
                    .build();
        });
    }

    private void deleteIfExists(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            for (Path p : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}

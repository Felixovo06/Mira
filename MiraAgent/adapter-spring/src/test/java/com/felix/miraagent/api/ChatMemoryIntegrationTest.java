package com.felix.miraagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.api.dto.ChatApiRequest;
import com.felix.miraagent.model.ChatResponse;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.ModelClient;
import com.felix.miraagent.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "mira.model.api-key=test-key",
        "mira.model.base-url=http://localhost:9999",
        "memory.base-dir=target/test-memory-it",
        "mira.artifact.base-dir=target/test-artifacts-it",
        "mira.summary.base-dir=target/test-summary-it"
})
@AutoConfigureMockMvc
class ChatMemoryIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ModelClient modelClient;

    @BeforeEach
    void cleanMemory() throws Exception {
        deleteIfExists(Path.of("target/test-memory-it"));
        deleteIfExists(Path.of("target/test-artifacts-it"));
        deleteIfExists(Path.of("target/test-summary-it"));
    }

    @Test
    void chatToolCallWritesMemoryThroughApiRuntimeAndWriter() throws Exception {
        stubWriteMemoryThenFinal("喜欢乌龙茶", "记住啦");

        ChatApiRequest request = new ChatApiRequest();
        request.setUserId("user-it");
        request.setSessionId("session-it");
        request.setContent("我喜欢乌龙茶");
        request.setEnabledTools(List.of("write_memory"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.content").value("记住啦"))
                .andExpect(jsonPath("$.toolExecutions[0].status").value("SUCCESS"));

        String memory = Files.readString(Path.of("target/test-memory-it/user-it/PREFERENCES.md"));
        org.junit.jupiter.api.Assertions.assertTrue(memory.contains("喜欢乌龙茶"));
    }

    @Test
    void banCategoryBlocksMemoryToolWriteThroughApi() throws Exception {
        mockMvc.perform(post("/api/memory/ban-category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "banned-user",
                                "category", "PREFERENCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ban recorded"));

        stubWriteMemoryThenFinal("不要写入", "不会写入");

        ChatApiRequest request = new ChatApiRequest();
        request.setUserId("banned-user");
        request.setSessionId("session-ban-it");
        request.setContent("我喜欢绿茶");
        request.setEnabledTools(List.of("write_memory"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.toolExecutions[0].status").value("ERROR"));

        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(Path.of("target/test-memory-it/banned-user/PREFERENCES.md")));
    }

    private void stubWriteMemoryThenFinal(String content, String finalAnswer) {
        AtomicInteger calls = new AtomicInteger();
        when(modelClient.chat(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                ToolCall toolCall = ToolCall.builder()
                        .id("write-1")
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

package com.felix.miraagent.persistence.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.persistence.entity.MessageEntity;
import com.felix.miraagent.persistence.entity.SessionEntity;
import com.felix.miraagent.persistence.mapper.MessageMapper;
import com.felix.miraagent.persistence.mapper.SessionMapper;
import com.felix.miraagent.session.Session;
import com.felix.miraagent.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MybatisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(MybatisSessionStore.class);

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public MybatisSessionStore(SessionMapper sessionMapper, MessageMapper messageMapper, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Session createSession(Session session) {
        // 对齐旧实现的 on conflict (id) do nothing 语义
        if (sessionMapper.selectById(session.getId()) != null) {
            return session;
        }
        SessionEntity entity = new SessionEntity();
        entity.setId(session.getId());
        entity.setUserId(session.getUserId());
        entity.setCharacterId(session.getCharacterId());
        entity.setTitle(session.getTitle());
        entity.setSource(session.getSource());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sessionMapper.insert(entity);
        return session;
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        SessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(Session.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .characterId(entity.getCharacterId())
                .title(entity.getTitle())
                .source(entity.getSource())
                .build());
    }

    @Override
    public void appendMessage(String sessionId, Message message) {
        MessageEntity entity = new MessageEntity();
        entity.setId(message.getId() != null ? message.getId() : UUID.randomUUID().toString());
        entity.setSessionId(sessionId);
        entity.setRole(message.getRole().name().toLowerCase());
        entity.setContent(message.getContent());
        entity.setToolCallId(message.getToolCallId());
        entity.setToolName(message.getToolName());
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            try {
                entity.setToolCalls(objectMapper.writeValueAsString(message.getToolCalls()));
            } catch (Exception e) {
                log.warn("Failed to serialize tool_calls for message {}", message.getId(), e);
            }
        }
        entity.setCreatedAt(Instant.now());
        messageMapper.insert(entity);
    }

    @Override
    public List<Message> loadMessages(String sessionId) {
        return messageMapper.selectList(Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getSessionId, sessionId)
                        .orderByAsc(MessageEntity::getCreatedAt))
                .stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void updateLastMessageAt(String sessionId) {
        Instant now = Instant.now();
        sessionMapper.update(null, Wrappers.<SessionEntity>lambdaUpdate()
                .eq(SessionEntity::getId, sessionId)
                .set(SessionEntity::getLastMessageAt, now)
                .set(SessionEntity::getUpdatedAt, now));
    }

    private Message toMessage(MessageEntity entity) {
        Message.MessageBuilder builder = Message.builder()
                .id(entity.getId())
                .role(MessageRole.valueOf(entity.getRole().toUpperCase()))
                .content(entity.getContent())
                .toolCallId(entity.getToolCallId())
                .toolName(entity.getToolName());

        if (entity.getToolCalls() != null) {
            try {
                List<ToolCall> toolCalls = objectMapper.readValue(entity.getToolCalls(), new TypeReference<>() {});
                toolCalls.forEach(builder::toolCall);
            } catch (Exception e) {
                log.warn("Failed to deserialize tool_calls for message {}", entity.getId(), e);
            }
        }
        return builder.build();
    }
}

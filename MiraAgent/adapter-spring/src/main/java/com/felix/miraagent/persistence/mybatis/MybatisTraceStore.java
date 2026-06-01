package com.felix.miraagent.persistence.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.persistence.entity.AgentTraceEntity;
import com.felix.miraagent.persistence.mapper.AgentTraceMapper;
import com.felix.miraagent.trace.TraceEvent;
import com.felix.miraagent.trace.TraceEventType;
import com.felix.miraagent.trace.TraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class MybatisTraceStore implements TraceStore {

    private static final Logger log = LoggerFactory.getLogger(MybatisTraceStore.class);

    private final AgentTraceMapper traceMapper;
    private final ObjectMapper objectMapper;

    public MybatisTraceStore(AgentTraceMapper traceMapper, ObjectMapper objectMapper) {
        this.traceMapper = traceMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(TraceEvent event) {
        AgentTraceEntity entity = new AgentTraceEntity();
        entity.setId(event.getId());
        entity.setRunId(event.getRunId());
        entity.setSessionId(event.getSessionId());
        entity.setStepIndex(event.getStepIndex());
        entity.setEventType(event.getEventType().name());
        if (event.getPayload() != null) {
            try {
                entity.setPayload(objectMapper.writeValueAsString(event.getPayload()));
            } catch (Exception e) {
                log.warn("Failed to serialize trace payload for event {}", event.getId(), e);
            }
        }
        entity.setCreatedAt(Instant.now());
        traceMapper.insert(entity);
    }

    @Override
    public List<TraceEvent> findByRunId(String runId) {
        return traceMapper.selectList(Wrappers.<AgentTraceEntity>lambdaQuery()
                        .eq(AgentTraceEntity::getRunId, runId)
                        .orderByAsc(AgentTraceEntity::getStepIndex))
                .stream()
                .map(this::toEvent)
                .toList();
    }

    @Override
    public List<TraceEvent> findBySessionId(String sessionId) {
        return traceMapper.selectList(Wrappers.<AgentTraceEntity>lambdaQuery()
                        .eq(AgentTraceEntity::getSessionId, sessionId)
                        .orderByAsc(AgentTraceEntity::getCreatedAt))
                .stream()
                .map(this::toEvent)
                .toList();
    }

    private TraceEvent toEvent(AgentTraceEntity entity) {
        Map<String, Object> payload = null;
        if (entity.getPayload() != null) {
            try {
                payload = objectMapper.readValue(entity.getPayload(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize trace payload for event {}", entity.getId(), e);
            }
        }
        return TraceEvent.builder()
                .id(entity.getId())
                .runId(entity.getRunId())
                .sessionId(entity.getSessionId())
                .stepIndex(entity.getStepIndex() != null ? entity.getStepIndex() : 0)
                .eventType(TraceEventType.valueOf(entity.getEventType()))
                .payload(payload)
                .build();
    }
}

package com.felix.miraagent.persistence.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.persistence.entity.ToolExecutionEntity;
import com.felix.miraagent.persistence.mapper.ToolExecutionMapper;
import com.felix.miraagent.tools.ToolExecutionRecord;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolExecutionStore;
import com.felix.miraagent.tools.ToolStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MybatisToolExecutionStore implements ToolExecutionStore {

    private final ToolExecutionMapper toolExecutionMapper;

    public MybatisToolExecutionStore(ToolExecutionMapper toolExecutionMapper) {
        this.toolExecutionMapper = toolExecutionMapper;
    }

    @Override
    public void record(String runId, String sessionId, ToolCall call, ToolExecutionResult result) {
        ToolExecutionEntity entity = new ToolExecutionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setRunId(runId);
        entity.setSessionId(sessionId);
        entity.setToolCallId(result.getToolCallId());
        entity.setToolName(result.getToolName());
        entity.setArguments(call != null ? call.getArguments() : null);
        entity.setStatus(result.getStatus().name());
        entity.setModelVisibleContent(result.getModelVisibleContent());
        entity.setErrorMessage(result.getError());
        entity.setStartedAt(result.getStartedAt() != null ? result.getStartedAt() : Instant.now());
        entity.setFinishedAt(result.getFinishedAt());
        toolExecutionMapper.insert(entity);
    }

    @Override
    public List<ToolExecutionRecord> findByRunId(String runId) {
        return toolExecutionMapper.selectList(Wrappers.<ToolExecutionEntity>lambdaQuery()
                        .eq(ToolExecutionEntity::getRunId, runId)
                        .orderByAsc(ToolExecutionEntity::getStartedAt)
                        .orderByAsc(ToolExecutionEntity::getId))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<ToolExecutionRecord> findBySessionId(String sessionId) {
        return toolExecutionMapper.selectList(Wrappers.<ToolExecutionEntity>lambdaQuery()
                        .eq(ToolExecutionEntity::getSessionId, sessionId)
                        .orderByAsc(ToolExecutionEntity::getStartedAt)
                        .orderByAsc(ToolExecutionEntity::getId))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    private ToolExecutionRecord toRecord(ToolExecutionEntity entity) {
        return ToolExecutionRecord.builder()
                .id(entity.getId())
                .runId(entity.getRunId())
                .sessionId(entity.getSessionId())
                .toolCallId(entity.getToolCallId())
                .toolName(entity.getToolName())
                .arguments(entity.getArguments())
                .status(ToolStatus.valueOf(entity.getStatus()))
                .modelVisibleContent(entity.getModelVisibleContent())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }
}

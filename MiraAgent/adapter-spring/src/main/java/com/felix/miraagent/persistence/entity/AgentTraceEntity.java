package com.felix.miraagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.felix.miraagent.persistence.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.Instant;

@Data
@TableName(value = "agent_traces", autoResultMap = true)
public class AgentTraceEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String runId;
    private String sessionId;
    private Integer stepIndex;
    private String eventType;

    @TableField(value = "payload", typeHandler = JsonbTypeHandler.class)
    private String payload;

    private Instant createdAt;
}

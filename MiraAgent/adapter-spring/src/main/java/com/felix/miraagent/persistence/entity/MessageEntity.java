package com.felix.miraagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.felix.miraagent.persistence.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.Instant;

@Data
@TableName(value = "messages", autoResultMap = true)
public class MessageEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String role;
    private String content;
    private String toolCallId;
    private String toolName;

    @TableField(value = "tool_calls", typeHandler = JsonbTypeHandler.class)
    private String toolCalls;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private String metadata;

    private Instant createdAt;
}

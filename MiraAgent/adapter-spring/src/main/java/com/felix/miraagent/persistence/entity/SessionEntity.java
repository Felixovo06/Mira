package com.felix.miraagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("sessions")
public class SessionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String userId;
    private String characterId;
    private String title;
    private String source;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastMessageAt;
}

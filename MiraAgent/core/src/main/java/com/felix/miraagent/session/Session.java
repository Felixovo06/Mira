package com.felix.miraagent.session;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class Session {
    String id;
    String userId;
    String characterId;
    String title;
    String source;
    @Builder.Default
    Instant createdAt = Instant.now();
    @Builder.Default
    Instant updatedAt = Instant.now();
    Instant lastMessageAt;
    String parentSessionId;

    List<com.felix.miraagent.model.Message> messages;
}

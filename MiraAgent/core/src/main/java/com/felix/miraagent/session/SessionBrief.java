package com.felix.miraagent.session;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class SessionBrief {
    String sessionId;
    String userId;
    String characterId;
    String title;
    Instant lastMessageAt;
    int messageCount;
}

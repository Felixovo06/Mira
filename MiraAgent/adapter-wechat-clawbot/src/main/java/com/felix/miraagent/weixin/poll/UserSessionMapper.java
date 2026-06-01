package com.felix.miraagent.weixin.poll;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserSessionMapper {
    private final ConcurrentHashMap<String, String> userToSession = new ConcurrentHashMap<>();

    public String getOrCreateSession(String userId) {
        return userToSession.computeIfAbsent(userId, k -> UUID.randomUUID().toString());
    }
}

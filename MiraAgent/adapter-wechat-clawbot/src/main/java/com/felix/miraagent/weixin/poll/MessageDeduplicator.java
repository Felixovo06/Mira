package com.felix.miraagent.weixin.poll;

import java.util.concurrent.ConcurrentHashMap;

public class MessageDeduplicator {
    private static final long TTL_MS = 300_000L;
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    public boolean isDuplicate(String messageId) {
        cleanupExpired();
        long now = System.currentTimeMillis();
        return seen.putIfAbsent(messageId, now) != null;
    }

    private void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        seen.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}

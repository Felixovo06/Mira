package com.felix.miraagent.memory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMemoryWritePolicy implements MemoryWritePolicy {

    private final Set<String> bannedCategories = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isAllowed(String userId, MemoryCategory category) {
        if (userId == null || category == null) {
            return false;
        }
        return !bannedCategories.contains(key(userId, category));
    }

    @Override
    public void banCategory(String userId, MemoryCategory category) {
        if (userId != null && !userId.isBlank() && category != null) {
            bannedCategories.add(key(userId, category));
        }
    }

    private String key(String userId, MemoryCategory category) {
        return userId + ":" + category.name();
    }
}

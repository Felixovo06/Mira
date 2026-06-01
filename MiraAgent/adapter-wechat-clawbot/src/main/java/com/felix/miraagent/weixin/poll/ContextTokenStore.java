package com.felix.miraagent.weixin.poll;

import java.util.concurrent.ConcurrentHashMap;

public class ContextTokenStore {
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    public void put(String senderId, String contextToken) {
        store.put(senderId, contextToken);
    }

    public String get(String senderId) {
        return store.get(senderId);
    }
}

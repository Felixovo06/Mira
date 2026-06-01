package com.felix.miraagent.session.impl;

import com.felix.miraagent.model.Message;
import com.felix.miraagent.session.Session;
import com.felix.miraagent.session.SessionStore;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySessionStore implements SessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<Message>> messages = new ConcurrentHashMap<>();

    @Override
    public Session createSession(Session session) {
        sessions.put(session.getId(), session);
        messages.put(session.getId(), new CopyOnWriteArrayList<>());
        return session;
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void appendMessage(String sessionId, Message message) {
        messages.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public List<Message> loadMessages(String sessionId) {
        return Collections.unmodifiableList(
                messages.getOrDefault(sessionId, Collections.emptyList()));
    }

    @Override
    public void updateLastMessageAt(String sessionId) {
        // no-op for in-memory
    }
}

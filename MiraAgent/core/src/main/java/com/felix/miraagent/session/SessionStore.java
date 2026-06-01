package com.felix.miraagent.session;

import com.felix.miraagent.model.Message;

import java.util.List;
import java.util.Optional;

public interface SessionStore {
    Session createSession(Session session);

    Optional<Session> findById(String sessionId);

    void appendMessage(String sessionId, Message message);

    List<Message> loadMessages(String sessionId);

    void updateLastMessageAt(String sessionId);
}

package com.felix.miraagent.session;

import com.felix.miraagent.model.Message;

import java.util.List;

public interface SessionSearchService {

    SessionDiscoveryResult discovery(String sessionId, String query, int contextWindow);

    List<Message> scroll(String sessionId, String anchorMessageId, int contextWindow);

    List<SessionBrief> browse(String userId, int limit);
}

package com.felix.miraagent.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.session.AnchoredMessageView;
import com.felix.miraagent.session.SessionBrief;
import com.felix.miraagent.session.SessionDiscoveryResult;
import com.felix.miraagent.session.SessionSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcSessionSearchService implements SessionSearchService {

    private static final Logger log = LoggerFactory.getLogger(JdbcSessionSearchService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSessionSearchService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public SessionDiscoveryResult discovery(String sessionId, String query, int contextWindow) {
        if (sessionId == null || query == null || query.isBlank()) {
            return SessionDiscoveryResult.builder()
                    .sessionId(sessionId)
                    .anchors(Collections.emptyList())
                    .build();
        }

        String sql = """
                SELECT m.id, m.session_id, m.role, m.content, m.tool_call_id, m.tool_name,
                       m.tool_calls, m.created_at,
                       similarity(m.content, ?) as trgm_score,
                       ts_rank(to_tsvector('simple', coalesce(m.content, '')), plainto_tsquery('simple', ?)) as fts_score
                FROM messages m
                WHERE m.session_id = ?
                  AND (
                    m.content % ?
                    OR to_tsvector('simple', coalesce(m.content, '')) @@ plainto_tsquery('simple', ?)
                  )
                ORDER BY (similarity(m.content, ?) + ts_rank(to_tsvector('simple', coalesce(m.content, '')), plainto_tsquery('simple', ?))) DESC
                LIMIT 20
                """;

        List<ScoredMessage> hits;
        try {
            hits = jdbc.query(sql, scoredMessageRowMapper(), query, query, sessionId, query, query, query, query);
        } catch (Exception e) {
            log.warn("discovery query failed for session {}", sessionId, e);
            return SessionDiscoveryResult.builder()
                    .sessionId(sessionId)
                    .anchors(Collections.emptyList())
                    .build();
        }

        List<AnchoredMessageView> anchors = new ArrayList<>();
        for (ScoredMessage hit : hits) {
            List<Message> context = scroll(sessionId, hit.message.getId(), contextWindow);
            List<Message> before = new ArrayList<>();
            List<Message> after = new ArrayList<>();
            boolean foundAnchor = false;
            for (Message m : context) {
                if (m.getId().equals(hit.message.getId())) {
                    foundAnchor = true;
                } else if (!foundAnchor) {
                    before.add(m);
                } else {
                    after.add(m);
                }
            }
            anchors.add(AnchoredMessageView.builder()
                    .anchorMessage(hit.message)
                    .contextBefore(before)
                    .contextAfter(after)
                    .relevanceScore(hit.score)
                    .build());
        }

        return SessionDiscoveryResult.builder()
                .sessionId(sessionId)
                .anchors(anchors)
                .build();
    }

    @Override
    public List<Message> scroll(String sessionId, String anchorMessageId, int contextWindow) {
        if (sessionId == null || anchorMessageId == null) {
            return Collections.emptyList();
        }

        List<Timestamp> anchorTimes = jdbc.query(
                "SELECT created_at FROM messages WHERE id = ? AND session_id = ?",
                (rs, rn) -> rs.getTimestamp("created_at"),
                anchorMessageId, sessionId);

        if (anchorTimes.isEmpty()) {
            return Collections.emptyList();
        }

        Timestamp anchorTime = anchorTimes.get(0);
        int fetchWindow = contextWindow + 2;

        List<Message> rawBefore;
        try {
            rawBefore = jdbc.query(
                    """
                    SELECT id, role, content, tool_call_id, tool_name, tool_calls, created_at
                    FROM messages
                    WHERE session_id = ? AND created_at < ?
                    ORDER BY created_at DESC
                    LIMIT ?
                    """,
                    messageRowMapper(), sessionId, anchorTime, fetchWindow);
        } catch (Exception e) {
            log.warn("scroll before query failed for anchor {}", anchorMessageId, e);
            rawBefore = Collections.emptyList();
        }

        List<Message> rawAfter;
        try {
            rawAfter = jdbc.query(
                    """
                    SELECT id, role, content, tool_call_id, tool_name, tool_calls, created_at
                    FROM messages
                    WHERE session_id = ? AND created_at > ?
                    ORDER BY created_at ASC
                    LIMIT ?
                    """,
                    messageRowMapper(), sessionId, anchorTime, fetchWindow);
        } catch (Exception e) {
            log.warn("scroll after query failed for anchor {}", anchorMessageId, e);
            rawAfter = Collections.emptyList();
        }

        List<Message> before = new ArrayList<>(rawBefore);
        Collections.reverse(before);

        before = trimAndExtendBefore(before, contextWindow, sessionId, anchorTime);
        List<Message> after = trimAndExtendAfter(rawAfter, contextWindow, sessionId, anchorTime);

        List<Message> anchor;
        try {
            anchor = jdbc.query(
                    "SELECT id, role, content, tool_call_id, tool_name, tool_calls, created_at FROM messages WHERE id = ?",
                    messageRowMapper(), anchorMessageId);
        } catch (Exception e) {
            log.warn("scroll anchor query failed for {}", anchorMessageId, e);
            anchor = Collections.emptyList();
        }

        List<Message> result = new ArrayList<>();
        result.addAll(before);
        if (!anchor.isEmpty()) {
            result.add(anchor.get(0));
        }
        result.addAll(after);
        return result;
    }

    private List<Message> trimAndExtendBefore(List<Message> before, int contextWindow, String sessionId, Timestamp anchorTime) {
        if (before.size() <= contextWindow) {
            return before;
        }
        List<Message> trimmed = new ArrayList<>(before.subList(before.size() - contextWindow, before.size()));
        Message first = trimmed.get(0);
        if (first.getRole() == MessageRole.TOOL) {
            List<Message> extra = new ArrayList<>();
            try {
                Timestamp firstTime = toTimestamp(first.getCreatedAt());
                extra = jdbc.query(
                        """
                        SELECT id, role, content, tool_call_id, tool_name, tool_calls, created_at
                        FROM messages
                        WHERE session_id = ? AND created_at < ? AND created_at < ?
                        ORDER BY created_at DESC
                        LIMIT 5
                        """,
                        messageRowMapper(), sessionId, firstTime, anchorTime);
                Collections.reverse(extra);
            } catch (Exception e) {
                log.warn("scroll extend-before query failed", e);
            }
            List<Message> extended = new ArrayList<>(extra);
            extended.addAll(trimmed);
            return extended;
        }
        return trimmed;
    }

    private List<Message> trimAndExtendAfter(List<Message> after, int contextWindow, String sessionId, Timestamp anchorTime) {
        if (after.size() <= contextWindow) {
            return after;
        }
        List<Message> trimmed = new ArrayList<>(after.subList(0, contextWindow));
        Message last = trimmed.get(trimmed.size() - 1);
        if (last.getRole() == MessageRole.ASSISTANT && last.getToolCalls() != null && !last.getToolCalls().isEmpty()) {
            List<Message> extra = new ArrayList<>();
            try {
                Timestamp lastTime = toTimestamp(last.getCreatedAt());
                extra = jdbc.query(
                        """
                        SELECT id, role, content, tool_call_id, tool_name, tool_calls, created_at
                        FROM messages
                        WHERE session_id = ? AND created_at > ? AND created_at > ?
                        ORDER BY created_at ASC
                        LIMIT 5
                        """,
                        messageRowMapper(), sessionId, lastTime, anchorTime);
            } catch (Exception e) {
                log.warn("scroll extend-after query failed", e);
            }
            List<Message> extended = new ArrayList<>(trimmed);
            for (Message m : extra) {
                extended.add(m);
                if (m.getRole() != MessageRole.TOOL) {
                    break;
                }
            }
            return extended;
        }
        return trimmed;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : new Timestamp(0);
    }

    @Override
    public List<SessionBrief> browse(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }

        String sql = """
                SELECT s.id, s.user_id, s.character_id, s.title, s.last_message_at,
                       COUNT(m.id) as message_count
                FROM sessions s
                LEFT JOIN messages m ON m.session_id = s.id
                WHERE s.user_id = ?
                GROUP BY s.id, s.user_id, s.character_id, s.title, s.last_message_at
                ORDER BY s.last_message_at DESC NULLS LAST
                LIMIT ?
                """;

        try {
            return jdbc.query(sql, sessionBriefRowMapper(), userId, limit);
        } catch (Exception e) {
            log.warn("browse query failed for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    private RowMapper<SessionBrief> sessionBriefRowMapper() {
        return (rs, rowNum) -> {
            Timestamp lastMessageAt = rs.getTimestamp("last_message_at");
            return SessionBrief.builder()
                    .sessionId(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .characterId(rs.getString("character_id"))
                    .title(rs.getString("title"))
                    .lastMessageAt(lastMessageAt != null ? lastMessageAt.toInstant() : null)
                    .messageCount((int) rs.getLong("message_count"))
                    .build();
        };
    }

    private RowMapper<Message> messageRowMapper() {
        return (rs, rowNum) -> {
            String roleStr = rs.getString("role").toUpperCase();
            MessageRole role = MessageRole.valueOf(roleStr);
            Timestamp createdAt = rs.getTimestamp("created_at");
            var builder = Message.builder()
                    .id(rs.getString("id"))
                    .role(role)
                    .content(rs.getString("content"))
                    .toolCallId(rs.getString("tool_call_id"))
                    .toolName(rs.getString("tool_name"))
                    .createdAt(createdAt != null ? createdAt.toInstant() : Instant.now());

            String toolCallsJson = rs.getString("tool_calls");
            if (toolCallsJson != null) {
                try {
                    List<ToolCall> toolCalls = objectMapper.readValue(toolCallsJson, new TypeReference<>() {});
                    toolCalls.forEach(builder::toolCall);
                } catch (Exception e) {
                    log.warn("Failed to deserialize tool_calls for message {}", rs.getString("id"), e);
                }
            }
            return builder.build();
        };
    }

    private RowMapper<ScoredMessage> scoredMessageRowMapper() {
        return (rs, rowNum) -> {
            String roleStr = rs.getString("role").toUpperCase();
            MessageRole role = MessageRole.valueOf(roleStr);
            Timestamp createdAt = rs.getTimestamp("created_at");
            var builder = Message.builder()
                    .id(rs.getString("id"))
                    .role(role)
                    .content(rs.getString("content"))
                    .toolCallId(rs.getString("tool_call_id"))
                    .toolName(rs.getString("tool_name"))
                    .createdAt(createdAt != null ? createdAt.toInstant() : Instant.now());

            String toolCallsJson = rs.getString("tool_calls");
            if (toolCallsJson != null) {
                try {
                    List<ToolCall> toolCalls = objectMapper.readValue(toolCallsJson, new TypeReference<>() {});
                    toolCalls.forEach(builder::toolCall);
                } catch (Exception e) {
                    log.warn("Failed to deserialize tool_calls for message {}", rs.getString("id"), e);
                }
            }

            double score = rs.getDouble("trgm_score") + rs.getDouble("fts_score");
            return new ScoredMessage(builder.build(), score);
        };
    }

    private record ScoredMessage(Message message, double score) {}
}

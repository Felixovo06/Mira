package com.felix.miraagent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.felix.miraagent.eval.model.EvalCase;
import com.felix.miraagent.eval.model.RunOutcome;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 把运行中的 MiraAgent 当黑盒，经 SSE {@code /api/chat/stream} 跑一条用例，
 * 采集 TTFT / 总耗时 / 工具调用 / token / 最终回复——评测只依赖系统的公开 API，不侵入内部。
 */
public class AgentEvalClient {

    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public AgentEvalClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public RunOutcome run(EvalCase c) {
        String sessionId = "eval-" + UUID.randomUUID();
        String userId = "eval-" + (c.id() != null ? c.id() : "x");

        // 多轮场景:先把前置消息按序发出(同 session),建立上下文,再评最后一条
        for (String prior : c.setupMessages()) {
            sendSetup(sessionId, userId, prior, c.enabledTools());
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("userId", userId);
        body.put("sessionId", sessionId);
        body.put("content", c.userMessage());
        body.put("stream", true);
        if (c.characterId() != null) {
            body.put("characterId", c.characterId());
        }
        if (c.enabledTools() != null && !c.enabledTools().isEmpty()) {
            ArrayNode tools = body.putArray("enabledTools");
            c.enabledTools().forEach(tools::add);
        }

        List<Map<String, String>> toolCalls = new ArrayList<>();
        Map<String, String> toolStatuses = new LinkedHashMap<>();
        long[] ttft = {-1};
        int[] usage = {0, 0};
        String[] finalContent = {""};
        String[] error = {null};
        boolean[] done = {false};

        long t0 = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat/stream"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<Stream<String>> resp = http.send(req, HttpResponse.BodyHandlers.ofLines());
            if (resp.statusCode() / 100 != 2) {
                return fail("HTTP " + resp.statusCode(), sessionId, t0);
            }
            String[] event = {""};
            try (Stream<String> lines = resp.body()) {
                lines.forEach(raw -> {
                    String line = raw.replaceAll("\r$", "");
                    if (line.isEmpty()) {
                        event[0] = "";
                    } else if (line.startsWith("event:")) {
                        event[0] = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        handle(event[0], line.substring(5).trim(), t0, ttft, toolCalls,
                                toolStatuses, usage, finalContent, error, done);
                    }
                });
            }
        } catch (Exception e) {
            return fail(e.getMessage(), sessionId, t0);
        }

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        boolean ok = done[0] && error[0] == null;
        return new RunOutcome(ok, error[0], sessionId, ttft[0] < 0 ? totalMs : ttft[0], totalMs,
                toolCalls, toolStatuses, usage[0], usage[1], finalContent[0]);
    }

    /** 发一条前置消息(非流式,忽略结果),用于在同 session 建立多轮上下文。 */
    private void sendSetup(String sessionId, String userId, String content, List<String> tools) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("userId", userId);
            body.put("sessionId", sessionId);
            body.put("content", content);
            if (tools != null && !tools.isEmpty()) {
                ArrayNode t = body.putArray("enabledTools");
                tools.forEach(t::add);
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
            // 前置消息失败不致命,继续评估目标消息
        }
    }

    /**
     * 轮询会话 trace,观测异步「自我改善」(后台复盘)的结果——经公开 trace API,黑盒。
     * 后台复盘在主回复后异步触发,故需等待至多 timeoutMs。
     */
    public ReviewObservation pollReview(String sessionId, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        while (System.nanoTime() < deadline) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/traces/sessions/" + sessionId))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    for (com.fasterxml.jackson.databind.JsonNode e : mapper.readTree(resp.body())) {
                        if ("BACKGROUND_REVIEW_FINISHED".equals(e.path("eventType").asText())) {
                            com.fasterxml.jackson.databind.JsonNode p = e.path("payload");
                            return new ReviewObservation(true,
                                    p.path("review_triggered_by").asText(""),
                                    p.path("skillWrites").asInt(0),
                                    p.path("memoryWrites").asInt(0));
                        }
                    }
                }
            } catch (Exception ignored) {
                // 重试到超时
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new ReviewObservation(false, "", 0, 0);
    }

    /** 后台复盘观测:是否触发、触发原因、写入的技能/记忆条数。 */
    public record ReviewObservation(boolean triggered, String triggeredBy, int skillWrites, int memoryWrites) {
    }

    private void handle(String event, String data, long t0, long[] ttft,
                        List<Map<String, String>> toolCalls, Map<String, String> toolStatuses,
                        int[] usage, String[] finalContent, String[] error, boolean[] done) {
        try {
            switch (event) {
                case "text_delta" -> {
                    if (ttft[0] < 0) {
                        ttft[0] = (System.nanoTime() - t0) / 1_000_000;
                    }
                }
                case "tool_call" -> {
                    JsonNode n = mapper.readTree(data);
                    Map<String, String> tc = new LinkedHashMap<>();
                    tc.put("name", n.path("name").asText(""));
                    tc.put("arguments", n.path("arguments").asText(""));
                    toolCalls.add(tc);
                }
                case "done" -> {
                    JsonNode r = mapper.readTree(data);
                    JsonNode u = r.path("usage");
                    if (u.isObject()) {
                        usage[0] = u.path("inputTokens").asInt(0);
                        usage[1] = u.path("outputTokens").asInt(0);
                    }
                    JsonNode fm = r.path("finalMessage");
                    finalContent[0] = fm.path("content").asText(r.path("content").asText(""));
                    for (JsonNode te : r.path("toolExecutions")) {
                        toolStatuses.put(te.path("toolName").asText(""), te.path("status").asText(""));
                    }
                    done[0] = true;
                }
                case "error" -> error[0] = mapper.readTree(data).path("message").asText("stream error");
                default -> { /* start / tool_result / trace 忽略 */ }
            }
        } catch (Exception e) {
            error[0] = "parse: " + e.getMessage();
        }
    }

    private RunOutcome fail(String err, String sessionId, long t0) {
        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        return new RunOutcome(false, err, sessionId, totalMs, totalMs,
                List.of(), Map.of(), 0, 0, "");
    }
}

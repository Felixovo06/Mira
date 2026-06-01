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
        ObjectNode body = mapper.createObjectNode();
        body.put("userId", "eval-" + (c.id() != null ? c.id() : "x"));
        body.put("sessionId", "eval-" + UUID.randomUUID());
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
                return fail("HTTP " + resp.statusCode(), t0);
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
            return fail(e.getMessage(), t0);
        }

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        boolean ok = done[0] && error[0] == null;
        return new RunOutcome(ok, error[0], ttft[0] < 0 ? totalMs : ttft[0], totalMs,
                toolCalls, toolStatuses, usage[0], usage[1], finalContent[0]);
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

    private RunOutcome fail(String err, long t0) {
        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        return new RunOutcome(false, err, totalMs, totalMs,
                List.of(), Map.of(), 0, 0, "");
    }
}

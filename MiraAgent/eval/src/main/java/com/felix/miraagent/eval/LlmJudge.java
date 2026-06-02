package com.felix.miraagent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.felix.miraagent.eval.model.EvalCase;
import com.felix.miraagent.eval.model.JudgeScores;
import com.felix.miraagent.eval.model.RunOutcome;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Layer 3 质量评测：LLM-as-Judge。直接对接 OpenAI 兼容 chat completions 端点打分，
 * <b>不依赖被测系统的任何内部类</b>（保持 eval 模块与主链路解耦）。
 * <p>每条用例多次评分取中位数以抑制单次抖动；未配置 judge 凭据时整体禁用（L3 跳过）。
 */
public class LlmJudge {

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int samples;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private LlmJudge(String baseUrl, String apiKey, String model, int samples) {
        this.baseUrl = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
        this.samples = Math.max(1, samples);
        this.enabled = baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    /** 显式装配（如复用聊天模型的凭据）；apiKey 为空则禁用。 */
    public static LlmJudge of(String baseUrl, String apiKey, String model, int samples) {
        return new LlmJudge(baseUrl, apiKey, model, samples);
    }

    /** 从系统属性/环境变量装配；无 apiKey 则禁用。 */
    public static LlmJudge fromConfig() {
        String base = prop("eval.judge.baseUrl", "EVAL_JUDGE_BASE_URL");
        String key = prop("eval.judge.apiKey", "EVAL_JUDGE_API_KEY");
        String model = prop("eval.judge.model", "EVAL_JUDGE_MODEL");
        String n = prop("eval.judge.samples", "EVAL_JUDGE_SAMPLES");
        return new LlmJudge(base, key, model == null ? "" : model, n == null ? 3 : Integer.parseInt(n));
    }

    public boolean enabled() {
        return enabled;
    }

    /** 多次评分取中位数；失败/禁用返回 null。 */
    public JudgeScores score(EvalCase c, RunOutcome o) {
        if (!enabled || !o.ok()) {
            return null;
        }
        List<JudgeScores> shots = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            JudgeScores s = scoreOnce(c, o);
            if (s != null) {
                shots.add(s);
            }
        }
        return shots.isEmpty() ? null : JudgeScores.median(shots);
    }

    private JudgeScores scoreOnce(EvalCase c, RunOutcome o) {
        String prompt = buildPrompt(c, o);
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0);
            ArrayNode msgs = body.putArray("messages");
            ObjectNode m = msgs.addObject();
            m.put("role", "user");
            m.put("content", prompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return null;
            }
            String content = mapper.readTree(resp.body())
                    .path("choices").path(0).path("message").path("content").asText("");
            return parse(stripFence(content));
        } catch (Exception e) {
            return null;
        }
    }

    private JudgeScores parse(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            return new JudgeScores(
                    intOrNull(n, "relevance"),
                    intOrNull(n, "persona_consistency"),
                    intOrNull(n, "tool_usage"),
                    intOrNull(n, "overall"));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || !v.canConvertToInt() ? null : v.asInt();
    }

    private String buildPrompt(EvalCase c, RunOutcome o) {
        String persona = c.characterId() != null ? c.characterId() : "（无特定角色）";
        String tools = o.toolNames().isEmpty() ? "（未调用工具）" : String.join(", ", o.toolNames());
        return """
                你是一名严格的 AI 对话质量评审员。根据信息为 Agent 回复打分，只返回 JSON。

                【角色设定】%s
                【用户消息】%s
                【Agent 调用的工具】%s
                【Agent 回复】%s

                请按以下维度打分（1-5 整数；不适用填 null）：
                - relevance: 回复与用户意图的相关程度
                - persona_consistency: 与角色设定的一致程度（无角色填 null）
                - tool_usage: 工具使用的必要性与正确性（未调用工具填 null）
                - overall: 综合质量

                只返回 JSON，例如：{"relevance":4,"persona_consistency":5,"tool_usage":null,"overall":4}
                """.formatted(persona, c.userMessage(), tools, o.finalContent());
    }

    /** 去掉模型可能包裹的 ```json 围栏。 */
    private String stripFence(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        int i = t.indexOf('{'), j = t.lastIndexOf('}');
        return (i >= 0 && j > i) ? t.substring(i, j + 1) : t;
    }

    private static String prop(String sysProp, String env) {
        String v = System.getProperty(sysProp);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return v == null || v.isBlank() ? null : v;
    }
}

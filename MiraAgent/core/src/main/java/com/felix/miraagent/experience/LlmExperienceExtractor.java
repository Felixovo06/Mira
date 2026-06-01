package com.felix.miraagent.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.model.ChatRequest;
import com.felix.miraagent.model.ChatResponse;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.model.ModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ExperienceExtractor 的 LLM 实现：prompt 强约束固定 JSON schema + 解析 + 失败重试一次
 * （不依赖 provider 的 schema 强制，因 mimo 不一定支持）。confidence 按来源定死，不信任 LLM 自报值。
 */
public class LlmExperienceExtractor implements ExperienceExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmExperienceExtractor.class);

    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;

    public LlmExperienceExtractor(ModelClient modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExperienceReviewResult extract(ExperienceReviewRequest request) {
        if (request == null || request.getTranscript() == null || request.getTranscript().isBlank()) {
            return ExperienceReviewResult.nothing();
        }
        String prompt = buildPrompt(request, false);
        ExperienceReviewResult result = callAndParse(prompt, request);
        if (result == null) {
            // 重试一次，强调只输出 JSON
            result = callAndParse(buildPrompt(request, true), request);
        }
        return result != null ? result : ExperienceReviewResult.nothing();
    }

    private ExperienceReviewResult callAndParse(String prompt, ExperienceReviewRequest request) {
        ChatRequest chatRequest = ChatRequest.builder()
                .message(Message.builder().id(UUID.randomUUID().toString())
                        .role(MessageRole.USER).content(prompt).build())
                .temperature(0.1)
                .maxTokens(1500)
                .stream(false)
                .build();
        try {
            ChatResponse response = modelClient.chat(chatRequest);
            String raw = response.getAssistantMessage() != null ? response.getAssistantMessage().getContent() : "";
            return parse(raw, request);
        } catch (Exception e) {
            log.warn("Experience extraction LLM call failed: {}", e.getMessage());
            return null;
        }
    }

    private ExperienceReviewResult parse(String raw, ExperienceReviewRequest request) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = stripFences(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            ExperienceReviewResult.ExperienceReviewResultBuilder builder = ExperienceReviewResult.builder()
                    .worthSaving(root.path("worth_saving").asBoolean(false));

            double confidence = request.getConfidenceSource().value();
            for (JsonNode m : arrayOrEmpty(root, "memory_writes")) {
                String content = m.path("content").asText("").trim();
                if (content.isEmpty()) {
                    continue;
                }
                builder.memoryWrite(MemoryWritePlan.builder()
                        .kind(m.path("kind").asText("fact"))
                        .content(content)
                        .scope(m.path("scope").asText("global"))
                        .confidence(confidence)   // 定死，忽略 LLM 自报
                        .sourceTraceId(request.getSourceTraceId())
                        .dedupKey(m.path("dedup_key").asText(null))
                        .build());
            }
            for (JsonNode s : arrayOrEmpty(root, "skill_writes")) {
                String op = s.path("op").asText("create").trim();
                String name = s.path("name").asText("").trim();
                if (!"patch".equals(op) && name.isEmpty()) {
                    continue;
                }
                builder.skillWrite(SkillWritePlan.builder()
                        .op(op)
                        .targetSkillId(s.path("target_skill_id").asText(null))
                        .name(name)
                        .description(s.path("description").asText("").trim())
                        .whenToUse(s.path("when_to_use").asText("").trim())
                        .steps(stringList(s, "steps"))
                        .toolSuggestions(stringList(s, "tool_suggestions"))
                        .checklist(stringList(s, "checklist"))
                        .sourceTraceId(request.getSourceTraceId())
                        .build());
            }
            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to parse experience extraction JSON: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(ExperienceReviewRequest request, boolean retry) {
        StringBuilder sb = new StringBuilder();
        if (retry) {
            sb.append("严格只输出合法 JSON，不要任何解释、不要 markdown 代码块。\n");
        }
        sb.append("你是经验提炼器。阅读下面这轮对话/工具记录，判断是否有值得长期沉淀的内容，并输出固定 JSON：\n");
        sb.append("{\n");
        sb.append("  \"worth_saving\": true/false,\n");
        sb.append("  \"memory_writes\": [{\"kind\":\"fact|preference|relationship|tool_experience\",\"content\":\"用户事实\",\"scope\":\"global|character\",\"dedup_key\":\"可选\"}],\n");
        sb.append("  \"skill_writes\": [{\"op\":\"create|patch\",\"target_skill_id\":\"patch时填\",\"name\":\"技能名\",\"description\":\"何时用\",\"when_to_use\":\"...\",\"steps\":[\"...\"],\"tool_suggestions\":[\"...\"],\"checklist\":[\"...\"]}]\n");
        sb.append("}\n");
        sb.append("铁律：\n");
        sb.append("- 用户事实/隐私/偏好只能进 memory_writes；skill_writes 只放可复用的做事流程，绝不含任何用户事实。\n");
        sb.append("- 不要把一次性细节写成通用 skill；不要把失败流程当成功经验；不要把角色设定写进 skill。\n");
        sb.append("- 没有值得沉淀的内容就 worth_saving=false 且两个数组都为空。\n");
        if (request.getFocusHint() != null && !request.getFocusHint().isBlank()) {
            sb.append("- 本轮重点：").append(request.getFocusHint()).append("\n");
        }
        sb.append("\n对话/记录：\n").append(request.getTranscript()).append("\n");
        return sb.toString();
    }

    private String stripFences(String raw) {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl >= 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        // 截取首个 { 到末个 }，容忍前后噪声
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        return (start >= 0 && end > start) ? t.substring(start, end + 1) : t;
    }

    private Iterable<JsonNode> arrayOrEmpty(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isArray() ? node : List.of();
    }

    private List<String> stringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) {
            arr.forEach(n -> {
                String v = n.asText("").trim();
                if (!v.isEmpty()) {
                    result.add(v);
                }
            });
        }
        return result;
    }
}

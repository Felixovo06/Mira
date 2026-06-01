package com.felix.miraagent.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.fake.FakeModelClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmExperienceExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ExperienceReviewRequest req(ConfidenceSource source) {
        return ExperienceReviewRequest.builder()
                .userId("u1").sessionId("s1").sourceTraceId("trace-1")
                .transcript("[USER] 帮我把考研复习拆成周计划\n[ASSISTANT] 好的...")
                .confidenceSource(source)
                .build();
    }

    @Test
    void parsesMemoryAndSkillWrites() {
        String json = """
                {"worth_saving": true,
                 "memory_writes": [{"kind":"fact","content":"用户在准备考研, 目标12月","scope":"global","confidence":0.6}],
                 "skill_writes": [{"op":"create","name":"复习计划制定","description":"拆周计划",
                     "when_to_use":"用户要求规划","steps":["问截止","拆成周"],"tool_suggestions":["todo"],"checklist":["有量化目标"]}]}
                """;
        var extractor = new LlmExperienceExtractor(new FakeModelClient().thenReply(json), mapper);
        ExperienceReviewResult r = extractor.extract(req(ConfidenceSource.USER_EXPLICIT));

        assertTrue(r.isWorthSaving());
        assertEquals(1, r.getMemoryWrites().size());
        assertEquals("用户在准备考研, 目标12月", r.getMemoryWrites().get(0).getContent());
        // confidence 按来源定死=1.0，忽略 LLM 自报的 0.6
        assertEquals(1.0, r.getMemoryWrites().get(0).getConfidence());
        assertEquals("trace-1", r.getMemoryWrites().get(0).getSourceTraceId());

        assertEquals(1, r.getSkillWrites().size());
        assertEquals("create", r.getSkillWrites().get(0).getOp());
        assertEquals(java.util.List.of("问截止", "拆成周"), r.getSkillWrites().get(0).getSteps());
    }

    @Test
    void confidenceFollowsAgentInferredSource() {
        String json = "{\"worth_saving\":true,\"memory_writes\":[{\"kind\":\"fact\",\"content\":\"x\"}],\"skill_writes\":[]}";
        var extractor = new LlmExperienceExtractor(new FakeModelClient().thenReply(json), mapper);
        var r = extractor.extract(req(ConfidenceSource.AGENT_INFERRED));
        assertEquals(0.6, r.getMemoryWrites().get(0).getConfidence());
    }

    @Test
    void stripsCodeFences() {
        String fenced = "```json\n{\"worth_saving\":false,\"memory_writes\":[],\"skill_writes\":[]}\n```";
        var extractor = new LlmExperienceExtractor(new FakeModelClient().thenReply(fenced), mapper);
        var r = extractor.extract(req(ConfidenceSource.AGENT_INFERRED));
        assertFalse(r.isWorthSaving());
        assertTrue(r.getMemoryWrites().isEmpty());
    }

    @Test
    void retriesOnceThenGivesUpGracefully() {
        // 两次都是垃圾 → nothing()
        var extractor = new LlmExperienceExtractor(
                new FakeModelClient().thenReply("not json at all").thenReply("still not json"), mapper);
        var r = extractor.extract(req(ConfidenceSource.AGENT_INFERRED));
        assertFalse(r.isWorthSaving());
        assertTrue(r.getMemoryWrites().isEmpty());
        assertTrue(r.getSkillWrites().isEmpty());
    }

    @Test
    void retrySucceedsOnSecondCall() {
        var extractor = new LlmExperienceExtractor(
                new FakeModelClient().thenReply("garbage")
                        .thenReply("{\"worth_saving\":true,\"memory_writes\":[{\"kind\":\"preference\",\"content\":\"喜欢简洁\"}],\"skill_writes\":[]}"),
                mapper);
        var r = extractor.extract(req(ConfidenceSource.AGENT_INFERRED));
        assertTrue(r.isWorthSaving());
        assertEquals(1, r.getMemoryWrites().size());
    }

    @Test
    void usesLargeMaxTokensToAvoidSkillJsonTruncation() {
        // 回归：含完整 skill 计划的提炼 JSON 较长，maxTokens 过小会被截断导致技能静默丢失。
        var fake = new FakeModelClient().thenReply("{\"worth_saving\":false,\"memory_writes\":[],\"skill_writes\":[]}");
        new LlmExperienceExtractor(fake, mapper).extract(req(ConfidenceSource.AGENT_INFERRED));
        assertNotNull(fake.lastRequest);
        assertTrue(fake.lastRequest.getMaxTokens() >= 4000,
                "提炼调用应给足 token 预算，实际=" + fake.lastRequest.getMaxTokens());
    }

    @Test
    void emptyTranscriptShortCircuits() {
        var extractor = new LlmExperienceExtractor(new FakeModelClient(), mapper);
        var r = extractor.extract(ExperienceReviewRequest.builder().userId("u").sessionId("s").transcript("").build());
        assertFalse(r.isWorthSaving());
    }
}

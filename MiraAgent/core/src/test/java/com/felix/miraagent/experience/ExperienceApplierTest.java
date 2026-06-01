package com.felix.miraagent.experience;

import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryWriteRequest;
import com.felix.miraagent.memory.MemoryWriteResult;
import com.felix.miraagent.memory.SerializedMemoryWriter;
import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillCreateCommand;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.skill.SkillPatch;
import com.felix.miraagent.skill.SkillWriteResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceApplierTest {

    static class FakeMemoryWriter implements SerializedMemoryWriter {
        final List<MemoryWriteRequest> submitted = new ArrayList<>();
        @Override public MemoryWriteResult submit(MemoryWriteRequest r) {
            submitted.add(r);
            return MemoryWriteResult.builder().memoryId("m").success(true).build();
        }
        @Override public MemoryWriteResult archive(String u, String id) { return null; }
        @Override public void shutdown() { }
    }

    static class FakeSkillManager implements SkillManager {
        SkillCreateCommand created; SkillPatch patched;
        @Override public SkillWriteResult create(SkillCreateCommand c) {
            created = c; return SkillWriteResult.builder().skillId("s").success(true).build();
        }
        @Override public SkillWriteResult patch(SkillPatch p) {
            patched = p; return SkillWriteResult.builder().skillId(p.getSkillId()).success(true).build();
        }
        @Override public SkillWriteResult archive(String id) { return null; }
        @Override public List<SkillIndex> listActive() { return List.of(); }
        @Override public Optional<Skill> view(String id, String t, String s) { return Optional.empty(); }
        @Override public void recordUse(String id, String t, String s) { }
    }

    private ExperienceReviewRequest req() {
        return ExperienceReviewRequest.builder()
                .userId("u1").characterId("mira").sessionId("s1").sourceTraceId("trace-1")
                .transcript("...").build();
    }

    @Test
    void appliesMemoryAndSkillCreate() {
        var mem = new FakeMemoryWriter();
        var skills = new FakeSkillManager();
        var applier = new ExperienceApplier(mem, skills);

        var result = ExperienceReviewResult.builder()
                .worthSaving(true)
                .memoryWrite(MemoryWritePlan.builder().kind("preference").content("喜欢简洁")
                        .scope("global").confidence(0.6).sourceTraceId("trace-1").build())
                .skillWrite(SkillWritePlan.builder().op("create").name("复习计划").description("拆周计划")
                        .whenToUse("规划").step("问截止").toolSuggestion("todo").checklistItem("有量化").build())
                .build();

        var applied = applier.apply(result, req());
        assertEquals(1, applied.getMemoriesWritten());
        assertEquals(1, applied.getSkillsWritten());

        assertEquals(MemoryCategory.PREFERENCE, mem.submitted.get(0).getCategory());
        assertEquals("trace-1", mem.submitted.get(0).getSourceTraceId());
        assertNotNull(skills.created);
        assertEquals("复习计划", skills.created.getName());
        assertEquals("background_review", skills.created.getSource());
        assertTrue(skills.created.getBody().contains("执行步骤"));
        assertTrue(skills.created.getBody().contains("问截止"));
    }

    @Test
    void patchRoutesToTargetSkill() {
        var skills = new FakeSkillManager();
        var applier = new ExperienceApplier(new FakeMemoryWriter(), skills);
        var result = ExperienceReviewResult.builder().worthSaving(true)
                .skillWrite(SkillWritePlan.builder().op("patch").targetSkillId("code-review")
                        .description("更准的描述").step("额外一步").build())
                .build();
        applier.apply(result, req());
        assertNotNull(skills.patched);
        assertEquals("code-review", skills.patched.getSkillId());
        assertTrue(skills.patched.getAppendBody().contains("额外一步"));
    }

    @Test
    void notWorthSavingWritesNothing() {
        var mem = new FakeMemoryWriter();
        var skills = new FakeSkillManager();
        var applied = new ExperienceApplier(mem, skills).apply(ExperienceReviewResult.nothing(), req());
        assertEquals(0, applied.getMemoriesWritten());
        assertEquals(0, applied.getSkillsWritten());
        assertTrue(mem.submitted.isEmpty());
        assertNull(skills.created);
    }

    @Test
    void characterScopeMapsCharacterId() {
        var mem = new FakeMemoryWriter();
        var applier = new ExperienceApplier(mem, new FakeSkillManager());
        var result = ExperienceReviewResult.builder().worthSaving(true)
                .memoryWrite(MemoryWritePlan.builder().kind("relationship").content("昵称").scope("character").build())
                .build();
        applier.apply(result, req());
        assertEquals("mira", mem.submitted.get(0).getCharacterId());
    }
}

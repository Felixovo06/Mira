package com.felix.miraagent.skill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSkillManagerTest {

    /** 记录 writer 调用的假实现。 */
    static class FakeWriter implements SerializedSkillWriter {
        String lastOp;
        SkillCreateCommand lastCreate;
        SkillPatch lastPatch;
        String lastArchive;
        @Override public SkillWriteResult create(SkillCreateCommand c) {
            lastOp = "create"; lastCreate = c;
            return SkillWriteResult.builder().skillId("code-review").success(true).build();
        }
        @Override public SkillWriteResult patch(SkillPatch p) {
            lastOp = "patch"; lastPatch = p;
            return SkillWriteResult.builder().skillId(p.getSkillId()).success(true).build();
        }
        @Override public SkillWriteResult archive(String id) {
            lastOp = "archive"; lastArchive = id;
            return SkillWriteResult.builder().skillId(id).success(true).build();
        }
        @Override public void shutdown() { }
    }

    static class RecordingTracker implements SkillUsageTracker {
        String patched; String viewed; String used;
        @Override public Optional<SkillMetadata> record(SkillUsageEvent e) { return Optional.empty(); }
        @Override public Optional<SkillMetadata> recordView(String id, String t, String s) { viewed = id; return Optional.empty(); }
        @Override public Optional<SkillMetadata> recordUse(String id, String t, String s) { used = id; return Optional.empty(); }
        @Override public Optional<SkillMetadata> recordPatch(String id, String t, String n) { patched = id; return Optional.empty(); }
        @Override public Optional<SkillMetadata> setPinned(String id, boolean p) { return Optional.empty(); }
    }

    private SkillLoader loaderWithSkill(AtomicReference<Boolean> present) {
        return new SkillLoader() {
            @Override public List<SkillIndex> loadActiveIndex() {
                return List.of(SkillIndex.builder().skillId("code-review").status(SkillStatus.ACTIVE).build());
            }
            @Override public Optional<Skill> loadSkill(String id) {
                return present.get()
                        ? Optional.of(Skill.builder().metadata(SkillMetadata.builder().skillId(id).build())
                            .content(SkillContent.of(id, "d", "b")).build())
                        : Optional.empty();
            }
            @Override public Optional<SkillContent> loadContent(String id) { return Optional.empty(); }
            @Override public String loadResource(String id, String p) { return ""; }
            @Override public List<String> listResources(String id, String p) { return List.of(); }
        };
    }

    @Test
    void patchRecordsPatchStat() {
        var writer = new FakeWriter();
        var tracker = new RecordingTracker();
        var mgr = new DefaultSkillManager(writer, loaderWithSkill(new AtomicReference<>(true)), tracker);

        mgr.patch(SkillPatch.builder().skillId("code-review").newBody("x").note("n").build());
        assertEquals("patch", writer.lastOp);
        assertEquals("code-review", tracker.patched);
    }

    @Test
    void viewRecordsViewWhenPresent() {
        var tracker = new RecordingTracker();
        var present = new AtomicReference<>(true);
        var mgr = new DefaultSkillManager(new FakeWriter(), loaderWithSkill(present), tracker);

        assertTrue(mgr.view("code-review", "t", "s").isPresent());
        assertEquals("code-review", tracker.viewed);

        // 不存在则不记 view
        present.set(false);
        tracker.viewed = null;
        assertTrue(mgr.view("nope", "t", "s").isEmpty());
        assertNull(tracker.viewed);
    }

    @Test
    void createAndArchiveDelegateToWriter() {
        var writer = new FakeWriter();
        var mgr = new DefaultSkillManager(writer, loaderWithSkill(new AtomicReference<>(true)), new RecordingTracker());

        mgr.create(SkillCreateCommand.builder().name("Code Review").description("d").body("b").build());
        assertEquals("create", writer.lastOp);

        mgr.archive("code-review");
        assertEquals("archive", writer.lastOp);
        assertEquals("code-review", writer.lastArchive);
    }

    @Test
    void listActiveDelegatesToLoader() {
        var mgr = new DefaultSkillManager(new FakeWriter(), loaderWithSkill(new AtomicReference<>(true)), new RecordingTracker());
        assertEquals(1, mgr.listActive().size());
    }
}

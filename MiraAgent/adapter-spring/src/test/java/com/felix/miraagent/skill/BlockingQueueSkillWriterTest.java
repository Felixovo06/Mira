package com.felix.miraagent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BlockingQueueSkillWriterTest {

    private SkillFileStore store;
    private BlockingQueueSkillWriter writer;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        store = new SkillFileStore(tmp.toString(), new ObjectMapper().findAndRegisterModules());
        writer = new BlockingQueueSkillWriter(store, null, null, null);
    }

    @AfterEach
    void tearDown() {
        if (writer != null) {
            writer.shutdown();
        }
    }

    private SkillCreateCommand cmd(String name, String desc, String body) {
        return SkillCreateCommand.builder().name(name).description(desc).body(body).source("user").build();
    }

    @Test
    void createWritesNewSkill() {
        SkillWriteResult r = writer.create(cmd("Code Review", "系统化审查复杂改动", "## 步骤\n1. 读 diff"));
        assertTrue(r.isSuccess());
        assertEquals("code-review", r.getSkillId());
        var loaded = store.load("code-review").orElseThrow();
        assertEquals(SkillStatus.ACTIVE, loaded.getMetadata().getStatus());
        assertEquals(1, loaded.getMetadata().getVersion());
        assertTrue(loaded.getContent().getBody().contains("读 diff"));
    }

    @Test
    void createCollidingNameGetsSuffix() {
        writer.create(cmd("Review", "d1", "b1"));
        SkillWriteResult r2 = writer.create(cmd("Review", "d2", "b2"));
        assertEquals("review-2", r2.getSkillId());
    }

    @Test
    void createDeduplicatedBecomesPatch() {
        writer.create(cmd("Code Review", "系统化审查复杂改动", "## 步骤\n1. 读 diff"));
        // 去重器对任何 description 都判定与 code-review 重复
        var dedupWriter = new BlockingQueueSkillWriter(store, null,
                description -> Optional.of(new SkillDeduplicator.DuplicateMatch("code-review", 0.92)), null);
        try {
            SkillWriteResult r = dedupWriter.create(cmd("Review Diff", "审查 diff 的方法", "## 补充\n2. 看测试"));
            assertTrue(r.isSuccess());
            // 未新建，而是 patch 了 code-review
            assertEquals("code-review", r.getSkillId());
            assertTrue(store.loadMetadata("review-diff").isEmpty());
            var patched = store.load("code-review").orElseThrow();
            assertTrue(patched.getContent().getBody().contains("读 diff"));
            assertTrue(patched.getContent().getBody().contains("看测试")); // appendBody 生效
            assertEquals("审查 diff 的方法", patched.getMetadata().getDescription()); // 描述更新
        } finally {
            dedupWriter.shutdown();
        }
    }

    @Test
    void patchReplacesBody() {
        writer.create(cmd("Code Review", "d", "old body"));
        SkillWriteResult r = writer.patch(SkillPatch.builder()
                .skillId("code-review").newBody("new body").note("rewrite").build());
        assertTrue(r.isSuccess());
        assertEquals("new body", store.load("code-review").orElseThrow().getContent().getBody());
    }

    @Test
    void archiveFlipsStatus() {
        writer.create(cmd("Code Review", "d", "b"));
        assertTrue(writer.archive("code-review").isSuccess());
        assertEquals(SkillStatus.ARCHIVED, store.load("code-review").orElseThrow().getMetadata().getStatus());
    }

    @Test
    void patchMissingSkillFails() {
        assertFalse(writer.patch(SkillPatch.builder().skillId("nope").newBody("x").build()).isSuccess());
    }
}

package com.felix.miraagent.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillContent;
import com.felix.miraagent.skill.SkillCreateCommand;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.skill.SkillMetadata;
import com.felix.miraagent.skill.SkillPatch;
import com.felix.miraagent.skill.SkillStatus;
import com.felix.miraagent.skill.SkillWriteResult;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SkillToolHandlersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    static class FakeManager implements SkillManager {
        SkillCreateCommand created; SkillPatch patched; String archived; String viewed;
        @Override public SkillWriteResult create(SkillCreateCommand c) {
            created = c; return SkillWriteResult.builder().skillId("code-review").success(true).build();
        }
        @Override public SkillWriteResult patch(SkillPatch p) {
            patched = p; return SkillWriteResult.builder().skillId(p.getSkillId()).success(true).build();
        }
        @Override public SkillWriteResult archive(String id) {
            archived = id; return SkillWriteResult.builder().skillId(id).success(true).build();
        }
        @Override public List<SkillIndex> listActive() {
            return List.of(SkillIndex.builder().skillId("code-review").name("代码审查")
                    .description("系统化审查").status(SkillStatus.ACTIVE).build());
        }
        @Override public Optional<Skill> view(String id, String t, String s) {
            viewed = id;
            return Optional.of(Skill.builder().metadata(SkillMetadata.builder().skillId(id).build())
                    .content(SkillContent.of(id, "d", "完整步骤")).build());
        }
        @Override public void recordUse(String id, String t, String s) { }
    }

    private JsonNode json(String s) throws Exception { return mapper.readTree(s); }

    @Test
    void skillsListRendersIndex() {
        var h = new SkillsListToolHandler(new FakeManager());
        ToolExecutionResult r = h.execute("c1", mapper.createObjectNode());
        assertEquals(ToolStatus.SUCCESS, r.getStatus());
        assertTrue(r.getModelVisibleContent().contains("code-review"));
        assertTrue(r.getModelVisibleContent().contains("系统化审查"));
    }

    @Test
    void skillViewLoadsFullContentAndRecordsView() throws Exception {
        var mgr = new FakeManager();
        var h = new SkillViewToolHandler(mgr);
        ToolExecutionResult r = h.execute("c1", json("{\"skill_id\":\"code-review\"}"));
        assertEquals(ToolStatus.SUCCESS, r.getStatus());
        assertTrue(r.getModelVisibleContent().contains("完整步骤"));
        assertEquals("code-review", mgr.viewed);
    }

    @Test
    void skillViewMissingIdErrors() {
        var r = new SkillViewToolHandler(new FakeManager()).execute("c1", mapper.createObjectNode());
        assertEquals(ToolStatus.ERROR, r.getStatus());
    }

    @Test
    void skillManageCreateRoutes() throws Exception {
        var mgr = new FakeManager();
        var h = new SkillManageToolHandler(mgr);
        ToolExecutionResult r = h.execute("c1",
                json("{\"op\":\"create\",\"name\":\"Code Review\",\"description\":\"审查\",\"body\":\"步骤\",\"tags\":[\"review\"]}"));
        assertEquals(ToolStatus.SUCCESS, r.getStatus());
        assertNotNull(mgr.created);
        assertEquals("Code Review", mgr.created.getName());
        assertEquals(List.of("review"), mgr.created.getTags());
        assertEquals("housekeeping", mgr.created.getSource());
    }

    @Test
    void skillManageCreateRequiresNameAndDescription() throws Exception {
        var r = new SkillManageToolHandler(new FakeManager())
                .execute("c1", json("{\"op\":\"create\",\"name\":\"x\"}"));
        assertEquals(ToolStatus.ERROR, r.getStatus());
    }

    @Test
    void skillManagePatchAndArchiveRoute() throws Exception {
        var mgr = new FakeManager();
        var h = new SkillManageToolHandler(mgr);
        assertEquals(ToolStatus.SUCCESS,
                h.execute("c1", json("{\"op\":\"patch\",\"skill_id\":\"code-review\",\"note\":\"fix\"}")).getStatus());
        assertEquals("code-review", mgr.patched.getSkillId());

        assertEquals(ToolStatus.SUCCESS,
                h.execute("c2", json("{\"op\":\"archive\",\"skill_id\":\"code-review\"}")).getStatus());
        assertEquals("code-review", mgr.archived);
    }

    @Test
    void skillManageInvalidOpErrors() throws Exception {
        var r = new SkillManageToolHandler(new FakeManager()).execute("c1", json("{\"op\":\"frobnicate\"}"));
        assertEquals(ToolStatus.ERROR, r.getStatus());
    }
}

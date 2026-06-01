package com.felix.miraagent.api;

import com.felix.miraagent.api.controller.SkillManagementController;
import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillContent;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillLoader;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.skill.SkillMetadata;
import com.felix.miraagent.skill.SkillStatus;
import com.felix.miraagent.skill.SkillUsageTracker;
import com.felix.miraagent.skill.SkillWriteResult;
import com.felix.miraagent.skill.curator.Curator;
import com.felix.miraagent.skill.curator.CuratorReport;
import com.felix.miraagent.skill.curator.SkillSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SkillManagementControllerTest {

    private SkillLoader loaderWith(boolean hasSkill) {
        return new SkillLoader() {
            @Override public List<SkillIndex> loadActiveIndex() {
                return List.of(SkillIndex.builder().skillId("code-review").name("审查").status(SkillStatus.ACTIVE).build());
            }
            @Override public Optional<Skill> loadSkill(String id) {
                return hasSkill ? Optional.of(Skill.builder()
                        .metadata(SkillMetadata.builder().skillId(id).build())
                        .content(SkillContent.of(id, "d", "b")).build()) : Optional.empty();
            }
            @Override public Optional<SkillContent> loadContent(String id) { return Optional.empty(); }
            @Override public String loadResource(String id, String p) { return ""; }
            @Override public List<String> listResources(String id, String p) { return List.of(); }
        };
    }

    @Test
    void listReturnsActiveIndex() {
        var c = new SkillManagementController(Optional.of(loaderWith(true)), Optional.empty(), Optional.empty(), Optional.empty());
        var resp = c.list();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    void viewFoundAndNotFound() {
        var found = new SkillManagementController(Optional.of(loaderWith(true)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(HttpStatus.OK, found.view("code-review").getStatusCode());

        var missing = new SkillManagementController(Optional.of(loaderWith(false)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, missing.view("nope").getStatusCode());
    }

    @Test
    void archiveDelegatesToManager() {
        var archived = new boolean[]{false};
        SkillManager mgr = fakeManager(id -> { archived[0] = true; return true; });
        var c = new SkillManagementController(Optional.of(loaderWith(true)), Optional.of(mgr), Optional.empty(), Optional.empty());
        assertEquals(HttpStatus.NO_CONTENT, c.archive("code-review").getStatusCode());
        assertTrue(archived[0]);
    }

    @Test
    void pinDelegatesToTracker() {
        var pinned = new boolean[]{false};
        SkillUsageTracker tracker = new SkillUsageTracker() {
            @Override public Optional<SkillMetadata> record(com.felix.miraagent.skill.SkillUsageEvent e) { return Optional.empty(); }
            @Override public Optional<SkillMetadata> recordView(String id, String t, String s) { return Optional.empty(); }
            @Override public Optional<SkillMetadata> recordUse(String id, String t, String s) { return Optional.empty(); }
            @Override public Optional<SkillMetadata> recordPatch(String id, String t, String n) { return Optional.empty(); }
            @Override public Optional<SkillMetadata> setPinned(String id, boolean p) {
                pinned[0] = p; return Optional.of(SkillMetadata.builder().skillId(id).pinned(p).build());
            }
        };
        var c = new SkillManagementController(Optional.of(loaderWith(true)), Optional.empty(), Optional.of(tracker), Optional.empty());
        assertEquals(HttpStatus.NO_CONTENT, c.pin("code-review", true).getStatusCode());
        assertTrue(pinned[0]);
    }

    @Test
    void curatorReportReturned() {
        Curator curator = () -> CuratorReport.builder()
                .narrow(SkillSuggestion.builder().skillId("rare").reason("use<3").build())
                .build();
        var c = new SkillManagementController(Optional.of(loaderWith(true)), Optional.empty(), Optional.empty(), Optional.of(curator));
        var resp = c.curatorReport();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getNarrow().size());
    }

    @Test
    void curatorReportEmptyWhenAbsent() {
        var c = new SkillManagementController(Optional.of(loaderWith(true)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(HttpStatus.OK, c.curatorReport().getStatusCode());
        assertTrue(c.curatorReport().getBody().getNarrow().isEmpty());
    }

    private interface ArchiveFn { boolean archive(String id); }

    private SkillManager fakeManager(ArchiveFn archiveFn) {
        return new SkillManager() {
            @Override public SkillWriteResult create(com.felix.miraagent.skill.SkillCreateCommand c) { return null; }
            @Override public SkillWriteResult patch(com.felix.miraagent.skill.SkillPatch p) { return null; }
            @Override public SkillWriteResult archive(String id) {
                return SkillWriteResult.builder().skillId(id).success(archiveFn.archive(id)).build();
            }
            @Override public List<SkillIndex> listActive() { return List.of(); }
            @Override public Optional<Skill> view(String id, String t, String s) { return Optional.empty(); }
            @Override public void recordUse(String id, String t, String s) { }
        };
    }
}

package com.felix.miraagent.api.controller;

import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillLoader;
import com.felix.miraagent.skill.SkillManager;
import com.felix.miraagent.skill.SkillUsageTracker;
import com.felix.miraagent.skill.curator.Curator;
import com.felix.miraagent.skill.curator.CuratorReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Skill 管理 REST（docs/03 任务7）：list / view / archive / curator-report / pin。
 * Curator 只给建议，归档/合并需用户在此显式触发；前端 UI 留到 P3。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillManagementController {

    private final Optional<SkillLoader> skillLoader;
    private final Optional<SkillManager> skillManager;
    private final Optional<SkillUsageTracker> usageTracker;
    private final Optional<Curator> curator;

    public SkillManagementController(Optional<SkillLoader> skillLoader,
                                     Optional<SkillManager> skillManager,
                                     Optional<SkillUsageTracker> usageTracker,
                                     Optional<Curator> curator) {
        this.skillLoader = skillLoader;
        this.skillManager = skillManager;
        this.usageTracker = usageTracker;
        this.curator = curator;
    }

    @GetMapping
    public ResponseEntity<List<SkillIndex>> list() {
        return ResponseEntity.ok(skillLoader.map(SkillLoader::loadActiveIndex).orElse(List.of()));
    }

    @GetMapping("/{skillId}")
    public ResponseEntity<Skill> view(@PathVariable String skillId) {
        // 管理视图：不记 view 统计，直接读 store
        return skillLoader.flatMap(l -> l.loadSkill(skillId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{skillId}/archive")
    public ResponseEntity<Void> archive(@PathVariable String skillId) {
        if (skillManager.isEmpty()) {
            return ResponseEntity.status(503).build();
        }
        var result = skillManager.get().archive(skillId);
        return result.isSuccess() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{skillId}/pin")
    public ResponseEntity<Void> pin(@PathVariable String skillId,
                                    @RequestParam(defaultValue = "true") boolean pinned) {
        if (usageTracker.isEmpty()) {
            return ResponseEntity.status(503).build();
        }
        return usageTracker.get().setPinned(skillId, pinned).isPresent()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/curator-report")
    public ResponseEntity<CuratorReport> curatorReport() {
        return curator.map(c -> ResponseEntity.ok(c.analyze()))
                .orElseGet(() -> ResponseEntity.ok(CuratorReport.builder().build()));
    }
}

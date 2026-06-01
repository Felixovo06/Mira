package com.felix.miraagent.skill;

import java.util.List;
import java.util.Optional;

/**
 * SkillManager 默认实现：写入委托 SerializedSkillWriter（串行化 + 去重），
 * patch 成功后记 patch 统计，view 记 view 统计。读取走 SkillLoader。
 */
public class DefaultSkillManager implements SkillManager {

    private final SerializedSkillWriter writer;
    private final SkillLoader loader;
    private final SkillUsageTracker usageTracker; // nullable

    public DefaultSkillManager(SerializedSkillWriter writer, SkillLoader loader, SkillUsageTracker usageTracker) {
        this.writer = writer;
        this.loader = loader;
        this.usageTracker = usageTracker;
    }

    @Override
    public SkillWriteResult create(SkillCreateCommand command) {
        return writer.create(command);
    }

    @Override
    public SkillWriteResult patch(SkillPatch patch) {
        SkillWriteResult result = writer.patch(patch);
        if (result.isSuccess() && usageTracker != null) {
            usageTracker.recordPatch(result.getSkillId(), patch.getSourceTraceId(), patch.getNote());
        }
        return result;
    }

    @Override
    public SkillWriteResult archive(String skillId) {
        return writer.archive(skillId);
    }

    @Override
    public List<SkillIndex> listActive() {
        return loader.loadActiveIndex();
    }

    @Override
    public Optional<Skill> view(String skillId, String sourceTraceId, String sourceSessionId) {
        Optional<Skill> skill = loader.loadSkill(skillId);
        if (skill.isPresent() && usageTracker != null) {
            usageTracker.recordView(skillId, sourceTraceId, sourceSessionId);
        }
        return skill;
    }

    @Override
    public void recordUse(String skillId, String sourceTraceId, String sourceSessionId) {
        if (usageTracker != null) {
            usageTracker.recordUse(skillId, sourceTraceId, sourceSessionId);
        }
    }
}

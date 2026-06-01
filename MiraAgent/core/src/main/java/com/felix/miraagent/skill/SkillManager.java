package com.felix.miraagent.skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 应用服务：create/patch/archive 的策略编排 + list/view 读取。
 * 工具（skill_manage/skills_list/skill_view）与经验提炼（step5）/后台 review（step6）均调用本接口。
 * 写入统一委托 SerializedSkillWriter 串行化；patch/view 顺带更新使用统计。
 */
public interface SkillManager {

    /** 创建（内部去重，命中则自动转 patch）。 */
    SkillWriteResult create(SkillCreateCommand command);

    /** 修补已有 skill，并记一次 patch 统计（version+1）。 */
    SkillWriteResult patch(SkillPatch patch);

    /** 归档（逻辑删除，可恢复）。 */
    SkillWriteResult archive(String skillId);

    /** 当前 Active skill 索引。 */
    List<SkillIndex> listActive();

    /** 加载完整 skill 并记一次 view 统计。 */
    Optional<Skill> view(String skillId, String sourceTraceId, String sourceSessionId);

    /** 记一次 use 统计（skill 被实际用于完成任务）。 */
    void recordUse(String skillId, String sourceTraceId, String sourceSessionId);
}

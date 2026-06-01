package com.felix.miraagent.skill;

/**
 * Skill 写入串行化端口（镜像 SerializedMemoryWriter）。所有 create/patch/archive 走单 writer 串行，
 * 避免主 Loop housekeeping 与 Background Review 并发改同一 skill 损坏文件。
 * create 内部先去重：相似度 > 0.85 强制转为 patch。
 */
public interface SerializedSkillWriter {

    SkillWriteResult create(SkillCreateCommand command);

    SkillWriteResult patch(SkillPatch patch);

    SkillWriteResult archive(String skillId);

    void shutdown();
}

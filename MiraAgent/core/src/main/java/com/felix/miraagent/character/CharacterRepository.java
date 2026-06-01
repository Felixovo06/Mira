package com.felix.miraagent.character;

import java.util.List;
import java.util.Optional;

/**
 * 角色卡仓库端口。按 id 加载角色卡、列出全部、导入新卡。
 * core 只定义端口；文件/DB 实现在 adapter 层。无实现时 AgentRuntime 回退到默认/空壳 profile。
 */
public interface CharacterRepository {

    Optional<CharacterProfile> findById(String characterId);

    List<CharacterProfile> listAll();

    /** 导入/保存一张角色卡（以 id 为主键，存在则覆盖）。 */
    CharacterProfile save(CharacterProfile profile);
}

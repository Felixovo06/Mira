package com.felix.miraagent.style;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * 一条「世界书条目」：插拔式的全局风格/世界设定单元，可单独开关。
 * 多条目组成全局世界书，所有启用条目按 {@code order} 拼接，作为最稳定的内容
 * 注入到 stableSystemPrompt 最前端，利于 prefix caching。
 *
 * <p>区别于角色卡（{@code CharacterProfile} 定义"我是谁"），世界书条目定义
 * "世界是什么样、所有角色统一怎么说话"，凌驾于单个角色之上。一条条目内仍保留
 * 结构化字段（世界设定 / 语气 / 规则），便于分块表达。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StyleConstraint {

    /** 条目唯一 id（持久化主键，新建时由存储层生成）。 */
    String id;

    /** 条目名称（展示与拼接时的小标题，如"现实日常世界""毒舌语气"）。 */
    String name;

    /** 排序权重，升序拼接；同序按名称兜底。 */
    @Builder.Default
    int order = 0;

    /** 条目开关；为 false 时不注入此条目（插拔式核心）。 */
    @Builder.Default
    boolean enabled = true;

    /** 世界设定：大局背景、时空规则、世界观，约束所有角色所处的环境。 */
    String worldSetting;

    /** 回复语气方式：口吻、人称、节奏，约束所有角色统一的说话方式。 */
    String tone;

    /** 硬性风格规则（禁用词、格式、长度等），逐条列出。 */
    @Singular
    List<String> styleRules;
}

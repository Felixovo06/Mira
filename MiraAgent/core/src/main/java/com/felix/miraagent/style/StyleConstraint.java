package com.felix.miraagent.style;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * 全局风格约束：一份配置对所有角色生效，规定「世界设定」与「回复语气/风格」。
 * 作为最稳定的内容注入到 stableSystemPrompt 的最前端，利于 prefix caching。
 *
 * <p>区别于角色卡（{@code CharacterProfile} 定义"我是谁"），风格约束定义
 * "世界是什么样、所有角色统一怎么说话"，是凌驾于单个角色之上的大局框架。
 */
@Value
@Builder
@Jacksonized
public class StyleConstraint {

    /** 内容级开关；为 false 时不注入此份配置。 */
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

package com.felix.miraagent.style;

import java.util.Optional;

/**
 * 全局风格约束的来源。单一全局配置，故只需 {@link #get()}。
 * 返回 empty 表示未配置或被禁用，PromptBuilder 应跳过注入。
 */
public interface StyleConstraintProvider {

    Optional<StyleConstraint> get();
}

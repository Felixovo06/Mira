package com.felix.miraagent.style;

import java.util.List;

/**
 * 世界书的读侧来源：返回当前生效的条目集合。
 * 插拔式语义——只有 {@code enabled} 的条目生效，按 {@code order} 升序排列。
 * 返回空列表表示无注入（子系统关闭或没有启用条目），PromptBuilder 应跳过注入。
 */
public interface StyleConstraintProvider {

    /** 当前生效（启用且按 order 升序）的世界书条目；空列表表示不注入。 */
    List<StyleConstraint> activeEntries();
}

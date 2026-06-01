package com.felix.miraagent.style;

import java.util.Optional;

/**
 * 可读写的风格约束存储。读侧继承 {@link StyleConstraintProvider}（供 PromptBuilder 注入），
 * 额外提供编辑用的 {@link #current()} 与持久化的 {@link #save}。
 */
public interface StyleConstraintStore extends StyleConstraintProvider {

    /** 当前生效内容（不受子系统启用开关 gate），供前端读取后编辑。 */
    Optional<StyleConstraint> current();

    /** 保存并立即生效（覆盖外部文件，无需重启）。 */
    StyleConstraint save(StyleConstraint constraint);
}

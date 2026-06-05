package com.felix.miraagent.style;

import java.util.List;
import java.util.Optional;

/**
 * 可读写的世界书存储。读侧继承 {@link StyleConstraintProvider}（供 PromptBuilder 注入），
 * 额外提供编辑用的列表/增删改/开关。所有写操作持久化并立即生效，无需重启。
 */
public interface StyleConstraintStore extends StyleConstraintProvider {

    /** 全部条目（含禁用，按 order 升序），供前端列表读取后编辑。 */
    List<StyleConstraint> list();

    /** 按 id 读取单条。 */
    Optional<StyleConstraint> get(String id);

    /**
     * 新建或更新一条条目：id 为空视为新建（生成 id、order 追加到末尾），
     * 否则按 id 覆盖既有条目。返回保存后的条目。
     */
    StyleConstraint upsert(StyleConstraint entry);

    /** 删除一条；返回是否确有删除。 */
    boolean delete(String id);

    /** 开关一条；条目不存在返回 empty。 */
    Optional<StyleConstraint> setEnabled(String id, boolean enabled);
}

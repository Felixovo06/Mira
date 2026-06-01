package com.felix.miraagent.skill;

import java.util.Optional;

/** 无 embedding/无 DB 时的去重降级：从不判重，允许创建。 */
public class NoOpSkillDeduplicator implements SkillDeduplicator {
    @Override
    public Optional<DuplicateMatch> findDuplicate(String description) {
        return Optional.empty();
    }
}

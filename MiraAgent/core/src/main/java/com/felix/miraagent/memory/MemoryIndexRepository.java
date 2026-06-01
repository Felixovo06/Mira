package com.felix.miraagent.memory;

import java.util.List;
import java.util.Optional;

public interface MemoryIndexRepository {

    void save(MemoryIndex index);

    void archive(String userId, String memoryId);

    /**
     * Find non-archived memory indexes for a user.
     * characterId and category are optional filters; pass null to skip filtering.
     */
    List<MemoryIndex> findByUser(String userId, String characterId, String category);

    Optional<MemoryIndex> findById(String id);

    /** Delete all indexes for a user (used during full rebuild). */
    void deleteAll(String userId);
}

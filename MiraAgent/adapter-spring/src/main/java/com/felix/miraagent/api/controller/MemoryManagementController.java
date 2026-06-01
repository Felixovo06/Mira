package com.felix.miraagent.api.controller;

import com.felix.miraagent.memory.MemoryIndex;
import com.felix.miraagent.memory.MemoryIndexRepository;
import com.felix.miraagent.memory.MemoryStore;
import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryWritePolicy;
import com.felix.miraagent.memory.SerializedMemoryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/memory")
public class MemoryManagementController {

    private static final Logger log = LoggerFactory.getLogger(MemoryManagementController.class);

    private final Optional<MemoryIndexRepository> indexRepository;
    private final Optional<MemoryStore> memoryStore;
    private final Optional<SerializedMemoryWriter> memoryWriter;
    private final Optional<MemoryWritePolicy> memoryWritePolicy;

    public MemoryManagementController(Optional<MemoryIndexRepository> indexRepository,
                                      Optional<MemoryStore> memoryStore,
                                      Optional<SerializedMemoryWriter> memoryWriter,
                                      Optional<MemoryWritePolicy> memoryWritePolicy) {
        this.indexRepository = indexRepository;
        this.memoryStore = memoryStore;
        this.memoryWriter = memoryWriter;
        this.memoryWritePolicy = memoryWritePolicy;
    }

    @GetMapping
    public ResponseEntity<List<MemoryIndex>> list(
            @RequestParam String userId,
            @RequestParam(required = false) String characterId,
            @RequestParam(required = false) String category) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<MemoryIndex> result = indexRepository
                .map(r -> r.findByUser(userId, characterId, category))
                .orElse(List.of());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> archive(
            @PathVariable String memoryId,
            @RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (memoryWriter.isPresent()) {
            memoryWriter.get().archive(userId, memoryId);
        } else {
            indexRepository.ifPresent(r -> r.archive(userId, memoryId));
            memoryStore.ifPresent(s -> s.archive(userId, memoryId));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ban-category")
    public ResponseEntity<Map<String, String>> banCategory(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String category = body.get("category");
        if (userId == null || userId.isBlank() || category == null || category.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and category are required"));
        }
        try {
            MemoryCategory memoryCategory = MemoryCategory.valueOf(category.trim().toUpperCase());
            memoryWritePolicy.ifPresent(policy -> policy.banCategory(userId, memoryCategory));
            log.info("ban-category recorded: userId={}, category={}", userId, memoryCategory);
            return ResponseEntity.ok(Map.of("message", "ban recorded", "category", memoryCategory.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category: " + category));
        }
    }
}

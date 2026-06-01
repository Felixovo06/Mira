package com.felix.miraagent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Skill 写入单 writer（镜像 BlockingQueueMemoryWriter）。单条虚拟线程串行处理 create/patch/archive，
 * 保证去重+创建原子、并发改同一 skill 不损坏文件。索引与 embedding 顺带更新。
 */
public class BlockingQueueSkillWriter implements SerializedSkillWriter {

    private static final Logger log = LoggerFactory.getLogger(BlockingQueueSkillWriter.class);
    private static final Runnable POISON_PILL = () -> {};

    private final SkillStore skillStore;
    private final SkillIndexRepository indexRepository;   // nullable
    private final SkillDeduplicator deduplicator;         // nullable → 不去重
    private final SkillEmbeddingIndexer embeddingIndexer; // nullable
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;

    public BlockingQueueSkillWriter(SkillStore skillStore,
                                    SkillIndexRepository indexRepository,
                                    SkillDeduplicator deduplicator,
                                    SkillEmbeddingIndexer embeddingIndexer) {
        this.skillStore = skillStore;
        this.indexRepository = indexRepository;
        this.deduplicator = deduplicator;
        this.embeddingIndexer = embeddingIndexer;
        this.workerThread = Thread.ofVirtual().name("skill-writer").start(this::processQueue);
    }

    @Override
    public SkillWriteResult create(SkillCreateCommand command) {
        return run(() -> doCreate(command));
    }

    @Override
    public SkillWriteResult patch(SkillPatch patch) {
        return run(() -> doPatch(patch));
    }

    @Override
    public SkillWriteResult archive(String skillId) {
        return run(() -> doArchive(skillId));
    }

    @Override
    public void shutdown() {
        queue.add(POISON_PILL);
        try {
            workerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SkillWriteResult run(java.util.function.Supplier<SkillWriteResult> task) {
        CompletableFuture<SkillWriteResult> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.join();
    }

    // ----- 以下方法都只在单一 worker 线程上执行 -----

    private SkillWriteResult doCreate(SkillCreateCommand cmd) {
        // 去重：相似度 > 0.85 强制转 patch
        if (deduplicator != null && cmd.getDescription() != null && !cmd.getDescription().isBlank()) {
            Optional<SkillDeduplicator.DuplicateMatch> dup = deduplicator.findDuplicate(cmd.getDescription());
            if (dup.isPresent()) {
                String existingId = dup.get().getSkillId();
                log.info("Skill create deduplicated -> patch existing {} (sim={})", existingId, dup.get().getSimilarity());
                return doPatch(SkillPatch.builder()
                        .skillId(existingId)
                        .newDescription(cmd.getDescription())
                        .appendBody(cmd.getBody())
                        .note("merged from create (similarity " + String.format("%.2f", dup.get().getSimilarity()) + ")")
                        .sourceTraceId(cmd.getSourceTraceId())
                        .sourceSessionId(cmd.getSourceSessionId())
                        .build());
            }
        }

        String skillId = resolveSkillId(cmd);
        Instant now = Instant.now();
        SkillMetadata metadata = SkillMetadata.builder()
                .skillId(skillId)
                .name(cmd.getName())
                .description(cmd.getDescription())
                .status(SkillStatus.ACTIVE)
                .source(cmd.getSource())
                .sourceTraceId(cmd.getSourceTraceId())
                .sourceSessionId(cmd.getSourceSessionId())
                .version(1)
                .tags(cmd.getTags())
                .pinned(cmd.isPinned())
                .createdAt(now)
                .updatedAt(now)
                .build();
        SkillContent content = SkillContent.of(cmd.getName(), cmd.getDescription(), cmd.getBody());
        SkillWriteResult result = skillStore.write(Skill.builder().metadata(metadata).content(content).build());
        if (result.isSuccess()) {
            updateIndex(metadata, result.getSourceUri());
            indexEmbedding(skillId, cmd.getDescription());
        }
        return result;
    }

    private SkillWriteResult doPatch(SkillPatch patch) {
        Optional<Skill> existing = skillStore.load(patch.getSkillId());
        if (existing.isEmpty()) {
            return SkillWriteResult.builder().skillId(patch.getSkillId())
                    .success(false).error("skill not found").build();
        }
        SkillMetadata meta = existing.get().getMetadata();
        String currentBody = existing.get().getContent() != null ? existing.get().getContent().getBody() : "";
        String newDescription = patch.getNewDescription() != null ? patch.getNewDescription() : meta.getDescription();
        String newBody;
        if (patch.getNewBody() != null) {
            newBody = patch.getNewBody();
        } else if (patch.getAppendBody() != null && !patch.getAppendBody().isBlank()) {
            newBody = (currentBody == null || currentBody.isBlank())
                    ? patch.getAppendBody().trim()
                    : currentBody + "\n\n" + patch.getAppendBody().trim();
        } else {
            newBody = currentBody;
        }

        Instant now = Instant.now();
        // 保留计数/version，version 的递增交由 SkillManager.recordPatch 统一处理
        SkillMetadata updated = meta.toBuilder()
                .description(newDescription)
                .status(SkillStatus.ACTIVE)
                .updatedAt(now)
                .build();
        SkillContent content = SkillContent.of(meta.getName(), newDescription, newBody);
        SkillWriteResult result = skillStore.write(Skill.builder().metadata(updated).content(content).build());
        if (result.isSuccess()) {
            updateIndex(updated, result.getSourceUri());
            indexEmbedding(patch.getSkillId(), newDescription);
        }
        return result;
    }

    private SkillWriteResult doArchive(String skillId) {
        SkillWriteResult result = skillStore.archive(skillId);
        if (result.isSuccess() && indexRepository != null) {
            try {
                indexRepository.archive(skillId);
            } catch (Exception e) {
                log.warn("Failed to archive skill index for {}", skillId, e);
            }
        }
        return result;
    }

    private void updateIndex(SkillMetadata metadata, String sourceUri) {
        if (indexRepository == null) {
            return;
        }
        try {
            SkillIndex index = SkillIndex.fromMetadata(metadata);
            if (sourceUri != null) {
                index = index.toBuilder().sourceUri(sourceUri).build();
            }
            indexRepository.save(index);
        } catch (Exception e) {
            log.warn("Failed to update skill index for {}", metadata.getSkillId(), e);
        }
    }

    private void indexEmbedding(String skillId, String description) {
        if (embeddingIndexer != null && description != null && !description.isBlank()) {
            embeddingIndexer.indexAsync(skillId, description);
        }
    }

    private String resolveSkillId(SkillCreateCommand cmd) {
        if (cmd.getSkillId() != null && !cmd.getSkillId().isBlank()) {
            return cmd.getSkillId();
        }
        String base = slugify(cmd.getName());
        if (skillStore.loadMetadata(base).isEmpty()) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (skillStore.loadMetadata(candidate).isEmpty()) {
                return candidate;
            }
        }
        return base + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "skill";
        }
        String slug = name.trim().toLowerCase()
                .replaceAll("[\\s\\p{Punct}]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "skill" : slug;
    }

    private void processQueue() {
        while (true) {
            try {
                Runnable task = queue.take();
                if (task == POISON_PILL) {
                    break;
                }
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

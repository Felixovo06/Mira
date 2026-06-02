package com.felix.miraagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

public class BlockingQueueMemoryWriter implements SerializedMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(BlockingQueueMemoryWriter.class);
    private static final Runnable POISON_PILL = () -> {};

    /** 默认阈值：>= 视为同一事实重复（仅强化、不新增卡片）。pg_trgm similarity，0-1。 */
    private static final double DEFAULT_EXACT_DUP_THRESHOLD = 0.82;
    /** 默认阈值：>= 视为近似（更新旧卡片内容/置信度，不新增卡片）。 */
    private static final double DEFAULT_NEAR_DUP_THRESHOLD = 0.6;
    /** 同一事实被再次确认时，置信度强化增量（上限 100）。 */
    private static final int CONFIDENCE_REINFORCE = 5;

    private final MemoryStore memoryStore;
    private final MemoryIndexRepository indexRepository;
    private final AsyncEmbeddingIndexer embeddingIndexer;
    private final MemoryWritePolicy memoryWritePolicy;
    /** >= 此相似度视为完全重复，跳过新增、仅强化既有；<=0 关闭去重。 */
    private final double exactDupThreshold;
    /** [near, exact) 区间视为近似重复，更新既有卡片。 */
    private final double nearDupThreshold;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;

    public BlockingQueueMemoryWriter(MemoryStore memoryStore) {
        this(memoryStore, null, null, null);
    }

    public BlockingQueueMemoryWriter(MemoryStore memoryStore,
                                     MemoryIndexRepository indexRepository,
                                     AsyncEmbeddingIndexer embeddingIndexer) {
        this(memoryStore, indexRepository, embeddingIndexer, null);
    }

    public BlockingQueueMemoryWriter(MemoryStore memoryStore,
                                     MemoryIndexRepository indexRepository,
                                     AsyncEmbeddingIndexer embeddingIndexer,
                                     MemoryWritePolicy memoryWritePolicy) {
        this(memoryStore, indexRepository, embeddingIndexer, memoryWritePolicy,
                DEFAULT_NEAR_DUP_THRESHOLD, DEFAULT_EXACT_DUP_THRESHOLD);
    }

    public BlockingQueueMemoryWriter(MemoryStore memoryStore,
                                     MemoryIndexRepository indexRepository,
                                     AsyncEmbeddingIndexer embeddingIndexer,
                                     MemoryWritePolicy memoryWritePolicy,
                                     double nearDupThreshold,
                                     double exactDupThreshold) {
        this.memoryStore = memoryStore;
        this.indexRepository = indexRepository;
        this.embeddingIndexer = embeddingIndexer;
        this.memoryWritePolicy = memoryWritePolicy;
        this.nearDupThreshold = nearDupThreshold;
        this.exactDupThreshold = exactDupThreshold;
        this.workerThread = Thread.ofVirtual().name("memory-writer").start(this::processQueue);
    }

    @Override
    public MemoryWriteResult submit(MemoryWriteRequest request) {
        CompletableFuture<MemoryWriteResult> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                if (memoryWritePolicy != null && !memoryWritePolicy.isAllowed(request.getUserId(), request.getCategory())) {
                    future.complete(MemoryWriteResult.builder()
                            .memoryId(request.getMemoryId())
                            .success(false)
                            .error("Memory category is banned: " + request.getCategory())
                            .build());
                    return;
                }
                MemoryWriteResult deduped = tryDeduplicate(request);
                if (deduped != null) {
                    future.complete(deduped);
                    return;
                }
                MemoryWriteResult result = memoryStore.write(request);
                if (result.isSuccess()) {
                    try {
                        indexWrite(request, result);
                    } catch (Exception e) {
                        log.warn("Failed to update memory index for memory {}", result.getMemoryId(), e);
                    }
                }
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.join();
    }

    @Override
    public MemoryWriteResult archive(String userId, String memoryId) {
        CompletableFuture<MemoryWriteResult> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                MemoryWriteResult result = memoryStore.archive(userId, memoryId);
                if (indexRepository != null && result.isSuccess()) {
                    try {
                        indexRepository.archive(userId, memoryId);
                    } catch (Exception e) {
                        log.warn("Failed to archive memory index for memory {}", memoryId, e);
                    }
                }
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.join();
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

    /**
     * 写入前去重：在同 user + characterId + category 范围内查最相似的既有记忆。
     * <ul>
     *   <li>相似度 >= exactDupThreshold：同一事实重复 —— 不新增卡片、不追加文件，仅强化既有置信度并刷新 updatedAt；</li>
     *   <li>[nearDupThreshold, exact)：近似 —— 更新既有卡片内容/预览/置信度，不新增；</li>
     *   <li>低于 near：判为新事实，返回 null 走正常写入。</li>
     * </ul>
     * 去重以 DB 索引为准、不回改 .md（文件即去重后的集合，重建索引保持一致）。
     * 去重查询本身失败绝不能阻断写入：异常时返回 null 退化为正常写入。
     */
    private MemoryWriteResult tryDeduplicate(MemoryWriteRequest request) {
        if (indexRepository == null || exactDupThreshold <= 0
                || request.isArchived()
                || request.getContent() == null || request.getContent().isBlank()) {
            return null;
        }
        java.util.Optional<SimilarMemory> match;
        try {
            match = indexRepository.findMostSimilar(
                    request.getUserId(), request.getCharacterId(), request.getCategory(),
                    preview(request.getContent()), nearDupThreshold);
        } catch (Exception e) {
            log.warn("Dedup lookup failed, fall back to normal write for user {}", request.getUserId(), e);
            return null;
        }
        if (match.isEmpty()) {
            return null;
        }
        SimilarMemory hit = match.get();
        MemoryIndex existing = hit.getMemory();
        boolean exact = hit.getSimilarity() >= exactDupThreshold;

        MemoryIndex.MemoryIndexBuilder updated = existing.toBuilder()
                .confidence(Math.min(100, exact
                        ? existing.getConfidence() + CONFIDENCE_REINFORCE
                        : Math.max(existing.getConfidence(), request.getConfidence())));
        if (!exact) {
            // 近似：以更新的措辞替换预览与检索词
            updated.contentPreview(preview(request.getContent()))
                    .retrievalTerms(tokenize(request.getContent()));
        }
        try {
            indexRepository.save(updated.build());
        } catch (Exception e) {
            log.warn("Dedup merge failed, fall back to normal write for user {}", request.getUserId(), e);
            return null;
        }
        log.debug("Deduplicated memory write into {} ({}={})", existing.getId(),
                exact ? "exact" : "near", String.format("%.2f", hit.getSimilarity()));
        return MemoryWriteResult.builder()
                .memoryId(existing.getId())
                .filePath(existing.getSourceUri())
                .success(true)
                .deduplicated(true)
                .build();
    }

    private void indexWrite(MemoryWriteRequest request, MemoryWriteResult result) {
        if (indexRepository == null) {
            return;
        }
        MemoryIndex index = MemoryIndex.builder()
                .id(result.getMemoryId())
                .userId(request.getUserId())
                .characterId(request.getCharacterId())
                .scope(resolveScope(request))
                .category(request.getCategory())
                .contentPreview(preview(request.getContent()))
                .sourceUri(result.getFilePath())
                .confidence(request.getConfidence())
                .sourceSessionId(request.getSourceSessionId())
                .sourceMessageId(request.getSourceMessageId())
                .retrievalTerms(tokenize(request.getContent()))
                .embeddingRef(null)
                .archivedAt(request.isArchived() ? Instant.now() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        indexRepository.save(index);
        if (embeddingIndexer != null && !request.isArchived()) {
            embeddingIndexer.indexAsync(result.getMemoryId(), request.getContent());
        }
    }

    private MemoryScope resolveScope(MemoryWriteRequest request) {
        if (request.getScope() != null) {
            return request.getScope();
        }
        return request.getCategory() == MemoryCategory.RELATIONSHIP && request.getCharacterId() != null
                ? MemoryScope.CHARACTER
                : MemoryScope.GLOBAL;
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 500 ? content.substring(0, 500) : content;
    }

    private List<String> tokenize(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        String[] tokens = Pattern.compile("[\\s\\p{Punct}]+").split(content);
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String normalized = token.trim().toLowerCase();
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
                if (result.size() >= 20) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}

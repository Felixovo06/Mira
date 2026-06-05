package com.felix.miraagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class BlockingQueueMemoryWriter implements SerializedMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(BlockingQueueMemoryWriter.class);
    /** worker 空闲时的轮询间隔：用于在不依赖毒丸的前提下感知停机。 */
    private static final long POLL_INTERVAL_MS = 200;
    /** 默认队列容量;<=0 视为无界(仅测试/退化场景)。 */
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final long DEFAULT_OFFER_TIMEOUT_MS = 5_000;
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 10_000;

    /** 默认阈值：>= 视为同一事实重复（仅强化、不新增卡片）。pg_trgm similarity，0-1。 */
    private static final double DEFAULT_EXACT_DUP_THRESHOLD = 0.82;
    /** 默认阈值：>= 视为近似（更新旧卡片内容/置信度，不新增卡片）。 */
    private static final double DEFAULT_NEAR_DUP_THRESHOLD = 0.6;
    /** 默认语义去重下水位（向量 cosine）。 */
    private static final double DEFAULT_SEMANTIC_NEAR_THRESHOLD = 0.86;
    /** 默认语义去重上水位（向量 cosine）。 */
    private static final double DEFAULT_SEMANTIC_EXACT_THRESHOLD = 0.92;
    /** 同一事实被再次确认时，置信度强化增量（上限 100）。 */
    private static final int CONFIDENCE_REINFORCE = 5;

    private final MemoryStore memoryStore;
    private final MemoryIndexRepository indexRepository;
    private final AsyncEmbeddingIndexer embeddingIndexer;
    private final MemoryWritePolicy memoryWritePolicy;
    /** nullable：有则启用语义去重，并在写入路径上同步算一次向量（供去重 + 同步落库复用）。 */
    private final EmbeddingClient embeddingClient;
    /** >= 此相似度视为完全重复，跳过新增、仅强化既有；<=0 关闭去重。 */
    private final double exactDupThreshold;
    /** [near, exact) 区间视为近似重复，更新既有卡片。 */
    private final double nearDupThreshold;
    /** 向量 cosine >= 此值视为同一事实（catch 同义改写）。 */
    private final double semanticNearThreshold;
    /** 向量 cosine >= 此值按完全重复处理（仅强化、不改措辞）。 */
    private final double semanticExactThreshold;
    /** 有界写入队列：背压泄流,防突发写入无界增长 OOM。 */
    private final LinkedBlockingQueue<Runnable> queue;
    /** 同步 submit 入队的最长等待;超时按失败返回。 */
    private final long offerTimeoutMs;
    /** shutdown 排空的最长等待;超时则中断 worker。 */
    private final long shutdownTimeoutMs;
    /** 因队满/停机被拒的写入计数,供观测与测试断言。 */
    private final AtomicLong droppedCount = new AtomicLong();
    /** 置位后拒绝新提交;worker 排空后退出。 */
    private volatile boolean shuttingDown = false;
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
        this(memoryStore, indexRepository, embeddingIndexer, memoryWritePolicy, null,
                nearDupThreshold, exactDupThreshold,
                DEFAULT_SEMANTIC_NEAR_THRESHOLD, DEFAULT_SEMANTIC_EXACT_THRESHOLD);
    }

    public BlockingQueueMemoryWriter(MemoryStore memoryStore,
                                     MemoryIndexRepository indexRepository,
                                     AsyncEmbeddingIndexer embeddingIndexer,
                                     MemoryWritePolicy memoryWritePolicy,
                                     EmbeddingClient embeddingClient,
                                     double nearDupThreshold,
                                     double exactDupThreshold,
                                     double semanticNearThreshold,
                                     double semanticExactThreshold) {
        this(memoryStore, indexRepository, embeddingIndexer, memoryWritePolicy, embeddingClient,
                nearDupThreshold, exactDupThreshold, semanticNearThreshold, semanticExactThreshold,
                DEFAULT_QUEUE_CAPACITY, DEFAULT_OFFER_TIMEOUT_MS, DEFAULT_SHUTDOWN_TIMEOUT_MS);
    }

    public BlockingQueueMemoryWriter(MemoryStore memoryStore,
                                     MemoryIndexRepository indexRepository,
                                     AsyncEmbeddingIndexer embeddingIndexer,
                                     MemoryWritePolicy memoryWritePolicy,
                                     EmbeddingClient embeddingClient,
                                     double nearDupThreshold,
                                     double exactDupThreshold,
                                     double semanticNearThreshold,
                                     double semanticExactThreshold,
                                     int queueCapacity,
                                     long offerTimeoutMs,
                                     long shutdownTimeoutMs) {
        this.memoryStore = memoryStore;
        this.indexRepository = indexRepository;
        this.embeddingIndexer = embeddingIndexer;
        this.memoryWritePolicy = memoryWritePolicy;
        this.embeddingClient = embeddingClient;
        this.nearDupThreshold = nearDupThreshold;
        this.exactDupThreshold = exactDupThreshold;
        this.semanticNearThreshold = semanticNearThreshold;
        this.semanticExactThreshold = semanticExactThreshold;
        this.queue = queueCapacity > 0 ? new LinkedBlockingQueue<>(queueCapacity) : new LinkedBlockingQueue<>();
        this.offerTimeoutMs = offerTimeoutMs;
        this.shutdownTimeoutMs = shutdownTimeoutMs;
        this.workerThread = Thread.ofVirtual().name("memory-writer").start(this::processQueue);
    }

    /** 被拒计数(队满或停机中)。供观测/测试。 */
    public long droppedCount() {
        return droppedCount.get();
    }

    @Override
    public MemoryWriteResult submit(MemoryWriteRequest request) {
        // 同步语义：入队后等结果。供工具调用（需把结果回给模型）使用。返回即落定。
        return enqueueWrite(request, true).join();
    }

    @Override
    public CompletableFuture<MemoryWriteResult> submitAsync(MemoryWriteRequest request) {
        return enqueueWrite(request, false);
    }

    /**
     * 构造写入任务并按背压策略入队。blocking=true(工具路径)队满时阻塞至 offerTimeoutMs；
     * blocking=false(后台路径)队满即拒绝,泄流不卡主回复。被拒/停机时 future 以 success=false 完成。
     */
    private CompletableFuture<MemoryWriteResult> enqueueWrite(MemoryWriteRequest request, boolean blocking) {
        CompletableFuture<MemoryWriteResult> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                if (memoryWritePolicy != null && !memoryWritePolicy.isAllowed(request.getUserId(), request.getCategory())) {
                    future.complete(MemoryWriteResult.builder()
                            .memoryId(request.getMemoryId())
                            .success(false)
                            .error("Memory category is banned: " + request.getCategory())
                            .build());
                    return;
                }
                // 在 writer 线程上同步算一次向量：去重与写入索引复用，避免重复计算。
                List<Float> embedding = computeEmbedding(request);
                MemoryWriteResult deduped = tryDeduplicate(request, embedding);
                if (deduped != null) {
                    future.complete(deduped);
                    return;
                }
                MemoryWriteResult result = memoryStore.write(request);
                if (result.isSuccess()) {
                    try {
                        indexWrite(request, result, embedding);
                    } catch (Exception e) {
                        log.warn("Failed to update memory index for memory {}", result.getMemoryId(), e);
                    }
                }
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        };
        if (!tryEnqueue(task, blocking)) {
            future.complete(rejected(request.getMemoryId()));
        }
        return future;
    }

    @Override
    public MemoryWriteResult archive(String userId, String memoryId) {
        CompletableFuture<MemoryWriteResult> future = new CompletableFuture<>();
        Runnable task = () -> {
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
        };
        // 归档是控制操作（用户发起、需落定）：按阻塞入队,与 submit 同。
        if (!tryEnqueue(task, true)) {
            future.complete(rejected(memoryId));
        }
        return future.join();
    }

    /** 入队:停机中直接拒绝;blocking 路径队满阻塞至 offerTimeoutMs,非 blocking 队满即拒。 */
    private boolean tryEnqueue(Runnable task, boolean blocking) {
        if (shuttingDown) {
            droppedCount.incrementAndGet();
            log.warn("Memory writer is shutting down, rejecting write (dropped total={})", droppedCount.get());
            return false;
        }
        try {
            boolean accepted = blocking
                    ? queue.offer(task, offerTimeoutMs, TimeUnit.MILLISECONDS)
                    : queue.offer(task);
            if (!accepted) {
                droppedCount.incrementAndGet();
                log.warn("Memory writer queue full (capacity reached), rejecting {} write (dropped total={})",
                        blocking ? "blocking" : "async", droppedCount.get());
            }
            return accepted;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            droppedCount.incrementAndGet();
            return false;
        }
    }

    private MemoryWriteResult rejected(String memoryId) {
        return MemoryWriteResult.builder()
                .memoryId(memoryId)
                .success(false)
                .error("memory writer queue full or shutting down")
                .build();
    }

    @Override
    public void shutdown() {
        shuttingDown = true;
        try {
            workerThread.join(shutdownTimeoutMs);
            if (workerThread.isAlive()) {
                log.warn("Memory writer did not drain within {}ms, interrupting worker", shutdownTimeoutMs);
                workerThread.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processQueue() {
        while (true) {
            Runnable task;
            try {
                // 轮询而非 take()：空闲时也能感知停机标志,无需毒丸(毒丸在有界满队列下难以投递)。
                task = queue.poll(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (task != null) {
                task.run();
            } else if (shuttingDown) {
                break; // 队列已空且停机中：退出
            }
        }
        // 终排空：覆盖停机窗口内偶然入队的任务,保证其 future 完成、已落定不丢。
        for (Runnable remaining; (remaining = queue.poll()) != null; ) {
            remaining.run();
        }
    }

    /** 在 writer 线程上同步算内容向量；无 EmbeddingClient / 归档 / 空内容 / 失败时返回 null（降级到词法）。 */
    private List<Float> computeEmbedding(MemoryWriteRequest request) {
        if (embeddingClient == null || request.isArchived()
                || request.getContent() == null || request.getContent().isBlank()) {
            return null;
        }
        try {
            return embeddingClient.embed(request.getContent());
        } catch (Exception e) {
            log.warn("Embedding for memory write failed, degrade to lexical-only dedup/recall. user={}",
                    request.getUserId(), e);
            return null;
        }
    }

    /**
     * 写入前去重：先字面（pg_trgm）抓完全重复，再用向量 cosine 补字面漏掉的同义改写。
     * <ul>
     *   <li>字面 >= exact：同一事实 —— 不新增、仅强化既有置信度；</li>
     *   <li>语义 cosine >= semanticExact：按完全重复强化；[semanticNear, semanticExact) 按近似更新措辞；</li>
     *   <li>字面 [near, exact)：近似 —— 更新既有卡片措辞/置信度；</li>
     *   <li>都不命中：判为新事实，返回 null 走正常写入。</li>
     * </ul>
     * 去重以 DB 索引为准、不回改 .md（文件即去重后的集合，重建索引保持一致）。
     * 任何一路查询失败都不得阻断写入：异常时跳过该路，最终退化为正常写入。
     */
    private MemoryWriteResult tryDeduplicate(MemoryWriteRequest request, List<Float> embedding) {
        if (indexRepository == null || exactDupThreshold <= 0
                || request.isArchived()
                || request.getContent() == null || request.getContent().isBlank()) {
            return null;
        }
        // 1) 字面 trgm：先抓完全重复（无需向量调用）
        SimilarMemory trgmHit = null;
        try {
            trgmHit = indexRepository.findMostSimilar(
                    request.getUserId(), request.getCharacterId(), request.getCategory(),
                    preview(request.getContent()), nearDupThreshold).orElse(null);
        } catch (Exception e) {
            log.warn("Trgm dedup lookup failed, skip lexical dedup for user {}", request.getUserId(), e);
        }
        if (trgmHit != null && trgmHit.getSimilarity() >= exactDupThreshold) {
            return mergeInto(trgmHit, true, "lexical-exact", request);
        }
        // 2) 语义向量：catch 字面没抓到的同义改写
        if (embedding != null) {
            SimilarMemory vecHit = null;
            try {
                vecHit = indexRepository.findMostSimilarByVector(
                        request.getUserId(), request.getCharacterId(), request.getCategory(),
                        embedding, semanticNearThreshold).orElse(null);
            } catch (Exception e) {
                log.warn("Vector dedup lookup failed, skip semantic dedup for user {}", request.getUserId(), e);
            }
            if (vecHit != null) {
                boolean exact = vecHit.getSimilarity() >= semanticExactThreshold;
                return mergeInto(vecHit, exact, exact ? "semantic-exact" : "semantic-near", request);
            }
        }
        // 3) 字面近似
        if (trgmHit != null) {
            return mergeInto(trgmHit, false, "lexical-near", request);
        }
        return null;
    }

    /** 合并进既有卡片：exact 仅强化置信度；非 exact 同时以新措辞更新预览与检索词。失败则返回 null 退化为正常写入。 */
    private MemoryWriteResult mergeInto(SimilarMemory hit, boolean exact, String reason, MemoryWriteRequest request) {
        MemoryIndex existing = hit.getMemory();
        MemoryIndex.MemoryIndexBuilder updated = existing.toBuilder()
                .confidence(Math.min(100, exact
                        ? existing.getConfidence() + CONFIDENCE_REINFORCE
                        : Math.max(existing.getConfidence(), request.getConfidence())));
        if (!exact) {
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
                reason, String.format("%.2f", hit.getSimilarity()));
        return MemoryWriteResult.builder()
                .memoryId(existing.getId())
                .filePath(existing.getSourceUri())
                .success(true)
                .deduplicated(true)
                .build();
    }

    private void indexWrite(MemoryWriteRequest request, MemoryWriteResult result, List<Float> embedding) {
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
        if (request.isArchived() || embeddingIndexer == null) {
            return;
        }
        if (embedding != null) {
            // 已在 writer 线程算好向量：同步落库，消除“写入后向量未就绪、向量召回漏掉新卡”的窗口。
            embeddingIndexer.persist(result.getMemoryId(), embedding);
        } else {
            // 无直连 EmbeddingClient（或本次计算失败）：退回异步计算+落库，保留原行为。
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

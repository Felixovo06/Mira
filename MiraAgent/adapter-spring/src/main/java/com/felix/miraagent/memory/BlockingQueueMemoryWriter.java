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

    private final MemoryStore memoryStore;
    private final MemoryIndexRepository indexRepository;
    private final AsyncEmbeddingIndexer embeddingIndexer;
    private final MemoryWritePolicy memoryWritePolicy;
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
        this.memoryStore = memoryStore;
        this.indexRepository = indexRepository;
        this.embeddingIndexer = embeddingIndexer;
        this.memoryWritePolicy = memoryWritePolicy;
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

package com.felix.miraagent.memory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueMemoryWriter implements SerializedMemoryWriter {

    private static final Runnable POISON_PILL = () -> {};

    private final MemoryStore memoryStore;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;

    public BlockingQueueMemoryWriter(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        this.workerThread = Thread.ofVirtual().name("memory-writer").start(this::processQueue);
    }

    @Override
    public MemoryWriteResult submit(MemoryWriteRequest request) {
        CompletableFuture<MemoryWriteResult> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                MemoryWriteResult result = memoryStore.write(request);
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
}

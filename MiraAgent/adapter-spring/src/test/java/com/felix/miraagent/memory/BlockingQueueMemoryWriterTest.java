package com.felix.miraagent.memory;

import com.felix.miraagent.memory.jdbc.MemoryIndexRebuildService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BlockingQueueMemoryWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void submitWritesFileAndIndex() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        var writer = new BlockingQueueMemoryWriter(store, repo, null);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-1")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PREFERENCE)
                    .content("喜欢喝乌龙茶")
                    .sourceSessionId("s1")
                    .confidence(60)
                    .build());

            assertTrue(result.isSuccess());
            assertEquals("u1/PREFERENCES.md", result.getFilePath());
            assertTrue(Files.readString(tempDir.resolve("u1/PREFERENCES.md")).contains("喜欢喝乌龙茶"));
            assertEquals(1, repo.saved.size());
            MemoryIndex index = repo.saved.get(0);
            assertEquals("mem-1", index.getId());
            assertEquals("u1", index.getUserId());
            assertEquals(MemoryCategory.PREFERENCE, index.getCategory());
            assertEquals("喜欢喝乌龙茶", index.getContentPreview());
            assertEquals("s1", index.getSourceSessionId());
            assertEquals(60, index.getConfidence());
        } finally {
            writer.shutdown();
        }
    }

    @Test
    void archiveRunsThroughSameWriterAndArchivesIndex() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        var writer = new BlockingQueueMemoryWriter(store, repo, null);
        try {
            writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-2")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.EVENT)
                    .content("去过杭州")
                    .build());

            MemoryWriteResult archived = writer.archive("u1", "mem-2");

            assertTrue(archived.isSuccess());
            assertEquals("mem-2", repo.archivedMemoryId);
            assertTrue(Files.readString(tempDir.resolve("u1/MEMORY.md")).contains("<!-- archived -->"));
        } finally {
            writer.shutdown();
        }
    }

    @Test
    void bannedCategoryRejectsWritesBeforeFileMutation() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var policy = new InMemoryMemoryWritePolicy();
        policy.banCategory("u1", MemoryCategory.PROFILE);
        var writer = new BlockingQueueMemoryWriter(store, null, null, policy);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-3")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PROFILE)
                    .content("不要写入")
                    .build());

            assertFalse(result.isSuccess());
            assertFalse(Files.exists(tempDir.resolve("u1/USER.md")));
        } finally {
            writer.shutdown();
        }
    }

    private static class CapturingMemoryIndexRepository implements MemoryIndexRepository {
        private final List<MemoryIndex> saved = new ArrayList<>();
        private String archivedMemoryId;
        private SimilarMemory similarToReturn;
        private SimilarMemory vectorSimilarToReturn;

        void seed(MemoryIndex index) {
            saved.add(index);
        }

        @Override
        public void save(MemoryIndex index) {
            // upsert by id 以模拟真实仓库
            saved.removeIf(i -> i.getId().equals(index.getId()));
            saved.add(index);
        }

        @Override
        public void archive(String userId, String memoryId) {
            archivedMemoryId = memoryId;
        }

        @Override
        public List<MemoryIndex> findByUser(String userId, String characterId, String category) {
            return saved;
        }

        @Override
        public Optional<MemoryIndex> findById(String id) {
            return saved.stream().filter(i -> i.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<SimilarMemory> findMostSimilar(String userId, String characterId, MemoryCategory category,
                                                       String content, double minSimilarity) {
            if (similarToReturn != null && similarToReturn.getSimilarity() >= minSimilarity) {
                return Optional.of(similarToReturn);
            }
            return Optional.empty();
        }

        @Override
        public Optional<SimilarMemory> findMostSimilarByVector(String userId, String characterId, MemoryCategory category,
                                                               List<Float> embedding, double minSimilarity) {
            if (vectorSimilarToReturn != null && vectorSimilarToReturn.getSimilarity() >= minSimilarity) {
                return Optional.of(vectorSimilarToReturn);
            }
            return Optional.empty();
        }

        @Override
        public void deleteAll(String userId) {
            saved.clear();
        }
    }

    private static MemoryIndex existingPreference(String id, String content, int confidence) {
        return MemoryIndex.builder()
                .id(id)
                .userId("u1")
                .scope(MemoryScope.GLOBAL)
                .category(MemoryCategory.PREFERENCE)
                .contentPreview(content)
                .sourceUri("u1/PREFERENCES.md")
                .confidence(confidence)
                .build();
    }

    @Test
    void exactDuplicateReinforcesWithoutNewCardOrFileAppend() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        repo.seed(existingPreference("mem-existing", "喜欢喝乌龙茶", 60));
        repo.similarToReturn = SimilarMemory.builder()
                .memory(repo.saved.get(0)).similarity(0.95).build();
        var writer = new BlockingQueueMemoryWriter(store, repo, null);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-new")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PREFERENCE)
                    .content("喜欢喝乌龙茶")
                    .confidence(80)
                    .build());

            assertTrue(result.isSuccess());
            assertTrue(result.isDeduplicated());
            assertEquals("mem-existing", result.getMemoryId());
            // 没有新增卡片，置信度被强化（60 -> 65），内容保持不变
            assertEquals(1, repo.saved.size());
            MemoryIndex merged = repo.saved.get(0);
            assertEquals("mem-existing", merged.getId());
            assertEquals(65, merged.getConfidence());
            assertEquals("喜欢喝乌龙茶", merged.getContentPreview());
            // 文件未被追加
            assertFalse(Files.exists(tempDir.resolve("u1/PREFERENCES.md")));
        } finally {
            writer.shutdown();
        }
    }

    @Test
    void nearDuplicateUpdatesExistingCard() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        repo.seed(existingPreference("mem-existing", "喜欢乌龙茶", 60));
        repo.similarToReturn = SimilarMemory.builder()
                .memory(repo.saved.get(0)).similarity(0.7).build();
        var writer = new BlockingQueueMemoryWriter(store, repo, null);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-new")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PREFERENCE)
                    .content("其实更喜欢喝乌龙和普洱")
                    .confidence(80)
                    .build());

            assertTrue(result.isDeduplicated());
            assertEquals(1, repo.saved.size());
            MemoryIndex merged = repo.saved.get(0);
            assertEquals("mem-existing", merged.getId());
            assertEquals(80, merged.getConfidence());
            assertEquals("其实更喜欢喝乌龙和普洱", merged.getContentPreview());
            assertFalse(Files.exists(tempDir.resolve("u1/PREFERENCES.md")));
        } finally {
            writer.shutdown();
        }
    }

    /** 字面 trgm 没命中、但向量语义命中（同义改写）：应判重并强化既有，不新增卡片。 */
    @Test
    void semanticDuplicateReinforcesWhenLexicalMisses() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        repo.seed(existingPreference("mem-existing", "我养了一只橘猫", 60));
        // 字面相似度低（措辞不同），trgm 查不到
        repo.similarToReturn = null;
        // 向量语义高度相似（>= semanticExact 0.92）
        repo.vectorSimilarToReturn = SimilarMemory.builder()
                .memory(repo.saved.get(0)).similarity(0.95).build();
        EmbeddingClient embedding = text -> List.of(0.1f, 0.2f, 0.3f);
        var writer = new BlockingQueueMemoryWriter(store, repo, null, null, embedding,
                0.6, 0.82, 0.86, 0.92);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-new")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PREFERENCE)
                    .content("家里有只胖橘是我的宝贝")
                    .confidence(80)
                    .build());

            assertTrue(result.isDeduplicated());
            assertEquals("mem-existing", result.getMemoryId());
            assertEquals(1, repo.saved.size());
            // semantic-exact：仅强化置信度（60 -> 65），措辞不变、不新增卡片、不写文件
            assertEquals(65, repo.saved.get(0).getConfidence());
            assertEquals("我养了一只橘猫", repo.saved.get(0).getContentPreview());
            assertFalse(Files.exists(tempDir.resolve("u1/PREFERENCES.md")));
        } finally {
            writer.shutdown();
        }
    }

    /** 无 EmbeddingClient 时不做语义去重：字面又没命中则正常新增卡片（保持原行为）。 */
    @Test
    void noEmbeddingClientSkipsSemanticDedup() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        repo.seed(existingPreference("mem-existing", "我养了一只橘猫", 60));
        // 即使向量层准备了命中，没有 EmbeddingClient 就不会走到向量查询
        repo.vectorSimilarToReturn = SimilarMemory.builder()
                .memory(repo.saved.get(0)).similarity(0.99).build();
        var writer = new BlockingQueueMemoryWriter(store, repo, null);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-new")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PREFERENCE)
                    .content("家里有只胖橘是我的宝贝")
                    .confidence(80)
                    .build());

            assertTrue(result.isSuccess());
            assertFalse(result.isDeduplicated());
            assertEquals(2, repo.saved.size());
        } finally {
            writer.shutdown();
        }
    }

    @Test
    void distinctMemoryWritesNewCardNormally() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        repo.seed(existingPreference("mem-existing", "喜欢喝乌龙茶", 60));
        // 无相似命中
        var writer = new BlockingQueueMemoryWriter(store, repo, null);
        try {
            MemoryWriteResult result = writer.submit(MemoryWriteRequest.builder()
                    .memoryId("mem-new")
                    .userId("u1")
                    .scope(MemoryScope.GLOBAL)
                    .category(MemoryCategory.PREFERENCE)
                    .content("养了一只叫胖橘的猫")
                    .confidence(80)
                    .build());

            assertTrue(result.isSuccess());
            assertFalse(result.isDeduplicated());
            assertEquals(2, repo.saved.size());
            assertTrue(Files.readString(tempDir.resolve("u1/PREFERENCES.md")).contains("胖橘"));
        } finally {
            writer.shutdown();
        }
    }

    // ---------- G7：并发与一致性收口 ----------

    /** 多线程并发 submitAsync 跨用户：单 worker 串行落盘,所有写入零丢失、索引数与提交数一致。 */
    @Test
    void concurrentAsyncWritesAllPersistWithoutLoss() throws Exception {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        // 关闭去重(near=exact=0),让每条独立内容都落成独立卡片,便于断言零丢失
        var writer = new BlockingQueueMemoryWriter(store, repo, null, null, 0, 0);
        int threads = 8, perThread = 50, total = threads * perThread;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<CompletableFuture<MemoryWriteResult>> futures =
                java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            futures.add(writer.submitAsync(req("u" + tid + "-m" + i, "u" + tid, "fact " + tid + " " + i)));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "producers should finish enqueueing");
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

            assertEquals(total, futures.size());
            assertTrue(futures.stream().allMatch(f -> f.join().isSuccess()), "no write should be rejected/lost");
            assertEquals(total, repo.saved.size(), "every distinct write must land exactly once");
        } finally {
            writer.shutdown();
            pool.shutdownNow();
        }
    }

    /** 队列容量耗尽时:异步写入被拒(success=false)而非阻塞/OOM,且 droppedCount 计数准确。 */
    @Test
    void backpressureRejectsAsyncWritesWhenQueueFull() throws Exception {
        var gate = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var store = new GatedMemoryStore(new MemoryFileStore(tempDir.toString()), gate, started);
        var repo = new CapturingMemoryIndexRepository();
        // 容量 1：worker 卡住后,只容 1 条入队,其余应被拒
        var writer = new BlockingQueueMemoryWriter(store, repo, null, null, null,
                0, 0, 0, 0, 1, 100, 2000);
        try {
            writer.submitAsync(req("m-0", "u", "blocked")); // 被 worker 取走并卡在 write
            assertTrue(started.await(2, TimeUnit.SECONDS), "worker should pick up first task");

            List<CompletableFuture<MemoryWriteResult>> rest = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                rest.add(writer.submitAsync(req("m-" + i, "u", "extra-" + i)));
            }
            // 1 条进队列(未完成、不 join 以免死等),其余 19 条同步被拒
            long rejectedDone = rest.stream().filter(CompletableFuture::isDone)
                    .filter(f -> !f.join().isSuccess()).count();
            assertEquals(19, rejectedDone, "19 of 20 should be rejected (capacity=1, worker busy)");
            assertEquals(19, writer.droppedCount());
        } finally {
            gate.countDown();
            writer.shutdown();
        }
    }

    /** 停机后再提交:future 以失败完成、立即返回,绝不永久挂起。 */
    @Test
    void submitAfterShutdownReturnsFailureWithoutHanging() {
        var store = new MemoryFileStore(tempDir.toString());
        var writer = new BlockingQueueMemoryWriter(store, null, null);
        writer.shutdown();

        MemoryWriteResult result = assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> writer.submit(req("m-late", "u", "after shutdown")));

        assertFalse(result.isSuccess());
        assertTrue(writer.droppedCount() >= 1);
    }

    /** 优雅停机:已入队但未执行的写入会被排空,futures 全部成功完成、文件落盘。 */
    @Test
    void gracefulShutdownDrainsQueuedWrites() throws Exception {
        var gate = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var store = new GatedMemoryStore(new MemoryFileStore(tempDir.toString()), gate, started);
        var repo = new CapturingMemoryIndexRepository();
        var writer = new BlockingQueueMemoryWriter(store, repo, null, null, 0, 0);

        var futures = new ArrayList<CompletableFuture<MemoryWriteResult>>();
        for (int i = 0; i < 5; i++) {
            futures.add(writer.submitAsync(req("m-" + i, "u", "fact " + i)));
        }
        assertTrue(started.await(2, TimeUnit.SECONDS), "worker should be busy on first task");

        var shutdownThread = new Thread(writer::shutdown, "shutdown-trigger");
        shutdownThread.start();
        gate.countDown(); // 放行,worker 处理完 m-0 后应继续排空 m-1..m-4

        shutdownThread.join(8000);
        assertFalse(shutdownThread.isAlive(), "shutdown should complete after drain");
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
        assertTrue(futures.stream().allMatch(f -> f.join().isSuccess()), "all queued writes must drain");
        assertEquals(5, repo.saved.size());
    }

    /** 崩溃恢复:索引全失而 .md 仍在时,rebuild 从文件重建索引(事实源=文件)。 */
    @Test
    void rebuildRestoresIndexFromFilesAfterIndexLoss() {
        var store = new MemoryFileStore(tempDir.toString());
        var repo = new CapturingMemoryIndexRepository();
        var writer = new BlockingQueueMemoryWriter(store, repo, null, null, 0, 0);
        try {
            writer.submit(MemoryWriteRequest.builder().memoryId("mem-a").userId("u1")
                    .scope(MemoryScope.GLOBAL).category(MemoryCategory.PREFERENCE)
                    .content("喜欢喝乌龙茶").confidence(70).build());
            writer.submit(MemoryWriteRequest.builder().memoryId("mem-b").userId("u1")
                    .scope(MemoryScope.GLOBAL).category(MemoryCategory.EVENT)
                    .content("去过杭州西湖").confidence(70).build());
        } finally {
            writer.shutdown();
        }
        assertEquals(2, repo.saved.size());

        // 模拟崩溃:索引全部丢失,但 .md 文件仍在
        repo.deleteAll("u1");
        assertEquals(0, repo.saved.size());
        assertTrue(Files.exists(tempDir.resolve("u1/PREFERENCES.md")));
        assertTrue(Files.exists(tempDir.resolve("u1/MEMORY.md")));

        // 从文件重建
        var props = new MemoryProperties();
        props.setBaseDir(tempDir.toString());
        new MemoryIndexRebuildService(store, repo, props).rebuild("u1");

        assertEquals(2, repo.saved.size(), "both cards must be recovered from .md");
        assertTrue(repo.findById("mem-a").isPresent());
        assertTrue(repo.findById("mem-b").isPresent());
    }

    private static MemoryWriteRequest req(String memoryId, String userId, String content) {
        return MemoryWriteRequest.builder()
                .memoryId(memoryId)
                .userId(userId)
                .scope(MemoryScope.GLOBAL)
                .category(MemoryCategory.EVENT)
                .content(content)
                .confidence(50)
                .build();
    }

    /** 测试用:在 write() 上挂闸,模拟 worker 被慢写阻塞,以制造背压/排空场景。 */
    private static class GatedMemoryStore implements MemoryStore {
        private final MemoryStore delegate;
        private final CountDownLatch gate;
        private final CountDownLatch started;

        GatedMemoryStore(MemoryStore delegate, CountDownLatch gate, CountDownLatch started) {
            this.delegate = delegate;
            this.gate = gate;
            this.started = started;
        }

        @Override
        public MemoryWriteResult write(MemoryWriteRequest request) {
            started.countDown();
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return delegate.write(request);
        }

        @Override
        public MemoryWriteResult archive(String userId, String memoryId) {
            return delegate.archive(userId, memoryId);
        }

        @Override
        public String readFile(String userId, String fileRelativePath) {
            return delegate.readFile(userId, fileRelativePath);
        }
    }
}

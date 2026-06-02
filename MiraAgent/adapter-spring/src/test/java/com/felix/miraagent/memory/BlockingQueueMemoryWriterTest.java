package com.felix.miraagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
}

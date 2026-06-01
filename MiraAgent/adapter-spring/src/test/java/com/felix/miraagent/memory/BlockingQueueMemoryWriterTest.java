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

        @Override
        public void save(MemoryIndex index) {
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
        public void deleteAll(String userId) {
            saved.clear();
        }
    }
}

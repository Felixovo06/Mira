package com.felix.miraagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryFileStoreTest {

    private MemoryWriteRequest req(String userId, MemoryCategory cat, String content) {
        return MemoryWriteRequest.builder()
                .userId(userId).category(cat).content(content).build();
    }

    @Test
    void writesNormalUserMemoryWithinBase(@TempDir Path dir) {
        MemoryFileStore store = new MemoryFileStore(dir.toString());
        MemoryWriteResult r = store.write(req("alice", MemoryCategory.PROFILE, "likes tea"));
        assertTrue(r.isSuccess());
        assertTrue(Files.exists(dir.resolve("alice").resolve("USER.md")));
    }

    @Test
    void rejectsUserIdPathTraversalOnWrite(@TempDir Path dir, @TempDir Path outside) throws IOException {
        MemoryFileStore store = new MemoryFileStore(dir.toString());
        // 恶意 userId 想逃出记忆目录写到外面
        String evilUser = "../" + outside.getFileName().toString();
        MemoryWriteResult r = store.write(req(evilUser, MemoryCategory.PROFILE, "pwned"));
        assertFalse(r.isSuccess(), "越界 userId 应写入失败");
        // 确认外部目录没有被写入 USER.md
        assertFalse(Files.exists(outside.resolve("USER.md")));
    }

    @Test
    void rejectsCharacterIdEscapingSandbox(@TempDir Path dir) {
        MemoryFileStore store = new MemoryFileStore(dir.toString());
        // 绝对路径 characterId 想把关系记忆写到沙箱外
        MemoryWriteResult r = store.write(MemoryWriteRequest.builder()
                .userId("bob").category(MemoryCategory.RELATIONSHIP)
                .characterId("/etc").content("x").build());
        assertFalse(r.isSuccess(), "越界 characterId 应写入失败");
        assertFalse(Files.exists(Path.of("/etc/RELATIONSHIP.md")));
    }

    @Test
    void readFileReturnsEmptyForTraversalUserId(@TempDir Path dir) {
        MemoryFileStore store = new MemoryFileStore(dir.toString());
        assertEquals("", store.readFile("../../etc", "passwd"));
    }
}

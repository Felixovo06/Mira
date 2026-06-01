package com.felix.miraagent.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemoryFileStore implements MemoryStore {

    private final Path baseDir;

    public MemoryFileStore(String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    /**
     * 确保候选路径归一化后仍在 baseDir 内，否则拒绝。
     * userId/characterId 来自客户端请求，可能含 {@code ../}——防止越界写读记忆目录外的文件。
     */
    private Path within(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(baseDir)) {
            throw new IllegalArgumentException("memory path escapes base dir: " + candidate);
        }
        return normalized;
    }

    @Override
    public MemoryWriteResult write(MemoryWriteRequest request) {
        String memoryId = request.getMemoryId() != null ? request.getMemoryId() : UUID.randomUUID().toString();
        try {
            Path filePath = resolveFilePath(request);
            Files.createDirectories(filePath.getParent());

            String entry = buildEntry(memoryId, request);
            Files.writeString(filePath, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            String relPath = baseDir.relativize(filePath).toString();
            return MemoryWriteResult.builder()
                    .memoryId(memoryId)
                    .filePath(relPath)
                    .success(true)
                    .build();
        } catch (IOException | IllegalArgumentException e) {
            return MemoryWriteResult.builder()
                    .memoryId(memoryId)
                    .filePath(null)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    @Override
    public MemoryWriteResult archive(String userId, String memoryId) {
        Path userDir;
        try {
            userDir = within(baseDir.resolve(userId));
        } catch (IllegalArgumentException e) {
            return MemoryWriteResult.builder()
                    .memoryId(memoryId).success(false).error(e.getMessage()).build();
        }
        if (!Files.exists(userDir)) {
            return MemoryWriteResult.builder()
                    .memoryId(memoryId)
                    .success(false)
                    .error("Memory directory does not exist")
                    .build();
        }
        try {
            Path archivedPath = archiveInDirectory(userDir, memoryId);
            return MemoryWriteResult.builder()
                    .memoryId(memoryId)
                    .filePath(archivedPath != null ? baseDir.relativize(archivedPath).toString() : null)
                    .success(archivedPath != null)
                    .error(archivedPath == null ? "Memory id not found" : null)
                    .build();
        } catch (IOException e) {
            return MemoryWriteResult.builder()
                    .memoryId(memoryId)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    @Override
    public String readFile(String userId, String fileRelativePath) {
        Path filePath;
        try {
            filePath = within(baseDir.resolve(userId).resolve(fileRelativePath));
        } catch (IllegalArgumentException e) {
            return ""; // 越界路径按"无此记忆"处理，不抛穿透 loop
        }
        if (!Files.exists(filePath)) {
            return "";
        }
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read memory file: " + filePath, e);
        }
    }

    private Path resolveFilePath(MemoryWriteRequest request) {
        Path userDir = baseDir.resolve(request.getUserId());
        MemoryCategory category = request.getCategory();

        Path target;
        if (category == MemoryCategory.RELATIONSHIP && request.getCharacterId() != null) {
            target = userDir.resolve("characters").resolve(request.getCharacterId()).resolve("RELATIONSHIP.md");
        } else {
            target = switch (category) {
                case PROFILE -> userDir.resolve("USER.md");
                case PREFERENCE -> userDir.resolve("PREFERENCES.md");
                default -> userDir.resolve("MEMORY.md");
            };
        }
        // userId/characterId 来自客户端，校验最终路径不越界
        return within(target);
    }

    private String buildEntry(String memoryId, MemoryWriteRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- memory-id: ").append(memoryId);
        if (request.getSourceSessionId() != null) {
            sb.append(" source-session: ").append(request.getSourceSessionId());
        }
        sb.append(" -->\n");
        sb.append(request.getContent()).append("\n\n");
        return sb.toString();
    }

    private Path archiveInDirectory(Path dir, String memoryId) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            for (Path entry : stream.toList()) {
                Path archivedPath = null;
                if (Files.isDirectory(entry)) {
                    archivedPath = archiveInDirectory(entry, memoryId);
                } else if (entry.getFileName().toString().endsWith(".md")) {
                    archivedPath = archiveInFile(entry, memoryId);
                }
                if (archivedPath != null) {
                    return archivedPath;
                }
            }
        }
        return null;
    }

    private Path archiveInFile(Path filePath, String memoryId) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String marker = "<!-- memory-id: " + memoryId;
        int markerIndex = content.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int lineEnd = content.indexOf('\n', markerIndex);
        if (lineEnd < 0) {
            return null;
        }
        String afterCommentLine = content.substring(lineEnd + 1);
        if (afterCommentLine.startsWith("<!-- archived -->")) {
            return filePath;
        }

        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        int commentLineIndex = findLineIndex(lines, marker);
        if (commentLineIndex < 0 || commentLineIndex + 1 > lines.size()) {
            return null;
        }
        lines.add(commentLineIndex + 1, "<!-- archived -->");
        Files.writeString(filePath, String.join("\n", lines), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        return filePath;
    }

    private int findLineIndex(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(prefix)) {
                return i;
            }
        }
        return -1;
    }
}

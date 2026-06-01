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
        this.baseDir = Paths.get(baseDir);
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
        } catch (IOException e) {
            return MemoryWriteResult.builder()
                    .memoryId(memoryId)
                    .filePath(null)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    @Override
    public void archive(String userId, String memoryId) {
        Path userDir = baseDir.resolve(userId);
        if (!Files.exists(userDir)) {
            return;
        }
        try {
            archiveInDirectory(userDir, memoryId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to archive memory " + memoryId, e);
        }
    }

    @Override
    public String readFile(String userId, String fileRelativePath) {
        Path filePath = baseDir.resolve(userId).resolve(fileRelativePath);
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

        if (category == MemoryCategory.RELATIONSHIP && request.getCharacterId() != null) {
            return userDir.resolve("characters").resolve(request.getCharacterId()).resolve("RELATIONSHIP.md");
        }

        return switch (category) {
            case PROFILE -> userDir.resolve("USER.md");
            case PREFERENCE -> userDir.resolve("PREFERENCES.md");
            default -> userDir.resolve("MEMORY.md");
        };
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

    private void archiveInDirectory(Path dir, String memoryId) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path entry : stream.toList()) {
                if (Files.isDirectory(entry)) {
                    archiveInDirectory(entry, memoryId);
                } else if (entry.getFileName().toString().endsWith(".md")) {
                    archiveInFile(entry, memoryId);
                }
            }
        }
    }

    private void archiveInFile(Path filePath, String memoryId) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String marker = "<!-- memory-id: " + memoryId;
        int markerIndex = content.indexOf(marker);
        if (markerIndex < 0) {
            return;
        }
        int lineEnd = content.indexOf('\n', markerIndex);
        if (lineEnd < 0) {
            return;
        }
        String afterCommentLine = content.substring(lineEnd + 1);
        if (afterCommentLine.startsWith("<!-- archived -->")) {
            return;
        }

        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        int commentLineIndex = findLineIndex(lines, marker);
        if (commentLineIndex < 0 || commentLineIndex + 1 > lines.size()) {
            return;
        }
        lines.add(commentLineIndex + 1, "<!-- archived -->");
        Files.writeString(filePath, String.join("\n", lines), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
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

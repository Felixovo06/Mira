package com.felix.miraagent.memory.jdbc;

import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryIndex;
import com.felix.miraagent.memory.MemoryIndexRepository;
import com.felix.miraagent.memory.MemoryProperties;
import com.felix.miraagent.memory.MemoryScope;
import com.felix.miraagent.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class MemoryIndexRebuildService {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndexRebuildService.class);
    private static final int MAX_PREVIEW_CHARS = 500;
    private static final int MAX_RETRIEVAL_TERMS = 20;

    private final MemoryStore memoryStore;
    private final MemoryIndexRepository memoryIndexRepository;
    private final MemoryProperties memoryProperties;

    public MemoryIndexRebuildService(MemoryStore memoryStore,
                                     MemoryIndexRepository memoryIndexRepository,
                                     MemoryProperties memoryProperties) {
        this.memoryStore = memoryStore;
        this.memoryIndexRepository = memoryIndexRepository;
        this.memoryProperties = memoryProperties;
    }

    /**
     * Rebuild memory indexes for a user by scanning all .md files under {baseDir}/{userId}/.
     * Deletes existing indexes first, then re-indexes all non-archived memory entries.
     */
    public void rebuild(String userId) {
        log.info("Starting memory index rebuild for user={}", userId);

        // 1. Delete all existing indexes for this user
        memoryIndexRepository.deleteAll(userId);

        // 2. Scan {baseDir}/{userId}/ for all .md files
        Path userDir = Paths.get(memoryProperties.getBaseDir()).resolve(userId);
        if (!Files.exists(userDir)) {
            log.info("No memory directory found for user={}, skipping rebuild", userId);
            return;
        }

        List<Path> mdFiles = collectMdFiles(userDir);
        log.info("Found {} .md files for user={}", mdFiles.size(), userId);

        int indexed = 0;
        for (Path mdFile : mdFiles) {
            try {
                indexed += indexFile(userId, userDir, mdFile);
            } catch (IOException e) {
                log.warn("Failed to index file {}: {}", mdFile, e.getMessage());
            }
        }

        log.info("Rebuild complete for user={}: {} entries indexed", userId, indexed);
    }

    private List<Path> collectMdFiles(Path dir) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> !Files.isDirectory(p))
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .forEach(result::add);
        } catch (IOException e) {
            log.warn("Failed to walk directory {}: {}", dir, e.getMessage());
        }
        return result;
    }

    /**
     * Parse all memory entries in one file and save indexes.
     * Returns the number of entries indexed.
     */
    private int indexFile(String userId, Path userDir, Path mdFile) throws IOException {
        String content = Files.readString(mdFile, StandardCharsets.UTF_8);
        String relPath = userDir.getParent().relativize(mdFile).toString(); // e.g. "userId/USER.md"
        // Make source URI relative to baseDir
        String sourceUri = Paths.get(memoryProperties.getBaseDir()).relativize(mdFile).toString();

        String[] lines = content.split("\n", -1);
        int count = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 3. Find <!-- memory-id: xxx ... --> marker lines
            if (!line.startsWith("<!-- memory-id:")) {
                continue;
            }

            String memoryId = extractMemoryId(line);
            if (memoryId == null) {
                continue;
            }

            // 4. Skip archived entries (next non-empty line is <!-- archived -->)
            if (isNextLineArchived(lines, i)) {
                continue;
            }

            // 5. Extract session id from marker if present
            String sourceSessionId = extractSourceSession(line);

            // Collect the content block after the marker line (skip archived tag if present)
            String contentBlock = extractContentBlock(lines, i);
            String contentPreview = contentBlock.length() > MAX_PREVIEW_CHARS
                    ? contentBlock.substring(0, MAX_PREVIEW_CHARS)
                    : contentBlock;

            // Infer scope and category from file name
            MemoryScope scope = inferScope(mdFile);
            MemoryCategory category = inferCategory(mdFile);

            // 5. Build retrieval terms via simple tokenisation
            List<String> retrievalTerms = tokenize(contentBlock);

            MemoryIndex index = MemoryIndex.builder()
                    .id(memoryId)
                    .userId(userId)
                    .scope(scope)
                    .category(category)
                    .contentPreview(contentPreview)
                    .sourceUri(sourceUri)
                    .confidence(80)
                    .sourceSessionId(sourceSessionId)
                    .retrievalTerms(retrievalTerms)
                    .build();

            // 6. Save
            memoryIndexRepository.save(index);
            count++;
        }

        return count;
    }

    /** Extract the memory-id value from a marker comment line. */
    private String extractMemoryId(String line) {
        // Pattern: <!-- memory-id: <uuid> [source-session: xxx] -->
        int start = line.indexOf("<!-- memory-id:") + "<!-- memory-id:".length();
        String rest = line.substring(start).trim();
        // id ends at first space or " -->"
        int end = rest.indexOf(' ');
        if (end < 0) {
            // might be "uuid -->"
            end = rest.indexOf(" -->");
        }
        if (end < 0) {
            // whole rest until "-->"
            end = rest.indexOf("-->");
        }
        if (end < 0) {
            return rest.isEmpty() ? null : rest;
        }
        String id = rest.substring(0, end).trim();
        return id.isEmpty() ? null : id;
    }

    /** Extract source-session value from a marker comment line, if present. */
    private String extractSourceSession(String line) {
        int idx = line.indexOf("source-session:");
        if (idx < 0) {
            return null;
        }
        String rest = line.substring(idx + "source-session:".length()).trim();
        int end = rest.indexOf(' ');
        if (end < 0) {
            end = rest.indexOf("-->");
        }
        if (end < 0) {
            return rest.isEmpty() ? null : rest;
        }
        String val = rest.substring(0, end).trim();
        return val.isEmpty() ? null : val;
    }

    /** Check whether the line immediately after index i (skipping blank lines) is <!-- archived -->. */
    private boolean isNextLineArchived(String[] lines, int markerIndex) {
        for (int j = markerIndex + 1; j < lines.length; j++) {
            String l = lines[j].trim();
            if (l.isEmpty()) {
                continue;
            }
            return l.equals("<!-- archived -->");
        }
        return false;
    }

    /**
     * Collect the text content that follows a marker line.
     * Skips the marker line itself and any <!-- archived --> tag.
     * Reads until the next blank-line-separated paragraph ends or another marker starts.
     */
    private String extractContentBlock(String[] lines, int markerIndex) {
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (int j = markerIndex + 1; j < lines.length; j++) {
            String l = lines[j];
            String trimmed = l.trim();
            // skip archived marker
            if (trimmed.equals("<!-- archived -->")) {
                continue;
            }
            // stop at next memory-id marker
            if (trimmed.startsWith("<!-- memory-id:")) {
                break;
            }
            if (trimmed.isEmpty()) {
                if (started) {
                    // one blank line ends the paragraph
                    break;
                }
                continue;
            }
            started = true;
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(l);
        }
        return sb.toString().trim();
    }

    /** Infer MemoryScope from file path (SESSION scope for session files, else GLOBAL). */
    private MemoryScope inferScope(Path mdFile) {
        String name = mdFile.getFileName().toString().toUpperCase();
        if (name.contains("SESSION")) {
            return MemoryScope.SESSION;
        }
        // character-specific files are under characters/<id>/
        String pathStr = mdFile.toString();
        if (pathStr.contains("/characters/")) {
            return MemoryScope.CHARACTER;
        }
        return MemoryScope.GLOBAL;
    }

    /** Infer MemoryCategory from file name. */
    private MemoryCategory inferCategory(Path mdFile) {
        String name = mdFile.getFileName().toString().toUpperCase();
        if (name.startsWith("USER")) {
            return MemoryCategory.PROFILE;
        }
        if (name.startsWith("PREFER")) {
            return MemoryCategory.PREFERENCE;
        }
        if (name.startsWith("RELATIONSHIP")) {
            return MemoryCategory.RELATIONSHIP;
        }
        if (name.startsWith("GOAL")) {
            return MemoryCategory.GOAL;
        }
        if (name.startsWith("SUMMARY")) {
            return MemoryCategory.SUMMARY;
        }
        return MemoryCategory.EVENT;
    }

    /**
     * Simple tokeniser: split on whitespace and punctuation, lowercase,
     * deduplicate, take at most MAX_RETRIEVAL_TERMS non-empty tokens.
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] tokens = text.split("[\\s\\p{Punct}]+");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String t = token.trim().toLowerCase();
            if (!t.isEmpty() && !result.contains(t)) {
                result.add(t);
                if (result.size() >= MAX_RETRIEVAL_TERMS) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}

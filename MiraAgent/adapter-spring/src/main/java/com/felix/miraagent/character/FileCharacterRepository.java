package com.felix.miraagent.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件角色卡仓库：classpath 内置示例卡(characters/*.json) + 外部目录用户导入卡。
 * 同 id 时外部目录覆盖内置。save 写入外部目录。
 */
public class FileCharacterRepository implements CharacterRepository {

    private static final Logger log = LoggerFactory.getLogger(FileCharacterRepository.class);

    private final Path baseDir;
    private final ObjectMapper objectMapper;

    public FileCharacterRepository(String baseDir, ObjectMapper objectMapper) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CharacterProfile> findById(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return Optional.empty();
        }
        Path external = baseDir.resolve(characterId + ".json").normalize();
        if (external.startsWith(baseDir) && Files.exists(external)) {
            try {
                return Optional.of(objectMapper.readValue(Files.readString(external, StandardCharsets.UTF_8),
                        CharacterProfile.class));
            } catch (IOException e) {
                log.warn("Failed to read character card '{}': {}", characterId, e.getMessage());
            }
        }
        return loadBundled().stream().filter(c -> characterId.equals(c.getId())).findFirst();
    }

    @Override
    public List<CharacterProfile> listAll() {
        Map<String, CharacterProfile> byId = new LinkedHashMap<>();
        for (CharacterProfile c : loadBundled()) {
            byId.put(c.getId(), c);
        }
        // 外部目录覆盖内置
        if (Files.isDirectory(baseDir)) {
            try (var stream = Files.list(baseDir)) {
                stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    try {
                        CharacterProfile c = objectMapper.readValue(
                                Files.readString(p, StandardCharsets.UTF_8), CharacterProfile.class);
                        byId.put(c.getId(), c);
                    } catch (IOException e) {
                        log.warn("Skip invalid character card {}: {}", p, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.warn("Failed to list character dir {}: {}", baseDir, e.getMessage());
            }
        }
        return new ArrayList<>(byId.values());
    }

    @Override
    public CharacterProfile save(CharacterProfile profile) {
        if (profile.getId() == null || profile.getId().isBlank()) {
            throw new IllegalArgumentException("character id is required");
        }
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(profile.getId() + ".json").normalize();
            if (!target.startsWith(baseDir)) {
                throw new IllegalArgumentException("invalid character id: " + profile.getId());
            }
            Files.writeString(target,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile),
                    StandardCharsets.UTF_8);
            log.info("Saved character card '{}'", profile.getId());
            return profile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save character card: " + e.getMessage(), e);
        }
    }

    private List<CharacterProfile> loadBundled() {
        List<CharacterProfile> result = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:characters/*.json");
            for (Resource r : resources) {
                try (var in = r.getInputStream()) {
                    result.add(objectMapper.readValue(in, CharacterProfile.class));
                } catch (IOException e) {
                    log.warn("Skip invalid bundled character card {}: {}", r.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan bundled character cards: {}", e.getMessage());
        }
        return result;
    }
}

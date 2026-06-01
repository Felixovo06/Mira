package com.felix.miraagent.style;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 文件风格约束来源：外部 {@code file} 优先，回退到内置 style/default.json。
 * 加载一次并缓存，配置变更需重启（与角色卡的内置加载行为一致）。
 */
public class FileStyleConstraintProvider implements StyleConstraintProvider {

    private static final Logger log = LoggerFactory.getLogger(FileStyleConstraintProvider.class);
    private static final String BUNDLED = "style/default.json";

    private final boolean enabled;
    private final Path externalFile;
    private final ObjectMapper objectMapper;
    private final Optional<StyleConstraint> cached;

    public FileStyleConstraintProvider(StyleConstraintProperties props, ObjectMapper objectMapper) {
        this.enabled = props.isEnabled();
        this.externalFile = props.getFile() == null || props.getFile().isBlank()
                ? null
                : Paths.get(props.getFile()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.cached = load();
    }

    @Override
    public Optional<StyleConstraint> get() {
        return enabled ? cached : Optional.empty();
    }

    private Optional<StyleConstraint> load() {
        if (externalFile != null && Files.exists(externalFile)) {
            try {
                StyleConstraint sc = objectMapper.readValue(
                        Files.readString(externalFile, StandardCharsets.UTF_8), StyleConstraint.class);
                log.info("Loaded style constraint from external file {}", externalFile);
                return Optional.of(sc);
            } catch (IOException e) {
                log.warn("Failed to read style constraint file {}: {}", externalFile, e.getMessage());
            }
        }
        Resource bundled = new ClassPathResource(BUNDLED);
        if (bundled.exists()) {
            try (var in = bundled.getInputStream()) {
                return Optional.of(objectMapper.readValue(in, StyleConstraint.class));
            } catch (IOException e) {
                log.warn("Failed to read bundled {}: {}", BUNDLED, e.getMessage());
            }
        }
        return Optional.empty();
    }
}

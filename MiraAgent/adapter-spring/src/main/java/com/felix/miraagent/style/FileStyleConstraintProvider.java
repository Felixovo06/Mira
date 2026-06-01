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
 * 文件风格约束存储：外部 {@code file} 优先，回退到内置 style/default.json。
 * 加载到内存缓存；{@link #save} 写回外部文件并刷新缓存，立即生效无需重启。
 */
public class FileStyleConstraintProvider implements StyleConstraintStore {

    private static final Logger log = LoggerFactory.getLogger(FileStyleConstraintProvider.class);
    private static final String BUNDLED = "style/default.json";

    private final boolean enabled;
    private final Path externalFile;
    private final ObjectMapper objectMapper;
    private volatile StyleConstraint current;

    public FileStyleConstraintProvider(StyleConstraintProperties props, ObjectMapper objectMapper) {
        this.enabled = props.isEnabled();
        this.externalFile = props.getFile() == null || props.getFile().isBlank()
                ? null
                : Paths.get(props.getFile()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.current = load().orElse(null);
    }

    @Override
    public Optional<StyleConstraint> get() {
        return enabled ? Optional.ofNullable(current) : Optional.empty();
    }

    @Override
    public Optional<StyleConstraint> current() {
        return Optional.ofNullable(current);
    }

    @Override
    public StyleConstraint save(StyleConstraint constraint) {
        if (externalFile == null) {
            throw new IllegalStateException("未配置 mira.style.file，无法保存风格约束");
        }
        try {
            if (externalFile.getParent() != null) {
                Files.createDirectories(externalFile.getParent());
            }
            Files.writeString(externalFile,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(constraint),
                    StandardCharsets.UTF_8);
            this.current = constraint;
            log.info("Saved style constraint to {}", externalFile);
            return constraint;
        } catch (IOException e) {
            throw new RuntimeException("保存风格约束失败: " + e.getMessage(), e);
        }
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

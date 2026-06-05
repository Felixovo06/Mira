package com.felix.miraagent.style;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件世界书存储：多条目、可单独开关，持久化到外部 {@code worldbook.json}（JSON 数组）。
 *
 * <p>启动迁移：若 worldbook.json 不存在，则把旧的单份 {@code style.json}（或内置
 * {@code style/default.json}）包成一条名为「默认风格」的条目并写入，实现无缝升级。
 * 所有写操作在内存快照上 copy-on-write，并立即写回文件，无需重启。
 */
public class FileWorldBookStore implements StyleConstraintStore {

    private static final Logger log = LoggerFactory.getLogger(FileWorldBookStore.class);
    private static final String BUNDLED = "style/default.json";
    private static final Comparator<StyleConstraint> BY_ORDER =
            Comparator.comparingInt(StyleConstraint::getOrder)
                    .thenComparing(e -> e.getName() == null ? "" : e.getName());

    private final boolean enabled;
    private final Path file;
    private final Path legacyFile;
    private final ObjectMapper objectMapper;
    private final Object lock = new Object();
    private volatile List<StyleConstraint> entries;

    public FileWorldBookStore(StyleConstraintProperties props, ObjectMapper objectMapper) {
        this.enabled = props.isEnabled();
        this.file = resolve(props.getFile());
        this.legacyFile = resolve(props.getLegacyFile());
        this.objectMapper = objectMapper;
        this.entries = List.copyOf(load());
    }

    private static Path resolve(String p) {
        return (p == null || p.isBlank()) ? null : Paths.get(p).toAbsolutePath().normalize();
    }

    // ---- 读侧 ----

    @Override
    public List<StyleConstraint> activeEntries() {
        if (!enabled) {
            return List.of();
        }
        return entries.stream().filter(StyleConstraint::isEnabled).sorted(BY_ORDER).toList();
    }

    @Override
    public List<StyleConstraint> list() {
        return entries.stream().sorted(BY_ORDER).toList();
    }

    @Override
    public Optional<StyleConstraint> get(String id) {
        return entries.stream().filter(e -> e.getId() != null && e.getId().equals(id)).findFirst();
    }

    // ---- 写侧 ----

    @Override
    public StyleConstraint upsert(StyleConstraint entry) {
        synchronized (lock) {
            var next = new ArrayList<>(entries);
            StyleConstraint saved;
            if (entry.getId() == null || entry.getId().isBlank()) {
                int maxOrder = next.stream().mapToInt(StyleConstraint::getOrder).max().orElse(0);
                saved = entry.toBuilder()
                        .id(UUID.randomUUID().toString())
                        .order(entry.getOrder() != 0 ? entry.getOrder() : maxOrder + 1)
                        .build();
                next.add(saved);
            } else {
                saved = entry;
                next.removeIf(e -> saved.getId().equals(e.getId()));
                next.add(saved);
            }
            persist(next);
            return saved;
        }
    }

    @Override
    public boolean delete(String id) {
        synchronized (lock) {
            var next = new ArrayList<>(entries);
            boolean removed = next.removeIf(e -> id != null && id.equals(e.getId()));
            if (removed) {
                persist(next);
            }
            return removed;
        }
    }

    @Override
    public Optional<StyleConstraint> setEnabled(String id, boolean on) {
        synchronized (lock) {
            var current = get(id);
            if (current.isEmpty()) {
                return Optional.empty();
            }
            StyleConstraint updated = current.get().toBuilder().enabled(on).build();
            var next = new ArrayList<>(entries);
            next.removeIf(e -> id.equals(e.getId()));
            next.add(updated);
            persist(next);
            return Optional.of(updated);
        }
    }

    // ---- 加载 / 迁移 / 落盘 ----

    private List<StyleConstraint> load() {
        if (file != null && Files.exists(file)) {
            try {
                List<StyleConstraint> loaded = objectMapper.readValue(
                        Files.readString(file, StandardCharsets.UTF_8),
                        new TypeReference<List<StyleConstraint>>() {});
                return ensureIds(loaded);
            } catch (IOException e) {
                log.warn("读取世界书文件 {} 失败: {}", file, e.getMessage());
            }
        }
        // 迁移：旧单份 style.json -> 一条；否则内置 default.json -> 一条
        StyleConstraint legacy = loadLegacySingle();
        List<StyleConstraint> migrated = new ArrayList<>();
        if (legacy != null) {
            migrated.add(legacy.toBuilder()
                    .id(UUID.randomUUID().toString())
                    .name(legacy.getName() == null || legacy.getName().isBlank() ? "默认风格" : legacy.getName())
                    .order(0)
                    .build());
            log.info("世界书迁移：旧风格约束已转为 1 条条目并写入 {}", file);
            persistQuietly(migrated);
        }
        return migrated;
    }

    private StyleConstraint loadLegacySingle() {
        if (legacyFile != null && Files.exists(legacyFile)) {
            try {
                return objectMapper.readValue(
                        Files.readString(legacyFile, StandardCharsets.UTF_8), StyleConstraint.class);
            } catch (IOException e) {
                log.warn("读取旧 style.json {} 失败: {}", legacyFile, e.getMessage());
            }
        }
        Resource bundled = new ClassPathResource(BUNDLED);
        if (bundled.exists()) {
            try (var in = bundled.getInputStream()) {
                return objectMapper.readValue(in, StyleConstraint.class);
            } catch (IOException e) {
                log.warn("读取内置 {} 失败: {}", BUNDLED, e.getMessage());
            }
        }
        return null;
    }

    /** 老文件里若有条目缺 id，补齐并落盘一次。 */
    private List<StyleConstraint> ensureIds(List<StyleConstraint> loaded) {
        boolean dirty = false;
        var fixed = new ArrayList<StyleConstraint>(loaded.size());
        for (StyleConstraint e : loaded) {
            if (e.getId() == null || e.getId().isBlank()) {
                fixed.add(e.toBuilder().id(UUID.randomUUID().toString()).build());
                dirty = true;
            } else {
                fixed.add(e);
            }
        }
        if (dirty) {
            persistQuietly(fixed);
        }
        return fixed;
    }

    private void persist(List<StyleConstraint> next) {
        if (file == null) {
            throw new IllegalStateException("未配置 mira.style.file，无法保存世界书");
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(next),
                    StandardCharsets.UTF_8);
            this.entries = List.copyOf(next);
        } catch (IOException e) {
            throw new RuntimeException("保存世界书失败: " + e.getMessage(), e);
        }
    }

    private void persistQuietly(List<StyleConstraint> next) {
        try {
            persist(next);
        } catch (RuntimeException e) {
            log.warn("世界书写盘失败（仅内存生效）: {}", e.getMessage());
            this.entries = List.copyOf(next);
        }
    }
}

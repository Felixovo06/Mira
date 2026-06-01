package com.felix.miraagent.tools.builtin;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件工具沙箱：把相对路径解析到 base-dir 内，拒绝越界（路径穿越防护）。
 * 文件/文档工具与上传 API 共享同一套防护。
 */
public class FileSandbox {

    private final Path baseDir;

    public FileSandbox(String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    public Path baseDir() {
        return baseDir;
    }

    /** 解析并校验：返回 base-dir 内的归一化绝对路径，越界抛 IllegalArgumentException。 */
    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path resolved = baseDir.resolve(relative).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("path escapes sandbox: " + relative);
        }
        return resolved;
    }

    public void ensureBaseDir() throws IOException {
        java.nio.file.Files.createDirectories(baseDir);
    }
}

package com.felix.miraagent.api.controller;

import com.felix.miraagent.tools.builtin.FileSandbox;
import com.felix.miraagent.tools.builtin.FileToolProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文档工作区 API：上传 / 列表 / 下载 / 删除。文件落在与文档工具同一个沙箱
 * （{@code mira.tools.file.base-dir}），上传后 agent 可经 document_read/document_write 处理，
 * 编辑结果再由用户下载取回。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final FileSandbox sandbox;

    public DocumentController(FileToolProperties properties) {
        this.sandbox = new FileSandbox(properties.getBaseDir());
    }

    @GetMapping
    public ResponseEntity<List<DocumentInfo>> list() throws IOException {
        Path base = sandbox.baseDir();
        if (!Files.isDirectory(base)) {
            return ResponseEntity.ok(List.of());
        }
        try (Stream<Path> entries = Files.list(base)) {
            List<DocumentInfo> docs = entries
                    .filter(Files::isRegularFile)
                    .map(DocumentController::toInfo)
                    .sorted((a, b) -> b.modified().compareTo(a.modified()))
                    .toList();
            return ResponseEntity.ok(docs);
        }
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Empty file");
        }
        String name = sanitizeName(file.getOriginalFilename());
        sandbox.ensureBaseDir();
        Path target;
        try {
            target = sandbox.resolve(name);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return ResponseEntity.ok(toInfo(target));
    }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> download(@PathVariable("name") String name) throws IOException {
        Path file;
        try {
            file = sandbox.resolve(sanitizeName(name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(file));
        String encoded = java.net.URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable("name") String name) throws IOException {
        Path file;
        try {
            file = sandbox.resolve(sanitizeName(name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Files.deleteIfExists(file);
        return ResponseEntity.noContent().build();
    }

    /** 只取文件名部分，剥离任何路径分隔符，沙箱再兜底防穿越。 */
    private static String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("invalid filename");
        }
        return name;
    }

    private static DocumentInfo toInfo(Path p) {
        long size = 0;
        String modified = "";
        try {
            size = Files.size(p);
            modified = Files.getLastModifiedTime(p).toInstant().toString();
        } catch (IOException ignored) {
            // 列举时个别文件取属性失败不影响整体
        }
        return new DocumentInfo(p.getFileName().toString(), size, modified);
    }

    public record DocumentInfo(String name, long size, String modified) {
    }
}

package com.felix.miraagent.api.controller;

import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolRegistry;
import com.felix.miraagent.tools.ToolResolveContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可用工具列表 API：供 Web UI 渲染工具开关/权限页，包含 MCP 桥接进来的工具。
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;

    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public ResponseEntity<List<ToolInfo>> list() {
        List<ToolInfo> tools = toolRegistry.listAvailable(ToolResolveContext.builder().build()).stream()
                .map(ToolController::toInfo)
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
        return ResponseEntity.ok(tools);
    }

    private static ToolInfo toInfo(ToolDefinition d) {
        return new ToolInfo(d.getName(), d.getDescription(),
                d.getRiskLevel() != null ? d.getRiskLevel().name() : "LOW",
                d.isEnabledByDefault());
    }

    public record ToolInfo(String name, String description, String riskLevel, boolean enabledByDefault) {
    }
}

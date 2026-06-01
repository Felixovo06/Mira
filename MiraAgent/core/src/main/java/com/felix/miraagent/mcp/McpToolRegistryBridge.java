package com.felix.miraagent.mcp;

import com.felix.miraagent.tools.ToolDefinition;
import com.felix.miraagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动时把各 MCP server 发现的工具注册进统一 {@link ToolRegistry}，
 * 使其与内部工具共享同一套权限 / trace / tool result 管理。
 * <p>单个 server 失败不影响其它 server（记录日志后跳过），降级而非崩溃。
 */
public class McpToolRegistryBridge {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistryBridge.class);

    /**
     * 注册所有 client 的工具。
     *
     * @return 注册结果（每个 server 暴露的工具名）
     */
    public List<Registered> registerAll(List<McpClient> clients, ToolRegistry registry) {
        List<Registered> all = new ArrayList<>();
        for (McpClient client : clients) {
            McpServerConfig config = client.config();
            if (!config.isEnabled()) {
                log.info("MCP server '{}' disabled, skipping", config.getId());
                continue;
            }
            try {
                all.addAll(registerServer(client, registry));
            } catch (Exception e) {
                log.warn("Failed to register MCP server '{}': {}", config.getId(), e.getMessage());
            }
        }
        return all;
    }

    private List<Registered> registerServer(McpClient client, ToolRegistry registry) {
        McpServerConfig config = client.config();
        client.initialize();
        List<McpToolDescriptor> tools = client.listTools();
        String prefix = config.effectivePrefix();
        List<Registered> registered = new ArrayList<>();
        for (McpToolDescriptor tool : tools) {
            String exposedName = sanitize(prefix + tool.getName());
            Map<String, Object> schema = tool.getInputSchema() != null
                    ? tool.getInputSchema()
                    : Map.of("type", "object", "properties", Map.of());
            ToolDefinition definition = ToolDefinition.builder()
                    .name(exposedName)
                    .description(decorate(config.getId(), tool.getDescription()))
                    .inputSchema(schema)
                    .riskLevel(config.getToolRiskLevel())
                    .build();
            registry.register(definition, new McpToolAdapter(client, tool.getName(), exposedName));
            registered.add(new Registered(config.getId(), tool.getName(), exposedName));
            log.info("Registered MCP tool '{}' from server '{}'", exposedName, config.getId());
        }
        return registered;
    }

    private String decorate(String serverId, String description) {
        String base = description == null ? "" : description;
        return "[MCP:" + serverId + "] " + base;
    }

    /** 工具名收敛到模型可接受的字符集 ^[a-zA-Z0-9_-]{1,64}$。 */
    private String sanitize(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    /** 一条注册记录。 */
    public record Registered(String serverId, String remoteName, String exposedName) {
    }
}

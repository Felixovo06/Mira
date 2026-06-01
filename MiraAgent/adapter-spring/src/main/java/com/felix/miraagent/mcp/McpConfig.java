package com.felix.miraagent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.tools.ToolRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 子系统装配。仅当 {@code mira.mcp.enabled=true} 时生效；
 * 默认不创建任何 bean，对现有链路零影响。
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "mira.mcp", name = "enabled", havingValue = "true")
public class McpConfig {

    @Bean
    public McpServerConnections mcpServerConnections(McpProperties properties,
                                                     ToolRegistry toolRegistry,
                                                     ObjectMapper objectMapper) {
        return new McpServerConnections(properties, toolRegistry, objectMapper);
    }
}

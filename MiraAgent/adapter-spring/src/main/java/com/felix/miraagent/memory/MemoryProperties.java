package com.felix.miraagent.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private String baseDir = System.getProperty("user.home") + "/.miraagent/memory";
}

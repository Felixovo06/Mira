package com.felix.miraagent.agent.compression;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mira.summary")
public class SummaryProperties {
    private String baseDir = System.getProperty("user.home") + "/.miraagent/summary";
}

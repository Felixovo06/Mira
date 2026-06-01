package com.felix.miraagent.memory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {

    @Bean
    public MemoryStore memoryFileStore(MemoryProperties memoryProperties) {
        return new MemoryFileStore(memoryProperties.getBaseDir());
    }

    @Bean
    public SerializedMemoryWriter blockingQueueMemoryWriter(MemoryStore memoryStore) {
        return new BlockingQueueMemoryWriter(memoryStore);
    }
}

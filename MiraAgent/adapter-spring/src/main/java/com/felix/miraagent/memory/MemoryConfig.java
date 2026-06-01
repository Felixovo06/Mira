package com.felix.miraagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.memory.jdbc.JdbcHybridMemoryRetriever;
import com.felix.miraagent.memory.jdbc.JdbcMemoryIndexRepository;
import com.felix.miraagent.memory.jdbc.JdbcMemoryRetriever;
import com.felix.miraagent.memory.jdbc.MemoryIndexRebuildService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties({MemoryProperties.class, EmbeddingProperties.class})
public class MemoryConfig {

    @Bean
    public MemoryStore memoryFileStore(MemoryProperties memoryProperties) {
        return new MemoryFileStore(memoryProperties.getBaseDir());
    }

    @Bean
    public SerializedMemoryWriter blockingQueueMemoryWriter(MemoryStore memoryStore) {
        return new BlockingQueueMemoryWriter(memoryStore);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public MemoryIndexRepository memoryIndexRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcMemoryIndexRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public MemoryIndexRebuildService memoryIndexRebuildService(MemoryStore memoryStore,
                                                               MemoryIndexRepository memoryIndexRepository,
                                                               MemoryProperties memoryProperties) {
        return new MemoryIndexRebuildService(memoryStore, memoryIndexRepository, memoryProperties);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public JdbcMemoryRetriever jdbcLexicalRetriever(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new JdbcMemoryRetriever(jdbc, objectMapper);
    }

    @Bean
    @Primary
    @ConditionalOnBean(DataSource.class)
    public MemoryRetriever hybridMemoryRetriever(JdbcMemoryRetriever lexical,
                                                 JdbcTemplate jdbc,
                                                 ObjectMapper objectMapper,
                                                 Optional<EmbeddingClient> embeddingClient) {
        return new JdbcHybridMemoryRetriever(lexical, embeddingClient.orElse(null), jdbc, objectMapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public AsyncEmbeddingIndexer asyncEmbeddingIndexer(JdbcTemplate jdbc, Optional<EmbeddingClient> embeddingClient) {
        return new AsyncEmbeddingIndexer(jdbc, embeddingClient.orElse(null));
    }

    @Bean
    @ConditionalOnProperty(prefix = "mira.embedding", name = "base-url")
    public EmbeddingClient embeddingClient(EmbeddingProperties props) {
        return new OpenAICompatibleEmbeddingClient(props);
    }
}

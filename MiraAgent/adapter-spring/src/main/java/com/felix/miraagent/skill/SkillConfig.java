package com.felix.miraagent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.config.UsableDataSourceCondition;
import com.felix.miraagent.memory.EmbeddingClient;
import com.felix.miraagent.persistence.mapper.SkillMapper;
import com.felix.miraagent.persistence.mybatis.MybatisSkillIndexRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/**
 * Skill 子系统装配（镜像 MemoryConfig）。
 * 索引仓库 conditional on usable DataSource：无 DB 时 SkillLoader 回退扫描文件 metadata。
 */
@Configuration
@EnableConfigurationProperties(SkillProperties.class)
public class SkillConfig {

    @Bean
    public SkillStore skillFileStore(SkillProperties props, ObjectMapper objectMapper) {
        return new SkillFileStore(props.getBaseDir(), objectMapper);
    }

    @Bean
    @Conditional(UsableDataSourceCondition.class)
    public SkillIndexRepository skillIndexRepository(SkillMapper skillMapper, ObjectMapper objectMapper) {
        return new MybatisSkillIndexRepository(skillMapper, objectMapper);
    }

    @Bean
    public SkillLoader skillLoader(SkillStore skillStore,
                                   java.util.Optional<SkillIndexRepository> skillIndexRepository) {
        return new DefaultSkillLoader(skillStore, skillIndexRepository.orElse(null));
    }

    @Bean
    public SkillUsageTracker skillUsageTracker(SkillStore skillStore,
                                               java.util.Optional<SkillIndexRepository> skillIndexRepository) {
        return new DefaultSkillUsageTracker(skillStore, skillIndexRepository.orElse(null));
    }

    @Bean
    @Conditional(UsableDataSourceCondition.class)
    public SkillIndexRebuildService skillIndexRebuildService(SkillStore skillStore,
                                                             SkillIndexRepository skillIndexRepository) {
        return new SkillIndexRebuildService(skillStore, skillIndexRepository);
    }

    @Bean
    @Conditional(UsableDataSourceCondition.class)
    public SkillEmbeddingIndexer skillEmbeddingIndexer(JdbcTemplate jdbcTemplate,
                                                       Optional<EmbeddingClient> embeddingClient) {
        return new SkillEmbeddingIndexer(jdbcTemplate, embeddingClient.orElse(null));
    }

    @Bean
    public SkillDeduplicator skillDeduplicator(Optional<JdbcTemplate> jdbcTemplate,
                                               Optional<EmbeddingClient> embeddingClient) {
        if (jdbcTemplate.isPresent() && embeddingClient.isPresent()) {
            return new EmbeddingSkillDeduplicator(jdbcTemplate.get(), embeddingClient.get());
        }
        return new NoOpSkillDeduplicator();
    }

    @Bean
    public SerializedSkillWriter serializedSkillWriter(SkillStore skillStore,
                                                       Optional<SkillIndexRepository> skillIndexRepository,
                                                       Optional<SkillDeduplicator> skillDeduplicator,
                                                       Optional<SkillEmbeddingIndexer> skillEmbeddingIndexer) {
        return new BlockingQueueSkillWriter(skillStore,
                skillIndexRepository.orElse(null),
                skillDeduplicator.orElse(null),
                skillEmbeddingIndexer.orElse(null));
    }

    @Bean
    public SkillManager skillManager(SerializedSkillWriter serializedSkillWriter,
                                     SkillLoader skillLoader,
                                     SkillUsageTracker skillUsageTracker) {
        return new DefaultSkillManager(serializedSkillWriter, skillLoader, skillUsageTracker);
    }

    @Bean
    public com.felix.miraagent.skill.curator.SkillSimilarityFinder skillSimilarityFinder(
            Optional<JdbcTemplate> jdbcTemplate) {
        return jdbcTemplate.<com.felix.miraagent.skill.curator.SkillSimilarityFinder>
                        map(EmbeddingSkillSimilarityFinder::new)
                .orElseGet(com.felix.miraagent.skill.curator.NoOpSkillSimilarityFinder::new);
    }

    @Bean
    public com.felix.miraagent.skill.curator.Curator curator(
            SkillLoader skillLoader,
            com.felix.miraagent.skill.curator.SkillSimilarityFinder skillSimilarityFinder) {
        return new com.felix.miraagent.skill.curator.DefaultCurator(skillLoader, skillSimilarityFinder);
    }
}

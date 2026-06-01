package com.felix.miraagent.persistence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.persistence.jdbc.JdbcSessionSearchService;
import com.felix.miraagent.persistence.jdbc.JdbcSessionStore;
import com.felix.miraagent.persistence.jdbc.JdbcTraceStore;
import com.felix.miraagent.persistence.jdbc.JdbcToolExecutionStore;
import com.felix.miraagent.config.UsableDataSourceCondition;
import com.felix.miraagent.session.SessionSearchService;
import com.felix.miraagent.session.SessionStore;
import com.felix.miraagent.session.impl.InMemorySessionStore;
import com.felix.miraagent.tools.ToolExecutionStore;
import com.felix.miraagent.tools.impl.InMemoryToolExecutionStore;
import com.felix.miraagent.trace.TraceStore;
import com.felix.miraagent.trace.impl.InMemoryTraceStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PersistenceConfig {

    @Bean
    @Primary
    public SessionStore sessionStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                     ObjectMapper objectMapper,
                                     Environment environment) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate != null
                && hasUsableDatasourceUrl(environment)
                ? new JdbcSessionStore(jdbcTemplate, objectMapper)
                : new InMemorySessionStore();
    }

    @Bean
    @Primary
    public TraceStore traceStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                 ObjectMapper objectMapper,
                                 Environment environment) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate != null
                && hasUsableDatasourceUrl(environment)
                ? new JdbcTraceStore(jdbcTemplate, objectMapper)
                : new InMemoryTraceStore();
    }

    @Bean
    @Primary
    public ToolExecutionStore toolExecutionStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                                 Environment environment) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate != null
                && hasUsableDatasourceUrl(environment)
                ? new JdbcToolExecutionStore(jdbcTemplate)
                : new InMemoryToolExecutionStore();
    }

    @Bean
    @Conditional(UsableDataSourceCondition.class)
    public SessionSearchService sessionSearchService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcSessionSearchService(jdbcTemplate, objectMapper);
    }

    private boolean hasUsableDatasourceUrl(Environment environment) {
        try {
            String url = environment.getProperty("spring.datasource.url");
            return url != null && !url.isBlank() && !url.contains("${");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

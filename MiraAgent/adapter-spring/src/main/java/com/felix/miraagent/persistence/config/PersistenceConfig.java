package com.felix.miraagent.persistence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.config.UsableDataSourceCondition;
import com.felix.miraagent.persistence.jdbc.JdbcSessionSearchService;
import com.felix.miraagent.persistence.mapper.AgentTraceMapper;
import com.felix.miraagent.persistence.mapper.MessageMapper;
import com.felix.miraagent.persistence.mapper.SessionMapper;
import com.felix.miraagent.persistence.mapper.ToolExecutionMapper;
import com.felix.miraagent.persistence.mybatis.MybatisSessionStore;
import com.felix.miraagent.persistence.mybatis.MybatisToolExecutionStore;
import com.felix.miraagent.persistence.mybatis.MybatisTraceStore;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 端口装配: 有可用 DataSource 时 Mapper 被注册(见 MapperScanConfig), 走 MyBatis-Plus 实现;
 * 否则 Mapper 不存在, 回退到 InMemory 实现。core 始终只依赖端口接口。
 */
@Configuration
public class PersistenceConfig {

    @Bean
    @Primary
    public SessionStore sessionStore(ObjectProvider<SessionMapper> sessionMapperProvider,
                                     ObjectProvider<MessageMapper> messageMapperProvider,
                                     ObjectMapper objectMapper) {
        SessionMapper sessionMapper = sessionMapperProvider.getIfAvailable();
        MessageMapper messageMapper = messageMapperProvider.getIfAvailable();
        return (sessionMapper != null && messageMapper != null)
                ? new MybatisSessionStore(sessionMapper, messageMapper, objectMapper)
                : new InMemorySessionStore();
    }

    @Bean
    @Primary
    public TraceStore traceStore(ObjectProvider<AgentTraceMapper> traceMapperProvider,
                                 ObjectMapper objectMapper) {
        AgentTraceMapper traceMapper = traceMapperProvider.getIfAvailable();
        return traceMapper != null
                ? new MybatisTraceStore(traceMapper, objectMapper)
                : new InMemoryTraceStore();
    }

    @Bean
    @Primary
    public ToolExecutionStore toolExecutionStore(ObjectProvider<ToolExecutionMapper> toolExecutionMapperProvider) {
        ToolExecutionMapper toolExecutionMapper = toolExecutionMapperProvider.getIfAvailable();
        return toolExecutionMapper != null
                ? new MybatisToolExecutionStore(toolExecutionMapper)
                : new InMemoryToolExecutionStore();
    }

    @Bean
    @Conditional(UsableDataSourceCondition.class)
    public SessionSearchService sessionSearchService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcSessionSearchService(jdbcTemplate, objectMapper);
    }
}

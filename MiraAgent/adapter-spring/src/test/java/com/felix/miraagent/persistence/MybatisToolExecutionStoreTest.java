package com.felix.miraagent.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.felix.miraagent.model.ToolCall;
import com.felix.miraagent.persistence.mapper.ToolExecutionMapper;
import com.felix.miraagent.persistence.mybatis.MybatisToolExecutionStore;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolStatus;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

class MybatisToolExecutionStoreTest {
    private MybatisToolExecutionStore store;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:tool_exec_mp;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        new JdbcTemplate(dataSource).execute("""
                create table if not exists tool_executions (
                    id text primary key,
                    run_id text not null,
                    session_id text not null,
                    tool_call_id text not null,
                    tool_name text not null,
                    arguments varchar,
                    status text not null,
                    model_visible_content text,
                    error_message text,
                    started_at timestamp with time zone not null,
                    finished_at timestamp with time zone
                )
                """);
        store = new MybatisToolExecutionStore(mapper(dataSource));
    }

    private ToolExecutionMapper mapper(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        sqlSessionFactory.getConfiguration().addMapper(ToolExecutionMapper.class);
        SqlSession sqlSession = sqlSessionFactory.openSession(true);
        return sqlSession.getMapper(ToolExecutionMapper.class);
    }

    @Test
    void recordsAndFindsToolExecutionByRun() {
        ToolCall call = ToolCall.builder()
                .id("tc1")
                .name("note")
                .arguments("{\"content\":\"hello\"}")
                .build();
        ToolExecutionResult result = ToolExecutionResult.success("tc1", "note", "Note saved: hello");

        store.record("run-1", "session-1", call, result);

        var records = store.findByRunId("run-1");
        assertEquals(1, records.size());
        assertEquals("tc1", records.get(0).getToolCallId());
        assertEquals("note", records.get(0).getToolName());
        assertEquals(ToolStatus.SUCCESS, records.get(0).getStatus());
        assertTrue(records.get(0).getArguments().contains("hello"));
    }
}

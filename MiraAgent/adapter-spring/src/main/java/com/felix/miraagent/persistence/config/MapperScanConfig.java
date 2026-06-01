package com.felix.miraagent.persistence.config;

import com.felix.miraagent.config.UsableDataSourceCondition;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 仅在存在可用 DataSource 时才注册 MyBatis Mapper。
 * 无 DB(无 spring.datasource.url)时不扫描, 避免 SqlSessionFactory 缺失导致启动失败,
 * 并让 PersistenceConfig 回退到 InMemory 实现。
 */
@Configuration
@Conditional(UsableDataSourceCondition.class)
@MapperScan("com.felix.miraagent.persistence.mapper")
public class MapperScanConfig {
}

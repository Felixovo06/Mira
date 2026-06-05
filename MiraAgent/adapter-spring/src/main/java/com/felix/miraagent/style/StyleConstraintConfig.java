package com.felix.miraagent.style;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 世界书子系统装配（多条目、可单独开关）。
 */
@Configuration
@EnableConfigurationProperties(StyleConstraintProperties.class)
public class StyleConstraintConfig {

    @Bean
    public StyleConstraintStore styleConstraintStore(StyleConstraintProperties props,
                                                     ObjectMapper objectMapper) {
        return new FileWorldBookStore(props, objectMapper);
    }
}

package com.felix.miraagent.style;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局风格约束子系统装配。
 */
@Configuration
@EnableConfigurationProperties(StyleConstraintProperties.class)
public class StyleConstraintConfig {

    @Bean
    public StyleConstraintProvider styleConstraintProvider(StyleConstraintProperties props,
                                                           ObjectMapper objectMapper) {
        return new FileStyleConstraintProvider(props, objectMapper);
    }
}

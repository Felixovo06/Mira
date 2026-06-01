package com.felix.miraagent.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class UsableDataSourceCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            String url = context.getEnvironment().getProperty("spring.datasource.url");
            return url != null && !url.isBlank() && !url.contains("${");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

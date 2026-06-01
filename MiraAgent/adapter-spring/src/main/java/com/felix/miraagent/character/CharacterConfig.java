package com.felix.miraagent.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 角色卡子系统装配。
 */
@Configuration
@EnableConfigurationProperties(CharacterProperties.class)
public class CharacterConfig {

    @Bean
    public CharacterRepository characterRepository(CharacterProperties props, ObjectMapper objectMapper) {
        return new FileCharacterRepository(props.getBaseDir(), objectMapper);
    }
}

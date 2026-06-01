package com.felix.miraagent.character;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 角色卡外部目录。内置示例卡随 classpath(resources/characters/*.json) 加载，
 * 此目录用于用户导入的卡（同 id 覆盖内置）。
 */
@Data
@ConfigurationProperties(prefix = "mira.character")
public class CharacterProperties {
    private String baseDir = System.getProperty("user.home") + "/.miraagent/characters";
}

package com.felix.miraagent.style;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 世界书配置。多条目持久化到外部 {@code file}（worldbook.json）；首次启动若该文件不存在，
 * 从旧 {@code legacyFile}（style.json）或内置 {@code style/default.json} 迁移为一条条目。
 * {@code enabled=false} 时整层关闭（不注入任何条目）。
 */
@Data
@ConfigurationProperties(prefix = "mira.style")
public class StyleConstraintProperties {

    /** 子系统总开关；false 时不注入任何世界书条目（运维级，无需改 JSON）。 */
    private boolean enabled = true;

    /** 世界书外部文件（JSON 数组），读写均在此。 */
    private String file = System.getProperty("user.home") + "/.miraagent/worldbook.json";

    /** 旧版单份风格约束文件，仅用于首次迁移。 */
    private String legacyFile = System.getProperty("user.home") + "/.miraagent/style.json";
}

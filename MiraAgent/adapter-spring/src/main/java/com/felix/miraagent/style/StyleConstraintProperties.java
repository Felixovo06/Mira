package com.felix.miraagent.style;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局风格约束配置。内置 classpath(style/default.json) 提供默认值，
 * 外部 {@code file} 存在时覆盖内置。{@code enabled=false} 时整层关闭。
 */
@Data
@ConfigurationProperties(prefix = "mira.style")
public class StyleConstraintProperties {

    /** 子系统总开关；false 时不注入任何风格约束（运维级，无需改 JSON）。 */
    private boolean enabled = true;

    /** 外部覆盖文件；存在则覆盖内置 style/default.json。 */
    private String file = System.getProperty("user.home") + "/.miraagent/style.json";
}

package com.felix.miraagent.weixin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mira.weixin")
public class WeixinProperties {
    private boolean enabled = false;
    private String token = "";
    private String baseUrl = "https://ilinkai.weixin.qq.com";
    private String characterId = "default";
}

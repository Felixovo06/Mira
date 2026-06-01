package com.felix.miraagent.weixin.config;

import com.felix.miraagent.agent.AgentRuntime;
import com.felix.miraagent.weixin.client.ILinkClient;
import com.felix.miraagent.weixin.client.RuntimeConfig;
import com.felix.miraagent.weixin.login.QrLoginService;
import com.felix.miraagent.weixin.login.WeixinLoginService;
import com.felix.miraagent.weixin.poll.ContextTokenStore;
import com.felix.miraagent.weixin.poll.MessageDeduplicator;
import com.felix.miraagent.weixin.poll.UserSessionMapper;
import com.felix.miraagent.weixin.poll.WeixinPoller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "mira.weixin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WeixinProperties.class)
public class WeixinBotAutoConfig {

    @Bean
    public RuntimeConfig weixinRuntimeConfig(WeixinProperties properties) {
        RuntimeConfig config = new RuntimeConfig();
        config.setBaseUrl(properties.getBaseUrl());
        if (!properties.getToken().isBlank()) {
            config.setBotToken(properties.getToken());
        }
        return config;
    }

    @Bean
    public ILinkClient iLinkClient() {
        return new ILinkClient();
    }

    @Bean
    public ContextTokenStore contextTokenStore() {
        return new ContextTokenStore();
    }

    @Bean
    public MessageDeduplicator messageDeduplicator() {
        return new MessageDeduplicator();
    }

    @Bean
    public UserSessionMapper userSessionMapper() {
        return new UserSessionMapper();
    }

    @Bean
    public WeixinPoller weixinPoller(AgentRuntime agentRuntime, ILinkClient iLinkClient,
                                     RuntimeConfig weixinRuntimeConfig, ContextTokenStore contextTokenStore,
                                     MessageDeduplicator messageDeduplicator, UserSessionMapper userSessionMapper,
                                     WeixinProperties properties) {
        return new WeixinPoller(agentRuntime, iLinkClient, weixinRuntimeConfig, contextTokenStore,
                messageDeduplicator, userSessionMapper, properties.getCharacterId());
    }

    @Bean
    public QrLoginService qrLoginService(WeixinProperties properties, ILinkClient iLinkClient,
                                         RuntimeConfig weixinRuntimeConfig, WeixinPoller weixinPoller) {
        return new QrLoginService(properties, iLinkClient, weixinRuntimeConfig, weixinPoller);
    }

    @Bean
    public WeixinLoginService weixinLoginService(ILinkClient iLinkClient, RuntimeConfig weixinRuntimeConfig,
                                                 WeixinPoller weixinPoller) {
        return new WeixinLoginService(iLinkClient, weixinRuntimeConfig, weixinPoller);
    }
}

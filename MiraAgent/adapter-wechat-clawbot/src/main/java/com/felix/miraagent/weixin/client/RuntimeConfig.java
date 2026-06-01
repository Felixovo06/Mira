package com.felix.miraagent.weixin.client;

public class RuntimeConfig {
    private volatile String botToken;
    private volatile String baseUrl;

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean hasToken() {
        return botToken != null && !botToken.isBlank();
    }
}

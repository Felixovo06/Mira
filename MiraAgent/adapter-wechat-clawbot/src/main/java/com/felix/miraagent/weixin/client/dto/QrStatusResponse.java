package com.felix.miraagent.weixin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QrStatusResponse {
    @JsonProperty("ret")
    private int ret;

    @JsonProperty("errmsg")
    private String errmsg;

    // wait | scaned | confirmed | scaned_but_redirect | expired
    @JsonProperty("status")
    private String status;

    @JsonProperty("bot_token")
    private String botToken;

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("base_url")
    private String baseUrl;

    public boolean isError() {
        return ret != 0;
    }
}

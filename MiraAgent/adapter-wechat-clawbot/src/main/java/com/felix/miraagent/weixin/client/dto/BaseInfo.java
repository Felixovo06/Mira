package com.felix.miraagent.weixin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseInfo {
    @JsonProperty("channel_version")
    private String channelVersion;

    public static BaseInfo defaults() {
        return new BaseInfo("2.2.0");
    }
}

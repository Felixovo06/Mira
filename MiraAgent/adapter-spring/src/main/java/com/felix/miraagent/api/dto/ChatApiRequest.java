package com.felix.miraagent.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatApiRequest {
    private String userId;
    private String sessionId;
    private String characterId;
    private String content;
    private List<String> enabledTools;
    @JsonProperty("stream")
    private boolean stream;
}

package com.felix.miraagent.weixin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SendMsg {
    @JsonProperty("to_user_id")
    private String toUserId;

    @JsonProperty("context_token")
    private String contextToken;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("message_type")
    private int messageType = 2;

    @JsonProperty("message_state")
    private int messageState = 2;

    @JsonProperty("item_list")
    private List<MessageItem> itemList;
}

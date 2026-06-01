package com.felix.miraagent.weixin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageItem {
    @JsonProperty("type")
    private int type;

    @JsonProperty("text_item")
    private TextItem textItem;
}

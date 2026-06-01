package com.felix.miraagent.session;

import com.felix.miraagent.model.Message;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AnchoredMessageView {
    Message anchorMessage;
    List<Message> contextBefore;
    List<Message> contextAfter;
    double relevanceScore;
}

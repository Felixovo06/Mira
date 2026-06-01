package com.felix.miraagent.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StreamDelta {
    String textDelta;
    ToolCall toolCallDelta;
    String finishReason;
    boolean done;
}

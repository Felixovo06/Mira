package com.felix.miraagent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ToolCall {
    String id;
    String name;
    String arguments;
}

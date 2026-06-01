package com.felix.miraagent.agent.compression;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SessionLineage {
    String sessionId;
    String parentSessionId;
    List<String> checkpointIds;
}

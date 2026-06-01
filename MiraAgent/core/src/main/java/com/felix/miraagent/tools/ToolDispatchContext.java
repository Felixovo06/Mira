package com.felix.miraagent.tools;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ToolDispatchContext {
    String runId;
    String sessionId;
    String userId;
    String characterId;
    ToolPermissionPolicy permissionPolicy;
}

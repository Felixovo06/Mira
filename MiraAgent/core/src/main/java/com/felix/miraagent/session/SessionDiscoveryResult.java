package com.felix.miraagent.session;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SessionDiscoveryResult {
    String sessionId;
    List<AnchoredMessageView> anchors;
}

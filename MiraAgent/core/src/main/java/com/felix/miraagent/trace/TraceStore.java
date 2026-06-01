package com.felix.miraagent.trace;

import java.util.List;

public interface TraceStore {
    void record(TraceEvent event);

    List<TraceEvent> findByRunId(String runId);

    List<TraceEvent> findBySessionId(String sessionId);
}

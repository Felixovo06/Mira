package com.felix.miraagent.trace.impl;

import com.felix.miraagent.trace.TraceEvent;
import com.felix.miraagent.trace.TraceStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryTraceStore implements TraceStore {

    private final List<TraceEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(TraceEvent event) {
        events.add(event);
    }

    @Override
    public List<TraceEvent> findByRunId(String runId) {
        return events.stream().filter(e -> runId.equals(e.getRunId())).toList();
    }

    @Override
    public List<TraceEvent> findBySessionId(String sessionId) {
        return events.stream().filter(e -> sessionId.equals(e.getSessionId())).toList();
    }
}

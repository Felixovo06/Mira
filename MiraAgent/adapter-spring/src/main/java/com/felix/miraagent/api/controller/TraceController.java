package com.felix.miraagent.api.controller;

import com.felix.miraagent.trace.TraceEvent;
import com.felix.miraagent.trace.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traces")
public class TraceController {

    private final TraceStore traceStore;

    public TraceController(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @GetMapping("/{runId}")
    public ResponseEntity<List<TraceEvent>> getTrace(@PathVariable String runId) {
        return ResponseEntity.ok(traceStore.findByRunId(runId));
    }
}

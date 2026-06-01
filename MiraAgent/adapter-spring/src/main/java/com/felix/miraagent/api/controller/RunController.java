package com.felix.miraagent.api.controller;

import com.felix.miraagent.agent.AgentRuntime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final AgentRuntime agentRuntime;

    public RunController(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @PostMapping("/{runId}/interrupt")
    public ResponseEntity<Map<String, String>> interrupt(@PathVariable String runId) {
        agentRuntime.interrupt(runId);
        return ResponseEntity.ok(Map.of("message", "interrupt signal sent", "runId", runId));
    }
}

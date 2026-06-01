package com.felix.miraagent.eval.model;

import java.util.List;
import java.util.Map;

/**
 * 一次真实运行的观测结果（从 SSE 流 + done 事件采集）。
 *
 * @param ok            运行是否正常完成（收到 done、无 error）
 * @param error         失败原因（ok=false 时）
 * @param ttftMs        首 token 延迟（Time To First Token）
 * @param totalMs       端到端总耗时
 * @param toolCalls     按调用顺序的 (工具名 -> 参数JSON)
 * @param toolStatuses  各工具执行状态（来自 done.toolExecutions）
 * @param inputTokens   输入 token
 * @param outputTokens  输出 token
 * @param finalContent  最终回复正文
 */
public record RunOutcome(
        boolean ok,
        String error,
        String sessionId,
        long ttftMs,
        long totalMs,
        List<Map<String, String>> toolCalls,
        Map<String, String> toolStatuses,
        int inputTokens,
        int outputTokens,
        String finalContent) {

    public List<String> toolNames() {
        return toolCalls.stream().map(tc -> tc.get("name")).toList();
    }

    public boolean calledTool(String name) {
        return toolNames().contains(name);
    }

    public boolean calledAnyTool() {
        return !toolCalls.isEmpty();
    }
}

package com.felix.miraagent.eval.model;

import java.util.List;
import java.util.Map;

/**
 * 一条评测用例（把 Agent 当黑盒，只描述输入与期望）。
 *
 * @param id            用例 id
 * @param category      场景分类：basic_chat / tool_call / persona / edge_case
 * @param userMessage   发给 Agent 的用户消息
 * @param characterId   角色卡 id（persona 场景用，可空）
 * @param enabledTools  本轮放行的工具（空则用默认）
 * @param expectedTool  期望被调用的工具名（Layer1 工具选择准确率；不期望工具则为 null）
 * @param expectedParams 期望的工具参数子集（值做子串匹配；可空）
 * @param expectNoTool  是否期望"不调用任何工具"（no-tool 准确率）
 * @param assertContains 最终回复应包含的关键字（轻量事实断言；可空）
 */
public record EvalCase(
        String id,
        String category,
        String userMessage,
        String characterId,
        List<String> enabledTools,
        String expectedTool,
        Map<String, String> expectedParams,
        Boolean expectNoTool,
        List<String> assertContains) {

    public boolean expectsNoTool() {
        return Boolean.TRUE.equals(expectNoTool);
    }
}

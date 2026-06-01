package com.felix.miraagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.felix.miraagent.eval.model.JudgeScores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** eval 模块的纯逻辑单测——不依赖模型/服务，可进 CI。 */
class EvalLogicTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void judgeMedianIgnoresNullsAndPicksMiddle() {
        JudgeScores m = JudgeScores.median(List.of(
                new JudgeScores(3, null, 4, 3),
                new JudgeScores(5, null, 2, 4),
                new JudgeScores(4, null, 3, 5)));
        assertEquals(4, m.relevance());          // 中位 of 3,5,4
        assertNull(m.personaConsistency());      // 全 null
        assertEquals(3, m.toolUsage());          // 中位 of 4,2,3
        assertEquals(4, m.overall());
    }

    @Test
    void diffFlagsRegressionWhenAccuracyDrops() {
        ObjectNode base = mapper.createObjectNode();
        base.putObject("layer1_unit").put("tool_selection_accuracy", 1.0);
        ObjectNode cur = mapper.createObjectNode();
        cur.putObject("layer1_unit").put("tool_selection_accuracy", 0.8);

        ObjectNode diff = ReportDiff.diff(mapper, base, cur, 0.05);
        assertEquals(1, diff.get("regression_count").asInt());
        assertEquals(0, diff.get("improvement_count").asInt());
    }

    @Test
    void diffTreatsLowerLatencyAsImprovement() {
        ObjectNode base = mapper.createObjectNode();
        base.putObject("layer2_chain").put("latency_ms_avg", 8000);
        ObjectNode cur = mapper.createObjectNode();
        cur.putObject("layer2_chain").put("latency_ms_avg", 5000);

        ObjectNode diff = ReportDiff.diff(mapper, base, cur, 0.05);
        assertEquals(1, diff.get("improvement_count").asInt());
        assertEquals(0, diff.get("regression_count").asInt());
    }

    @Test
    void diffIgnoresChangesWithinTolerance() {
        ObjectNode base = mapper.createObjectNode();
        base.putObject("layer2_chain").put("latency_ms_avg", 8000);
        ObjectNode cur = mapper.createObjectNode();
        cur.putObject("layer2_chain").put("latency_ms_avg", 8100); // +1.25% < 5%

        ObjectNode diff = ReportDiff.diff(mapper, base, cur, 0.05);
        assertEquals(0, diff.get("regression_count").asInt());
        assertEquals(0, diff.get("improvement_count").asInt());
    }
}

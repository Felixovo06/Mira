package com.felix.miraagent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Layer 4 回归对比：把当前 summary 与 baseline summary 的数值叶子逐项比较，
 * 超过容差的标为 regression / improvement。纯本模块逻辑，不依赖外部。
 */
public final class ReportDiff {

    /** 这些指标"越低越好"（延迟/token）；其余默认"越高越好"（准确率/分数）。 */
    private static final Set<String> LOWER_IS_BETTER = Set.of(
            "ttft_ms_avg", "ttft_ms_p95", "latency_ms_avg", "latency_ms_p95", "avg_tokens_per_turn");

    private ReportDiff() {
    }

    /**
     * @param tolerance 相对容差（如 0.05 = 5%），小于此变化视为噪声不计
     */
    public static ObjectNode diff(ObjectMapper mapper, JsonNode baselineSummary,
                                  JsonNode currentSummary, double tolerance) {
        ObjectNode diff = mapper.createObjectNode();
        ArrayNode regressions = diff.putArray("regressions");
        ArrayNode improvements = diff.putArray("improvements");

        Map<String, Double> base = flatten(baselineSummary);
        Map<String, Double> cur = flatten(currentSummary);

        for (Map.Entry<String, Double> e : cur.entrySet()) {
            String metric = e.getKey();
            Double now = e.getValue();
            Double was = base.get(metric);
            if (now == null || was == null) {
                continue;
            }
            double delta = now - was;
            double rel = Math.abs(was) < 1e-9 ? Math.abs(delta) : Math.abs(delta) / Math.abs(was);
            if (rel < tolerance) {
                continue; // 噪声内
            }
            boolean lowerBetter = LOWER_IS_BETTER.stream().anyMatch(metric::endsWith);
            boolean better = lowerBetter ? delta < 0 : delta > 0;
            ObjectNode row = mapper.createObjectNode();
            row.put("metric", metric);
            row.put("baseline", was);
            row.put("current", now);
            row.put("delta", round(delta));
            (better ? improvements : regressions).add(row);
        }
        diff.put("regression_count", regressions.size());
        diff.put("improvement_count", improvements.size());
        return diff;
    }

    /** 把嵌套 summary 拍平成 path->数值（只取数值叶子）。 */
    private static Map<String, Double> flatten(JsonNode node) {
        Map<String, Double> out = new java.util.LinkedHashMap<>();
        walk("", node, out);
        return out;
    }

    private static void walk(String prefix, JsonNode node, Map<String, Double> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> f = it.next();
                walk(prefix.isEmpty() ? f.getKey() : prefix + "." + f.getKey(), f.getValue(), out);
            }
        } else if (node.isNumber()) {
            out.put(prefix, node.asDouble());
        }
    }

    private static double round(double d) {
        return Math.round(d * 1000.0) / 1000.0;
    }
}

package com.felix.miraagent.eval.model;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM-as-Judge 各维度评分（1-5，不适用为 null）。
 * 多次评分取中位数以抑制单次抖动。
 */
public record JudgeScores(
        Integer relevance,
        Integer personaConsistency,
        Integer toolUsage,
        Integer overall) {

    /** 对多次评分按维度取中位数。 */
    public static JudgeScores median(List<JudgeScores> samples) {
        return new JudgeScores(
                medianOf(samples, JudgeScores::relevance),
                medianOf(samples, JudgeScores::personaConsistency),
                medianOf(samples, JudgeScores::toolUsage),
                medianOf(samples, JudgeScores::overall));
    }

    private static Integer medianOf(List<JudgeScores> samples,
                                    java.util.function.Function<JudgeScores, Integer> f) {
        List<Integer> vals = new ArrayList<>();
        for (JudgeScores s : samples) {
            Integer v = f.apply(s);
            if (v != null) {
                vals.add(v);
            }
        }
        if (vals.isEmpty()) {
            return null;
        }
        vals.sort(Integer::compareTo);
        return vals.get(vals.size() / 2);
    }
}

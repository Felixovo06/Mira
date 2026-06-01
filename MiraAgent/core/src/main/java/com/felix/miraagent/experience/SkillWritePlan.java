package com.felix.miraagent.experience;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 提炼出的一条 skill 写入计划。**schema 里没有任何"用户事实"字段**——隐私物理隔离：
 * 模型想把用户隐私写进 skill 时无格子可填，只能写进 MemoryWritePlan（docs/07 §16 决策 1）。
 */
@Value
@Builder
public class SkillWritePlan {
    String op;                  // create | patch
    String targetSkillId;       // nullable，patch 时指定
    String name;
    String description;
    String whenToUse;
    @Singular("step")
    List<String> steps;
    @Singular("toolSuggestion")
    List<String> toolSuggestions;
    @Singular("checklistItem")
    List<String> checklist;
    String sourceTraceId;
}

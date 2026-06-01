package com.felix.miraagent.skill.curator;

/**
 * 最小 Curator（docs/07 §16 决策 5）：扫描 skill 索引，产出整理建议报告。
 * 只提建议，绝不自动合并或归档；pinned skill 受保护。
 */
public interface Curator {
    CuratorReport analyze();
}

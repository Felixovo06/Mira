package com.felix.miraagent.prompt.impl;

import com.felix.miraagent.style.StyleConstraint;

import java.util.List;

/**
 * 把世界书条目渲染成 markdown 段落，供 stableSystemPrompt 注入。
 * 禁用或内容全空时返回空串，由调用方决定不加入 prompt。
 * 多条目用 {@link #composeAll(List)} 拼接，每条以条目名为小标题分隔。
 */
public class StyleConstraintComposer {

    /** 渲染多条启用条目：逐条拼接，命名条目带 {@code # 条目名} 小标题。 */
    public String composeAll(List<StyleConstraint> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (StyleConstraint sc : entries) {
            if (sc == null || !sc.isEnabled()) {
                continue;
            }
            boolean named = hasText(sc.getName());
            String body = compose(sc, named ? 2 : 1);
            if (body.isBlank()) {
                continue;
            }
            if (named) {
                sb.append("# ").append(sc.getName().trim()).append("\n\n");
            }
            sb.append(body).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 渲染单条条目（顶级标题，向后兼容）。 */
    public String compose(StyleConstraint sc) {
        return compose(sc, 1);
    }

    /** 渲染单条条目，标题从 {@code level} 级起（用于嵌入命名条目时下沉一级）。 */
    private String compose(StyleConstraint sc, int level) {
        if (sc == null || !sc.isEnabled()) {
            return "";
        }
        String h1 = "#".repeat(level);
        String h2 = "#".repeat(level + 1);
        var sb = new StringBuilder();

        if (hasText(sc.getWorldSetting())) {
            sb.append(h1).append(" 世界设定\n").append(sc.getWorldSetting().trim()).append("\n\n");
        }

        boolean hasTone = hasText(sc.getTone());
        boolean hasRules = sc.getStyleRules() != null && sc.getStyleRules().stream().anyMatch(this::hasText);
        if (hasTone || hasRules) {
            sb.append(h1).append(" 回复风格\n");
            if (hasTone) {
                sb.append(h2).append(" 语气\n").append(sc.getTone().trim()).append("\n\n");
            }
            if (hasRules) {
                sb.append(h2).append(" 规则\n");
                sc.getStyleRules().stream().filter(this::hasText)
                        .forEach(rule -> sb.append("- ").append(rule.trim()).append("\n"));
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

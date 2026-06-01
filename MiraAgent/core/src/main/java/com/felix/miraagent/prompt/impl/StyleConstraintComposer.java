package com.felix.miraagent.prompt.impl;

import com.felix.miraagent.style.StyleConstraint;

/**
 * 把 {@link StyleConstraint} 渲染成 markdown 段落，供 stableSystemPrompt 注入。
 * 禁用或内容全空时返回空串，由调用方决定不加入 prompt。
 */
public class StyleConstraintComposer {

    public String compose(StyleConstraint sc) {
        if (sc == null || !sc.isEnabled()) {
            return "";
        }
        var sb = new StringBuilder();

        if (hasText(sc.getWorldSetting())) {
            sb.append("# 世界设定\n").append(sc.getWorldSetting().trim()).append("\n\n");
        }

        boolean hasTone = hasText(sc.getTone());
        boolean hasRules = sc.getStyleRules() != null && sc.getStyleRules().stream().anyMatch(this::hasText);
        if (hasTone || hasRules) {
            sb.append("# 回复风格\n");
            if (hasTone) {
                sb.append("## 语气\n").append(sc.getTone().trim()).append("\n\n");
            }
            if (hasRules) {
                sb.append("## 规则\n");
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

package com.felix.miraagent.prompt;

import com.felix.miraagent.prompt.impl.StyleConstraintComposer;
import com.felix.miraagent.style.StyleConstraint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StyleConstraintComposerTest {

    private final StyleConstraintComposer composer = new StyleConstraintComposer();

    @Test
    void nullReturnsEmpty() {
        assertEquals("", composer.compose(null));
    }

    @Test
    void disabledReturnsEmpty() {
        var sc = StyleConstraint.builder()
                .enabled(false)
                .worldSetting("某个世界")
                .build();
        assertEquals("", composer.compose(sc));
    }

    @Test
    void allEmptyFieldsReturnEmpty() {
        var sc = StyleConstraint.builder().build();
        assertEquals("", composer.compose(sc));
    }

    @Test
    void rendersWorldSettingToneAndRules() {
        var sc = StyleConstraint.builder()
                .worldSetting("现实世界、贴近日常的陪伴场景")
                .tone("自然口语化，像熟人聊天")
                .styleRule("不使用免责声明")
                .styleRule("默认简洁")
                .build();

        String out = composer.compose(sc);

        assertTrue(out.contains("# 世界设定"));
        assertTrue(out.contains("现实世界、贴近日常的陪伴场景"));
        assertTrue(out.contains("# 回复风格"));
        assertTrue(out.contains("## 语气"));
        assertTrue(out.contains("自然口语化"));
        assertTrue(out.contains("## 规则"));
        assertTrue(out.contains("- 不使用免责声明"));
        assertTrue(out.contains("- 默认简洁"));
    }

    @Test
    void worldSettingComesBeforeStyle() {
        var sc = StyleConstraint.builder()
                .worldSetting("WORLD_MARK")
                .tone("TONE_MARK")
                .build();
        String out = composer.compose(sc);
        assertTrue(out.indexOf("WORLD_MARK") < out.indexOf("TONE_MARK"));
    }

    @Test
    void blankRulesAreSkipped() {
        var sc = StyleConstraint.builder()
                .tone("某语气")
                .styleRule("   ")
                .build();
        String out = composer.compose(sc);
        assertFalse(out.contains("- "), "空白规则不应渲染为列表项");
    }
}

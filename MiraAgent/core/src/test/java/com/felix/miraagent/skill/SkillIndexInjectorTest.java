package com.felix.miraagent.skill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SkillIndexInjectorTest {

    private SkillIndex idx(String id, String name, String desc) {
        return SkillIndex.builder()
                .skillId(id).name(name).description(desc)
                .status(SkillStatus.ACTIVE).version(1).build();
    }

    private SkillLoader loaderWith(List<SkillIndex> active) {
        return new SkillLoader() {
            @Override public List<SkillIndex> loadActiveIndex() { return active; }
            @Override public Optional<Skill> loadSkill(String skillId) {
                return Optional.of(Skill.builder()
                        .metadata(SkillMetadata.builder().skillId(skillId).build())
                        .content(SkillContent.of(skillId, "d", "完整执行步骤正文"))
                        .build());
            }
            @Override public Optional<SkillContent> loadContent(String skillId) {
                return Optional.of(SkillContent.of(skillId, "d", "完整执行步骤正文"));
            }
            @Override public String loadResource(String skillId, String relativePath) { return ""; }
            @Override public List<String> listResources(String skillId, String subDir) { return List.of(); }
        };
    }

    @Test
    void renderIncludesIndexNotBody() {
        SkillIndexInjector injector = new SkillIndexInjector(loaderWith(List.of(
                idx("code-review", "代码审查", "系统化审查复杂改动"),
                idx("write-tests", "写测试", "为新功能补单测"))));

        String rendered = injector.renderIndex();
        assertTrue(rendered.contains("code-review"));
        assertTrue(rendered.contains("write-tests"));
        assertTrue(rendered.contains("系统化审查复杂改动"));
        // 只放索引，不放完整正文
        assertFalse(rendered.contains("完整执行步骤正文"));
        // 提示角色优先 + 按需加载
        assertTrue(rendered.contains("skill_view"));
        assertTrue(rendered.contains("角色"));
    }

    @Test
    void emptyWhenNoSkills() {
        SkillIndexInjector injector = new SkillIndexInjector(loaderWith(List.of()));
        assertEquals("", injector.renderIndex());
        assertTrue(injector.resolveContext().isEmpty());
    }

    @Test
    void truncatesLongDescription() {
        String longDesc = "描".repeat(300);
        SkillIndexInjector injector = new SkillIndexInjector(loaderWith(List.of(idx("s", "n", longDesc))), 50, 200);
        String rendered = injector.renderIndex();
        assertTrue(rendered.contains("…"));
        assertFalse(rendered.contains(longDesc));
    }

    @Test
    void capsNumberOfSkills() {
        List<SkillIndex> many = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> idx("s" + i, "n" + i, "d" + i)).toList();
        SkillIndexInjector injector = new SkillIndexInjector(loaderWith(many), 50, 200);
        String rendered = injector.renderIndex();
        assertTrue(rendered.contains("其余 10 个技能略"));
    }

    @Test
    void resolveContextLoadsFullContentOnDemand() {
        SkillIndexInjector injector = new SkillIndexInjector(loaderWith(List.of(idx("code-review", "n", "d"))));
        SkillResolveContext ctx = injector.resolveContext();
        assertEquals(1, ctx.getAvailableSkills().size());
        assertTrue(ctx.resolveContent("code-review").orElseThrow().getBody().contains("完整执行步骤正文"));
    }
}

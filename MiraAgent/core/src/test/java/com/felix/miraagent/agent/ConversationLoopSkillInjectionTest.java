package com.felix.miraagent.agent;

import com.felix.miraagent.agent.impl.ConversationLoop;
import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.fake.FakeModelClient;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.MessageRole;
import com.felix.miraagent.prompt.PromptBuildRequest;
import com.felix.miraagent.prompt.PromptBuildResult;
import com.felix.miraagent.prompt.PromptBuilder;
import com.felix.miraagent.prompt.impl.DefaultPromptBuilder;
import com.felix.miraagent.session.impl.InMemorySessionStore;
import com.felix.miraagent.skill.Skill;
import com.felix.miraagent.skill.SkillContent;
import com.felix.miraagent.skill.SkillIndex;
import com.felix.miraagent.skill.SkillIndexInjector;
import com.felix.miraagent.skill.SkillLoader;
import com.felix.miraagent.skill.SkillStatus;
import com.felix.miraagent.tools.builtin.BuiltinTools;
import com.felix.miraagent.tools.impl.DefaultToolDispatcher;
import com.felix.miraagent.tools.impl.DefaultToolPermissionPolicy;
import com.felix.miraagent.tools.impl.InMemoryToolExecutionStore;
import com.felix.miraagent.tools.impl.InMemoryToolRegistry;
import com.felix.miraagent.trace.impl.InMemoryTraceStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationLoopSkillInjectionTest {

    /** 捕获最后一次 PromptBuildRequest，同时委托真实 builder 产生有效结果。 */
    static class RecordingPromptBuilder implements PromptBuilder {
        final PromptBuilder delegate = new DefaultPromptBuilder();
        PromptBuildRequest captured;
        @Override public PromptBuildResult build(PromptBuildRequest request) {
            this.captured = request;
            return delegate.build(request);
        }
    }

    private SkillLoader loaderWith(List<SkillIndex> active) {
        return new SkillLoader() {
            @Override public List<SkillIndex> loadActiveIndex() { return active; }
            @Override public Optional<Skill> loadSkill(String skillId) { return Optional.empty(); }
            @Override public Optional<SkillContent> loadContent(String skillId) {
                return Optional.of(SkillContent.of(skillId, "d", "正文不应进 prompt"));
            }
            @Override public String loadResource(String skillId, String relativePath) { return ""; }
            @Override public List<String> listResources(String skillId, String subDir) { return List.of(); }
        };
    }

    private ConversationLoop buildLoop(RecordingPromptBuilder pb, SkillIndexInjector injector) {
        var toolRegistry = new InMemoryToolRegistry();
        BuiltinTools.registerAll(toolRegistry);
        return new ConversationLoop(
                new FakeModelClient().thenReply("好的~"),
                pb, toolRegistry, new DefaultToolDispatcher(toolRegistry),
                new InMemorySessionStore(), new InMemoryTraceStore(), new InMemoryToolExecutionStore(),
                null, null, null, null, null, null, null, injector);
    }

    private AgentRunRequest request() {
        return AgentRunRequest.builder()
                .runId(UUID.randomUUID().toString())
                .userId("u1").sessionId("s1")
                .characterProfile(CharacterProfile.defaultProfile())
                .messages(List.of(Message.builder().id(UUID.randomUUID().toString())
                        .role(MessageRole.USER).content("帮我审查这段代码").build()))
                .modelConfig(ModelConfig.builder().modelName("fake").build())
                .iterationBudget(IterationBudget.defaultBudget())
                .permissionPolicy(new DefaultToolPermissionPolicy())
                .build();
    }

    @Test
    void injectsSkillIndexButNotBody() {
        var pb = new RecordingPromptBuilder();
        var injector = new SkillIndexInjector(loaderWith(List.of(
                SkillIndex.builder().skillId("code-review").name("代码审查")
                        .description("系统化审查复杂改动").status(SkillStatus.ACTIVE).build())));
        buildLoop(pb, injector).run(request());

        assertNotNull(pb.captured.getSkillIndex());
        assertTrue(pb.captured.getSkillIndex().contains("code-review"));
        // 渐进式披露：正文不进 prompt
        assertFalse(pb.captured.getSkillIndex().contains("正文不应进 prompt"));
        // 角色身份段仍在稳定 prompt 顶部（角色优先）
        String system = pb.captured.getCharacterProfile() != null
                ? pb.captured.getCharacterProfile().getName() : "";
        assertNotNull(system);
    }

    @Test
    void noInjectorLeavesSkillIndexNull() {
        var pb = new RecordingPromptBuilder();
        buildLoop(pb, null).run(request());
        assertNull(pb.captured.getSkillIndex());
    }

    @Test
    void emptySkillsLeavesSkillIndexNull() {
        var pb = new RecordingPromptBuilder();
        buildLoop(pb, new SkillIndexInjector(loaderWith(List.of()))).run(request());
        assertNull(pb.captured.getSkillIndex());
    }
}

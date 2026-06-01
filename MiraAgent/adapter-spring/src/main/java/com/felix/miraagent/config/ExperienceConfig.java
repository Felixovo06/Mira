package com.felix.miraagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.experience.ExperienceApplier;
import com.felix.miraagent.experience.ExperienceExtractor;
import com.felix.miraagent.experience.LlmExperienceExtractor;
import com.felix.miraagent.memory.SerializedMemoryWriter;
import com.felix.miraagent.model.ModelClient;
import com.felix.miraagent.skill.SkillManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * 经验提炼装配（step5）。extractor 一次 LLM 调用产出固定 schema；applier 落地到 memory/skill。
 * 触发与异步编排在 step6 的 BackgroundReview。
 */
@Configuration
public class ExperienceConfig {

    @Bean
    public ExperienceExtractor experienceExtractor(ModelClient modelClient, ObjectMapper objectMapper) {
        return new LlmExperienceExtractor(modelClient, objectMapper);
    }

    @Bean
    public ExperienceApplier experienceApplier(Optional<SerializedMemoryWriter> memoryWriter,
                                               Optional<SkillManager> skillManager) {
        return new ExperienceApplier(memoryWriter.orElse(null), skillManager.orElse(null));
    }
}

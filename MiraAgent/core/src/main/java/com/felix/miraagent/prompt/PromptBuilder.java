package com.felix.miraagent.prompt;

public interface PromptBuilder {
    PromptBuildResult build(PromptBuildRequest request);
}

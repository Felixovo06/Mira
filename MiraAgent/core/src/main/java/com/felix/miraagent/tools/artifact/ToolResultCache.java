package com.felix.miraagent.tools.artifact;

import java.util.Optional;

public interface ToolResultCache {
    Optional<String> store(ToolResultArtifact artifact);
    Optional<ToolResultArtifact> load(String uri);
}

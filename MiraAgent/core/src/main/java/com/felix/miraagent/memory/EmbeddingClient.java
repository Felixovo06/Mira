package com.felix.miraagent.memory;

import java.util.List;

public interface EmbeddingClient {
    List<Float> embed(String text);
}

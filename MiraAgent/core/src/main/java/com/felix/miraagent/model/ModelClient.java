package com.felix.miraagent.model;

public interface ModelClient {
    ChatResponse chat(ChatRequest request);

    StreamHandle streamChat(ChatRequest request, StreamCallback callback);

    boolean supports(ModelCapability capability);
}

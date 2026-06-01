package com.felix.miraagent.model;

@FunctionalInterface
public interface StreamCallback {
    void onDelta(StreamDelta delta);
}

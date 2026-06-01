package com.felix.miraagent.agent;

import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptSignal {
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    public void interrupt() {
        interrupted.set(true);
    }

    public boolean isInterrupted() {
        return interrupted.get();
    }
}

package com.nexus.common.ai.governance;

import java.util.Objects;

/** 显式调用上下文。必须用try-with-resources恢复线程池原状态；SDK回调使用capture值。 */
public final class AiInvocationScope implements AutoCloseable {
    public record Snapshot(AiCallContext context, Runnable guard) {
        public void checkActive() {
            guard.run();
        }
    }

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();
    private final Snapshot previous;

    private AiInvocationScope(Snapshot snapshot) {
        previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(snapshot));
    }

    public static AiInvocationScope open(AiCallContext context, Runnable guard) {
        return new AiInvocationScope(new Snapshot(context, guard));
    }

    public static AiInvocationScope open(Snapshot snapshot) {
        return new AiInvocationScope(snapshot);
    }

    public static Snapshot capture() {
        return CURRENT.get();
    }

    public static void checkActive() {
        Snapshot current = CURRENT.get();
        if (current != null) current.checkActive();
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}

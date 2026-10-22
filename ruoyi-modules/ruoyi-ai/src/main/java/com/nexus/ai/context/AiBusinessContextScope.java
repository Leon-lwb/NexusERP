package com.nexus.ai.context;

import com.nexus.common.tenant.helper.TenantHelper;

import java.util.function.Supplier;

/** 工作线程显式安装并恢复完整业务上下文，数据库租户也在同一作用域执行。 */
public final class AiBusinessContextScope implements AutoCloseable {
    private final AiRequestContext.Snapshot previous;

    private AiBusinessContextScope(AiRequestContext.Snapshot snapshot) {
        previous = AiRequestContext.capture();
        AiRequestContext.restore(snapshot);
    }

    public static AiBusinessContextScope open(AiRequestContext.Snapshot snapshot) {
        return new AiBusinessContextScope(snapshot);
    }

    public static <T> T call(AiRequestContext.Snapshot snapshot, Supplier<T> action) {
        try (var ignored = open(snapshot)) {
            return snapshot.tenant() == null
                    ? action.get()
                    : TenantHelper.dynamic(snapshot.tenant(), action);
        }
    }

    @Override
    public void close() {
        AiRequestContext.restore(previous);
    }
}

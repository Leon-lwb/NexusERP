package com.nexus.ai.context;

import com.nexus.common.ai.governance.AiInvocationScope;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import java.util.Objects;
import java.util.function.Supplier;

/** 所有直接注入的工具与MCP工具共用身份/取消守卫，不能只恢复执行ID而遗留其他租户业务身份。 */
public final class AiToolInvocationGuard {
    private AiToolInvocationGuard() {}

    public record Captured(
            AiInvocationScope.Snapshot invocation, AiRequestContext.Snapshot business) {
        public <T> T call(Supplier<T> action) {
            try (var scope = invocation == null ? null : AiInvocationScope.open(invocation)) {
                if (invocation != null) invocation.checkActive();
                return AiBusinessContextScope.call(business, action);
            }
        }
    }

    public static Captured capture() {
        var invocation = AiInvocationScope.capture();
        var business = AiRequestContext.capture();
        if (invocation != null) {
            var actor = invocation.context();
            if (!Objects.equals(actor.tenantId(), business.tenant())
                    || !Objects.equals(actor.userId(), business.user()))
                business =
                        AiRequestContext.trusted(
                                actor.tenantId(),
                                actor.userId(),
                                actor.userName(),
                                actor.sessionId(),
                                null,
                                null,
                                null);
        }
        return new Captured(invocation, business);
    }

    public static Object wrap(Object target) {
        var captured = capture();
        if (captured.invocation() == null && captured.business().tenant() == null) return target;
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(
                (MethodInterceptor)
                        call ->
                                captured.call(
                                        () -> {
                                            try {
                                                return call.proceed();
                                            } catch (RuntimeException | Error error) {
                                                throw error;
                                            } catch (Throwable error) {
                                                throw new IllegalStateException("工具执行失败", error);
                                            }
                                        }));
        return proxy.getProxy();
    }
}

package com.nexus.ai.context;

/**
 * AI 请求上下文持有者
 * <p>
 * 用于在异步线程池中传播请求上下文（tenantId、userId）。
 * Sa-Token 的请求上下文存储在 ThreadLocal 中，不会自动传播到异步线程，
 * 因此需要在提交异步任务前手动捕获并在异步线程中设置。
 * </p>
 *
 * @author NexusERP
 */
public final class AiRequestContext {

    private static final ThreadLocal<Long> TENANT_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new InheritableThreadLocal<>();

    private AiRequestContext() {
    }

    /**
     * 设置请求上下文
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    public static void set(Long tenantId, Long userId) {
        TENANT_ID.set(tenantId);
        USER_ID.set(userId);
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID，可能为 null
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID，可能为 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 清理上下文（必须在异步任务结束时调用，避免内存泄漏）
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}

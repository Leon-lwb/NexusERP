package com.nexus.ai.context;

/**
 * AI 请求上下文持有者
 * <p>
 * 用于在异步线程池中传播请求上下文（tenantId、userId、sessionId、modelId 等）。
 * Sa-Token 的请求上下文存储在 ThreadLocal 中，不会自动传播到异步线程，
 * 因此需要在提交异步任务前手动捕获并在异步线程中设置。
 * </p>
 * <p>
 * Token 用量采集依赖此上下文：{@code AiChatModelListener} 从中读取
 * sessionId/userId/modelId 等信息，实现按用户/模型维度的 Token 统计。
 * </p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
public final class AiRequestContext {

    private static final ThreadLocal<String> TENANT_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> USER_NAME = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> SESSION_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> MODEL_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> MODEL_NAME = new InheritableThreadLocal<>();

    /** 当前对话选中的知识库ID（供 KnowledgeGraphTool 限定图谱范围） */
    private static final ThreadLocal<Long> KNOWLEDGE_ID = new InheritableThreadLocal<>();

    /** Agent 模式下暂存最后一次 LLM 调用的 token 用量（由 AiChatModelListener 写入） */
    private static final ThreadLocal<Integer> LAST_INPUT_TOKENS = new InheritableThreadLocal<>();
    private static final ThreadLocal<Integer> LAST_OUTPUT_TOKENS = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> LAST_MODEL_ID = new InheritableThreadLocal<>();

    private AiRequestContext() {
    }

    /**
     * 设置请求上下文（基础）
     *
     * @param tenantId 租户文本ID（保留前导零）
     * @param userId   用户ID
     */
    public static void set(String tenantId, Long userId) {
        TENANT_ID.set(tenantId);
        USER_ID.set(userId);
    }

    /**
     * 设置请求上下文（完整，含 Token 采集所需字段）
     *
     * @param tenantId  租户ID
     * @param userId    用户ID
     * @param userName  用户名称
     * @param sessionId 会话ID
     * @param modelId   模型ID
     * @param modelName 模型名称
     */
    public static void set(String tenantId, Long userId, String userName,
                           Long sessionId, Long modelId, String modelName) {
        TENANT_ID.set(tenantId);
        USER_ID.set(userId);
        USER_NAME.set(userName);
        SESSION_ID.set(sessionId);
        MODEL_ID.set(modelId);
        MODEL_NAME.set(modelName);
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID，可能为 null
     */
    public static String getTenantId() {
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
     * 获取用户名称
     *
     * @return 用户名称，可能为 null
     */
    public static String getUserName() {
        return USER_NAME.get();
    }

    /**
     * 获取会话ID
     *
     * @return 会话ID，可能为 null
     */
    public static Long getSessionId() {
        return SESSION_ID.get();
    }

    /**
     * 获取模型ID
     *
     * @return 模型ID，可能为 null
     */
    public static Long getModelId() {
        return MODEL_ID.get();
    }

    /**
     * 获取模型名称
     *
     * @return 模型名称，可能为 null
     */
    public static String getModelName() {
        return MODEL_NAME.get();
    }

    /**
     * 设置当前对话选中的知识库ID
     *
     * @param knowledgeId 知识库ID
     */
    public static void setKnowledgeId(Long knowledgeId) {
        KNOWLEDGE_ID.set(knowledgeId);
    }

    /**
     * 获取当前对话选中的知识库ID
     *
     * @return 知识库ID，可能为 null
     */
    public static Long getKnowledgeId() {
        return KNOWLEDGE_ID.get();
    }

    /**
     * 清理上下文（必须在异步任务结束时调用，避免内存泄漏）
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        USER_NAME.remove();
        SESSION_ID.remove();
        MODEL_ID.remove();
        MODEL_NAME.remove();
        KNOWLEDGE_ID.remove();
        LAST_INPUT_TOKENS.remove();
        LAST_OUTPUT_TOKENS.remove();
        LAST_MODEL_ID.remove();
    }

    /**
     * 设置最后一次 LLM 调用的 Token 用量（由 AiChatModelListener 调用）
     *
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param modelId      模型ID
     */
    public static void setLastTokenUsage(int inputTokens, int outputTokens, Long modelId) {
        LAST_INPUT_TOKENS.set(inputTokens);
        LAST_OUTPUT_TOKENS.set(outputTokens);
        LAST_MODEL_ID.set(modelId);
    }

    /**
     * 获取最后一次 LLM 调用的输入 Token 数
     *
     * @return 输入 Token 数，可能为 null
     */
    public static Integer getLastInputTokens() {
        return LAST_INPUT_TOKENS.get();
    }

    /**
     * 获取最后一次 LLM 调用的输出 Token 数
     *
     * @return 输出 Token 数，可能为 null
     */
    public static Integer getLastOutputTokens() {
        return LAST_OUTPUT_TOKENS.get();
    }

    /**
     * 获取最后一次 LLM 调用的模型ID
     *
     * @return 模型ID，可能为 null
     */
    public static Long getLastModelId() {
        return LAST_MODEL_ID.get();
    }

    /** 完整业务快照，包含知识库和最后用量；不携带登录Token。 */
    public record Snapshot(
            String tenant,
            Long user,
            String userName,
            Long session,
            Long model,
            String modelName,
            Long knowledge,
            Integer input,
            Integer output,
            Long lastModel) {}

    public static Snapshot capture() {
        return new Snapshot(
                TENANT_ID.get(),
                USER_ID.get(),
                USER_NAME.get(),
                SESSION_ID.get(),
                MODEL_ID.get(),
                MODEL_NAME.get(),
                KNOWLEDGE_ID.get(),
                LAST_INPUT_TOKENS.get(),
                LAST_OUTPUT_TOKENS.get(),
                LAST_MODEL_ID.get());
    }

    public static Snapshot trusted(
            String tenant,
            Long user,
            String name,
            Long session,
            Long model,
            String modelName,
            Long knowledge) {
        return new Snapshot(
                tenant, user, name, session, model, modelName, knowledge, null, null, null);
    }

    public static void restore(Snapshot snapshot) {
        clear();
        if (snapshot == null) return;
        TENANT_ID.set(snapshot.tenant());
        USER_ID.set(snapshot.user());
        USER_NAME.set(snapshot.userName());
        SESSION_ID.set(snapshot.session());
        MODEL_ID.set(snapshot.model());
        MODEL_NAME.set(snapshot.modelName());
        KNOWLEDGE_ID.set(snapshot.knowledge());
        LAST_INPUT_TOKENS.set(snapshot.input());
        LAST_OUTPUT_TOKENS.set(snapshot.output());
        LAST_MODEL_ID.set(snapshot.lastModel());
    }
}

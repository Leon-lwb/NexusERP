package com.nexus.ai.engineering.service;

import com.nexus.ai.engineering.domain.EngineeringTaskVo;
import com.nexus.common.ai.engineering.*;

import java.util.*;

/** 已认证工程工具上下文；模型参数不能提供身份、会话、设备或幂等命名空间。 */
public final class EngineeringToolContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private EngineeringToolContext() {}

    /** 设置当前异步任务上下文，任务结束必须清理。 */
    public static void set(Context context) {
        if (context == null) CURRENT.remove();
        else CURRENT.set(context);
    }

    /** 读取当前工具身份，线程未显式绑定时拒绝执行。 */
    public static Context current() {
        var value = CURRENT.get();
        EngineeringException.require(
                value != null, 403, "ENGINEERING_CONTEXT_REQUIRED", "工程工具缺少已认证调用上下文");
        return value;
    }

    /** 清理线程池线程，防止后续请求继承他人身份。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** 创建可恢复嵌套作用域，异常退出也清理上下文。 */
    public static Scope open(Context context) {
        var previous = CURRENT.get();
        set(context);
        return new Scope(previous);
    }

    /** 嵌套作用域的清理句柄。 */
    public static final class Scope implements AutoCloseable {
        private final Context previous;

        private Scope(Context previous) {
            this.previous = previous;
        }

        /** 恢复先前上下文。 */
        @Override
        public void close() {
            set(previous);
        }
    }

    /** 不可更改的调用身份和请求标识，真实任务回执仅由任务服务结果登记。 */
    public static final class Context {
        private final EngineeringActor actor;
        private final Long deviceId;
        private final Long sessionId;
        private final String requestId;
        private final Map<String, EngineeringTaskVo> receipts = new LinkedHashMap<>();

        /** 由服务端已认证聊天入口创建上下文。 */
        public Context(EngineeringActor actor, Long deviceId, Long sessionId, String requestId) {
            this.actor = actor;
            this.deviceId = deviceId;
            this.sessionId = sessionId;
            this.requestId = requestId;
        }

        /** 当前所有者。 */
        public EngineeringActor actor() {
            return actor;
        }

        /** 用户显式选择的设备。 */
        public Long deviceId() {
            return deviceId;
        }

        /** 当前已核对归属的会话。 */
        public Long sessionId() {
            return sessionId;
        }

        /** 本次聊天请求标识。 */
        public String requestId() {
            return requestId;
        }

        /** 登记真实服务回执，同任务不重复发卡片。 */
        public synchronized boolean record(EngineeringTaskVo receipt) {
            return receipts.putIfAbsent(receipt.taskId(), receipt) == null;
        }

        /** 获取本请求真实任务，不从模型自由文本提取。 */
        public synchronized List<EngineeringTaskVo> receipts() {
            return List.copyOf(receipts.values());
        }
    }
}

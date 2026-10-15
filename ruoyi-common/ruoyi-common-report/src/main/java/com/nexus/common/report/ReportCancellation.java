package com.nexus.common.report;

import java.util.concurrent.atomic.AtomicReference;

/** 报表取消信号，负责当前模块对应的契约或处理职责。 */
public final class ReportCancellation {
    /** 资源操作。 */
    private final AtomicReference<Runnable> action = new AtomicReference<>();

    /** 保存当前 {@code reason} 值。 */
    private volatile String reason;

    /**
     * 完成 {@code cancelled} 对应的报表取消信号处理。
     *
     * @return 当前检查或条件操作是否成功
     */
    public boolean cancelled() {
        return reason != null;
    }

    /**
     * 完成 {@code reason} 对应的报表取消信号处理。
     *
     * @return 当前处理返回的固定结果
     */
    public String reason() {
        return reason;
    }

    /**
     * 登记当前可取消资源的回调。
     *
     * @param callback 当前操作的 {@code callback} 输入
     */
    public void attach(Runnable callback) {
        action.set(callback);
        if (cancelled()) invoke(callback);
    }

    /** 释放当前取消回调。 */
    public void detach() {
        action.set(null);
    }

    /**
     * 请求取消当前用户拥有的任务。
     *
     * @param code 业务编码
     */
    public void cancel(String code) {
        reason = code;
        Runnable callback = action.get();
        if (callback != null) invoke(callback);
    }

    /** 检查取消、超时或受控状态条件。 */
    public void check() {
        if (cancelled()) throw new DatasetAccessException(reason, "报表执行已停止");
    }

    /**
     * 完成 {@code invoke} 对应的报表取消信号处理。
     *
     * @param callback 当前操作的 {@code callback} 输入
     */
    private static void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            /** 语句已关闭时取消已经生效，无需重复处理。 */
        }
    }
}

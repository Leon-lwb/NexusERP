package com.nexus.common.trace.port;

import com.nexus.common.trace.*;

/**
 * 目标库内同时提交inbox、业务效果和回执outbox；调用方只能在返回提交后ACK。
 */
public interface TraceInboxPort {
    /**
     * 返回接收日志所属业务模块。
     *
     * @return 固定的所属业务模块名称。
     */
    String targetModule();

    /**
     * 来源已验证后查询既有应用终态；无终态返回null，不能在此制造回执或放宽foreign prepare。
     */
    default TraceDeliveryResult findApplied(MaterialTraceEvent event) {
        return null;
    }

    /**
     * 在所属业务库事务中核对事件幂等、聚合顺序及内容一致性，并整体提交目标效果。
     *
     * @param event 已绑定认证租户并核验来源头的不可变事件。
     * @param handler 实际目标业务处理器，通过本地事务执行效果。
     * @param prepared 事务开始前已经准备的外部来源证据。
     * @return 目标本地事务的已提交结果，重复事件返回首次不可变结果。
     */
    TraceDeliveryResult accept(
            MaterialTraceEvent event, TraceEventHandler handler, Object prepared);
}

package com.nexus.common.trace.port;

import com.nexus.common.trace.MaterialTraceEvent;

/**
 * 来源读取在prepare完成，apply只在目标业务库事务中执行，禁止跨库写入。
 */
public interface TraceEventHandler {
    /**
     * 返回目标业务模块名称。
     *
     * @return 固定的所属业务模块名称。
     */
    String targetModule();

    /**
     * 判断处理器是否支持固定协议内的事件类型。
     *
     * @param eventType 由协议固定的业务事件类型。
     * @return 上述校验或业务条件是否成立。
     */
    boolean supports(String eventType);

    /**
     * 在目标事务开始前准备跨库来源事实，期间不持有目标业务行锁。
     *
     * @param event 已绑定认证租户并核验来源头的不可变事件。
     * @return 供本地写事务复用的已核验来源上下文。
     */
    default Object prepare(MaterialTraceEvent event) {
        return null;
    }

    /**
     * 在目标接收事务内执行业务效果，与接收日志和不可变结果一起提交。
     *
     * @param event 已绑定认证租户并核验来源头的不可变事件。
     * @param prepared 事务开始前已经准备的外部来源证据。
     * @return 与目标业务一起提交的结果载荷。
     */
    String apply(MaterialTraceEvent event, Object prepared);
}

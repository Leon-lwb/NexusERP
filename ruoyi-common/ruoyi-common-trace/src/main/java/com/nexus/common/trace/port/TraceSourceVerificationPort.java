package com.nexus.common.trace.port;

import com.nexus.common.trace.MaterialTraceEvent;

/**
 * 目标事务开始前核验已提交的来源事件；内容摘要只证明一致性，授权仍须查验来源事实。
 */
public interface TraceSourceVerificationPort {
    /**
     * 返回验证器负责的真实来源模块。
     *
     * @return 固定的所属业务模块名称。
     */
    String sourceModule();

    /**
     * 只读核验来源业务库已提交的事件及完整头信息，内容摘要不能单独替代授权。
     *
     * @param event 已绑定认证租户并核验来源头的不可变事件。
     */
    void verify(MaterialTraceEvent event);
}

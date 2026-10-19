package com.nexus.common.trace.port;

import com.nexus.common.trace.MaterialTraceEvent;
import java.util.List;

/**
 * 各模块提供自身数据源代理，append必须加入实际来源业务事务。
 */
public interface TraceOutboxPort {
    /**
     * 返回来源事件日志所属模块。
     *
     * @return 固定的所属业务模块名称。
     */
    String sourceModule();

    /**
     * 在来源业务事务内追加事件，保证业务状态与待发记录同库提交。
     *
     * @param event 已绑定认证租户并核验来源头的不可变事件。
     */
    void append(MaterialTraceEvent event);

    /**
     * 以短事务领取待发事件并建立实例租约，网络发送在事务提交后执行。
     *
     * @param owner 本次领取租约的实例标识。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param leaseSeconds 本次短领取租约的有效秒数。
     * @return 本实例领取的待发事件及其租约上下文。
     */
    List<MaterialTraceEvent> claim(String owner, int limit, int leaseSeconds);

    /**
     * 记录传输接收状态，业务最终应用仍等待目标确认。
     *
     * @param eventId 跨模块事件的稳定标识。
     * @param owner 本次领取租约的实例标识。
     */
    void brokerAccepted(String eventId, String owner);

    /**
     * 记录安全错误码并保留原事件，租约有效的原实例才可安排后续重试。
     *
     * @param eventId 跨模块事件的稳定标识。
     * @param owner 本次领取租约的实例标识。
     * @param errorCode 可审计且不包含凭据的传输错误码。
     */
    void retry(String eventId, String owner, String errorCode);

    /**
     * 在来源事务内核对原摘要并保存目标不可变结果，完成来源业务确认。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param eventId 跨模块事件的稳定标识。
     * @param contentHash 按稳定顺序计算的完整内容摘要。
     * @param resultPayload 目标业务首次提交的不可变结果载荷。
     */
    void acknowledge(String tenantId, String eventId, String contentHash, String resultPayload);
}

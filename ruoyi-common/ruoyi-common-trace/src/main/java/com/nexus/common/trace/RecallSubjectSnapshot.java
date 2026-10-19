package com.nexus.common.trace;

import java.util.List;

/**
 * 同一本地可重复读快照中的接口结果；跨库写入仍须独立核验来源授权。
 *
 * @param identityIds 待核验的跨模块稳定身份集合。
 * @param workOrders 来源范围内的实际工单集合。
 * @param truncatedByCapacity 是否因容量上限截断，不能解释为不存在关联。
 */
public record RecallSubjectSnapshot(
        List<Long> identityIds,
        List<WorkOrderTraceImpact> workOrders,
        boolean truncatedByCapacity) {
    public RecallSubjectSnapshot {
        identityIds = List.copyOf(identityIds);
        workOrders = List.copyOf(workOrders);
    }
}

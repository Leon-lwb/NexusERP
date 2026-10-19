package com.nexus.common.trace;

import com.nexus.common.trace.port.IdentityTraceReadPort;
import java.util.*;

/**
 * 由所属业务服务在同一本地读事务中调用，完整读取消耗与单件分配两侧的关系。
 */
public final class TraceRecallSnapshots {
    private TraceRecallSnapshots() {}

    public static RecallSubjectSnapshot read(
            IdentityTraceReadPort port, String tenant, String scope, Long source, int capacity) {
        if (!Set.of("BATCH", "IDENTITY").contains(scope)
                || source == null
                || source <= 0
                || capacity < 1
                || capacity > 10000) {
            throw new IllegalArgumentException("Recall snapshot scope or capacity is invalid");
        }
        var ids = new ArrayList<Long>();
        var impacts = new ArrayList<WorkOrderTraceImpact>();
        long after = 0;
        boolean more;
        do {
            int limit = Math.min(500, capacity - ids.size());
            if (limit <= 0) {
                return new RecallSubjectSnapshot(ids, impacts, true);
            }
            var page =
                    scope.equals("BATCH")
                            ? port.affectedByBatch(tenant, source, after, limit)
                            : port.affectedByIdentity(tenant, source, after, limit);
            more = page.hasMore();
            long previous = after;
            if (page.rows().size() > limit) {
                throw new IllegalStateException("Recall identity page exceeds its bound");
            }
            for (Long id : page.rows()) {
                if (id == null || id <= after) {
                    throw new IllegalStateException("Recall identity cursor did not advance");
                }
                ids.add(id);
                after = id;
            }
            if (more
                    && (after <= previous
                            || page.nextCursor() == null
                            || page.nextCursor().isBlank())) {
                throw new IllegalStateException("Recall identity cursor is missing");
            }
        } while (more);
        after = 0;
        do {
            int limit = Math.min(500, capacity - ids.size() - impacts.size());
            if (limit <= 0) {
                return new RecallSubjectSnapshot(ids, impacts, true);
            }
            var page =
                    scope.equals("BATCH")
                            ? port.unallocatedByBatch(tenant, source, after, limit)
                            : port.unallocatedByIdentity(tenant, source, after, limit);
            more = page.hasMore();
            long previous = after;
            if (page.rows().size() > limit) {
                throw new IllegalStateException("Recall work-order page exceeds its bound");
            }
            for (var impact : page.rows()) {
                if (impact == null
                        || impact.consumptionId() == null
                        || impact.consumptionId() <= after
                        || impact.mesWoId() == null
                        || impact.mesWoId() <= 0
                        || impact.unallocatedQuantity().signum() <= 0
                        || !port.sourceModule().equals(impact.module())) {
                    throw new IllegalStateException(
                            "Recall work-order evidence or cursor is invalid");
                }
                impacts.add(impact);
                after = impact.consumptionId();
            }
            if (more
                    && (after <= previous
                            || page.nextCursor() == null
                            || page.nextCursor().isBlank())) {
                throw new IllegalStateException("Recall work-order cursor is missing");
            }
        } while (more);
        return new RecallSubjectSnapshot(ids, impacts, false);
    }
}

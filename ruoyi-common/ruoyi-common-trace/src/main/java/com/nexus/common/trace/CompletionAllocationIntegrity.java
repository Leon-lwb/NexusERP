package com.nexus.common.trace;

import com.nexus.common.trace.port.CompletionAllocationPort;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核验实际组件清单及其来源身份，历史缺口保持可见，不按物料清单或当前库位补造。
 */
public final class CompletionAllocationIntegrity {
    private CompletionAllocationIntegrity() {}

    public static String hash(List<CompletionMaterialAllocation> rows) {
        return TraceHashes.sha256(
                rows.stream()
                        .map(
                                row ->
                                        TraceHashes.fingerprint(
                                                row.allocationId(),
                                                row.materialId(),
                                                row.reservationId(),
                                                row.reservationLineId(),
                                                row.postingGroupId(),
                                                row.identityId(),
                                                row.sourceStockId(),
                                                row.erpBatchId(),
                                                row.quantity().stripTrailingZeros().toPlainString(),
                                                row.sourceVersion(),
                                                row.evidenceHash(),
                                                row.sourceEventId()))
                        .collect(Collectors.joining("\n")));
    }

    public static List<CompletionMaterialAllocation> read(
            CompletionAllocationPort port,
            String tenant,
            Long id,
            int revision,
            String expectedHash) {
        var header =
                Objects.requireNonNull(port.allocationManifest(tenant, id, revision), "组件冻结清单不存在");
        require(
                tenant.equals(header.tenantId())
                        && id.equals(header.manifestId())
                        && revision == header.revision()
                        && header.lineCount() >= 0,
                "组件清单身份或行数无效");
        require(
                expectedHash != null && expectedHash.equals(header.contentHash()),
                "组件清单摘要与冻结事件不一致");
        var rows = new ArrayList<CompletionMaterialAllocation>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        boolean more;
        do {
            var page = port.allocations(tenant, id, revision, cursor, 500);
            require(page.rows().size() <= 500, "组件分页上限无效");
            rows.addAll(page.rows());
            more = page.hasMore();
            require(rows.size() <= header.lineCount(), "组件清单行数超过冻结汇总");
            if (more) {
                require(
                        !page.rows().isEmpty()
                                && page.nextCursor() != null
                                && !page.nextCursor().isBlank()
                                && cursors.add(page.nextCursor()),
                        "组件游标未向前推进");
                cursor = page.nextCursor();
            } else {
                require(page.nextCursor() == null || page.nextCursor().isBlank(), "组件清单结束标记冲突");
            }
        } while (more);
        require(rows.size() == header.lineCount(), "组件清单未完整读取");
        long previous = 0;
        Set<Long> serials = new HashSet<>();
        for (var row : rows) {
            require(
                    row.allocationId() != null
                            && row.allocationId() > previous
                            && row.materialId() != null
                            && row.materialId() > 0
                            && row.quantity() != null
                            && row.quantity().signum() > 0
                            && row.sourceVersion() > 0,
                    "组件清单行序、物料或数量无效");
            previous = row.allocationId();
            require(
                    (row.reservationId() != null) != (row.postingGroupId() != null),
                    "组件实际用料必须引用唯一原预留或预领料");
            require(
                    row.reservationId() == null
                            || row.reservationId() > 0
                                    && row.reservationLineId() != null
                                    && row.reservationLineId() > 0,
                    "组件预留来源不完整");
            require(
                    row.evidenceHash() != null
                            && row.evidenceHash().matches("[0-9a-f]{64}")
                            && row.sourceEventId() != null
                            && !row.sourceEventId().isBlank(),
                    "组件来源证据缺失");
            if (row.identityId() != null) {
                require(
                        row.identityId() > 0
                                && serials.add(row.identityId())
                                && row.quantity().compareTo(BigDecimal.ONE) == 0,
                        "组件SN必须是唯一整件实际消耗");
            }
        }
        require(hash(rows).equals(header.contentHash()), "组件分页内容与冻结摘要不一致");
        return List.copyOf(rows);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

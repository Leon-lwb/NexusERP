package com.nexus.common.trace;

import com.nexus.common.trace.port.CompletionManifestPort;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 在开启目标数据库事务前核验完整的不可变交付清单。
 */
public final class CompletionManifestIntegrity {
    private CompletionManifestIntegrity() {}

    /**
     * 按稳定行序冻结的完整清单与摘要。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param lines 按稳定行序读取的完整单件明细集合。
     */
    public record Frozen(CompletionManifest manifest, List<CompletionManifestLine> lines) {
        public Frozen {
            lines = List.copyOf(lines);
        }
    }

    public static String hash(List<CompletionManifestLine> lines) {
        return TraceHashes.sha256(
                lines.stream()
                        .map(
                                line ->
                                        TraceHashes.fingerprint(
                                                line.lineNo(),
                                                line.identityId(),
                                                line.localSnId(),
                                                line.snCode(),
                                                line.batchId(),
                                                line.localLotId(),
                                                line.materialId(),
                                                line.quantity()
                                                        .stripTrailingZeros()
                                                        .toPlainString(),
                                                line.result(),
                                                line.sourceVersion()))
                        .collect(Collectors.joining("\n")));
    }

    public static Frozen read(
            CompletionManifestPort port,
            String tenant,
            Long id,
            int revision,
            String expectedHash) {
        CompletionManifest header =
                Objects.requireNonNull(port.manifest(tenant, id, revision), "清单不存在");
        require(
                tenant.equals(header.tenantId())
                        && id.equals(header.manifestId())
                        && revision == header.revision(),
                "清单身份不一致");
        require(Set.of("FROZEN", "APPLIED", "RECEIVED").contains(header.status()), "清单未冻结");
        require(
                header.lineCount() >= 0 && header.goodQty() != null && header.scrapQty() != null,
                "清单汇总缺失");
        require(expectedHash == null || expectedHash.equals(header.contentHash()), "清单摘要与事件不一致");
        var lines = new ArrayList<CompletionManifestLine>();
        var cursors = new HashSet<String>();
        String cursor = null;
        boolean more;
        do {
            var page =
                    Objects.requireNonNull(port.lines(tenant, id, revision, cursor, 500), "清单分页缺失");
            require(page.rows() != null && page.rows().size() <= 500, "清单分页数量无效");
            lines.addAll(page.rows());
            more = page.hasMore();
            require(lines.size() <= header.lineCount(), "清单行数超过冻结汇总");
            if (more) {
                require(
                        !page.rows().isEmpty()
                                && page.nextCursor() != null
                                && !page.nextCursor().isBlank()
                                && cursors.add(page.nextCursor()),
                        "清单游标未向前推进");
                cursor = page.nextCursor();
            } else {
                require(page.nextCursor() == null || page.nextCursor().isBlank(), "清单结束标记与游标冲突");
            }
        } while (more);
        require(lines.size() == header.lineCount(), "清单未完整读取");
        BigDecimal good = BigDecimal.ZERO, scrap = BigDecimal.ZERO;
        Set<Long> identities = new HashSet<>();
        long index = 0;
        for (var line : lines) {
            require(
                    line.lineNo() == ++index
                            && line.quantity() != null
                            && line.quantity().signum() > 0
                            && line.sourceVersion() > 0,
                    "清单行序、数量或版本无效");
            require(Objects.equals(header.materialId(), line.materialId()), "清单物料与工单不一致");
            require(Set.of("GOOD", "SCRAP").contains(line.result()), "清单结果无效");
            if (line.identityId() != null) {
                require(
                        line.identityId() > 0
                                && identities.add(line.identityId())
                                && line.quantity().compareTo(BigDecimal.ONE) == 0
                                && line.snCode() != null
                                && !line.snCode().isBlank(),
                        "SN清单必须逐件且不能重复身份");
            }
            if ("GOOD".equals(line.result())) {
                good = good.add(line.quantity());
            } else {
                scrap = scrap.add(line.quantity());
            }
        }
        require(
                good.compareTo(header.goodQty()) == 0 && scrap.compareTo(header.scrapQty()) == 0,
                "单件结果与清单汇总不一致");
        require(hash(lines).equals(header.contentHash()), "清单分页内容摘要不一致");
        return new Frozen(header, lines);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

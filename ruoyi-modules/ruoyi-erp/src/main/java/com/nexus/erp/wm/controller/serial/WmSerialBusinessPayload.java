package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SnItem;
import java.util.*;

/**
 * 解析四端旧请求体，保留嵌套entity中反序列化会丢弃的实物字段。
 */
public final class WmSerialBusinessPayload {
    /**
     * 原业务行的实际身份明细与版本。
     *
     * @param ordinal 原来源内稳定的顺序位置。
     * @param oldLine 更正前实际持久化的原单行。
     * @param version 当前事实或规则版本。
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @param supplied 调用方提交的原始选择或断言，仍须来源校验。
     * @param items 业务行已确认的实际明细集合。
     */
    public record Line(
            int ordinal,
            Long oldLine,
            String version,
            Long policyVersion,
            boolean supplied,
            List<SnItem> items) {}

    private final JsonNode body;
    private final List<Line> lines;

    public WmSerialBusinessPayload(JsonNode body, SerialBusinessRoute route) {
        this.body = body;
        List<Line> parsed = new ArrayList<>();
        JsonNode rows =
                body.path(route.linesProperty() == null ? "__whole__" : route.linesProperty());
        if (!rows.isArray()) {
            rows = body.path("lines");
        }
        if (rows.isArray()) {
            int index = 0;
            for (var row : rows) {
                parsed.add(line(index++, row, body, route.whole()));
            }
        } else {
            parsed.add(line(0, body, body, route.whole()));
        }
        // PC执行载荷同时带行集合和顶层汇总，汇总必须精确对应各行，不能暗选行。
        if (rows.isArray() && body.has("snItems")) {
            List<SnItem> flat = items(body.get("snItems"));
            var nested = parsed.stream().flatMap(l -> l.items().stream()).toList();
            if (!flat.isEmpty() && nested.isEmpty()) {
                if (parsed.size() != 1) {
                    throw fail("多行单据的SN必须携带真实lineId归属");
                }
                var l = parsed.get(0);
                parsed.set(
                        0,
                        new Line(
                                l.ordinal(),
                                l.oldLine(),
                                l.version(),
                                l.policyVersion(),
                                true,
                                flat));
            } else if (!flat.isEmpty() && !WmSerialBusinessStore.sameItems(flat, nested)) {
                throw fail("顶层SN汇总与业务行不一致");
            }
        }
        Set<Long> seen = new HashSet<>();
        for (var row : parsed) {
            for (var item : row.items()) {
                if (!seen.add(item.identityId())) {
                    throw fail("同一SN不能分配到多行或多个上架明细");
                }
            }
        }
        lines = List.copyOf(parsed);
    }

    private static Line line(int ordinal, JsonNode row, JsonNode parent, boolean whole) {
        String version =
                version(
                        row.has("detailVersion")
                                ? row.get("detailVersion")
                                : parent.get("detailVersion"));
        boolean supplied = row.has("snItems");
        List<SnItem> selected = items(row.get("snItems"));
        JsonNode details =
                row.has("wmMaterialReceiveLineDetailList")
                        ? row.get("wmMaterialReceiveLineDetailList")
                        : row.get("detailList");
        if (details != null && details.isArray()) {
            List<SnItem> allocated = new ArrayList<>();
            boolean allocatedSupplied = false;
            for (var detail : details) {
                if (detail.has("snItems")) {
                    allocatedSupplied = true;
                    allocated.addAll(items(detail.get("snItems")));
                    String v = version(detail.get("detailVersion"));
                    if (v != null) {
                        if (version != null && !version.equals(v)) {
                            throw fail("同一业务行的版本不一致");
                        }
                        version = v;
                    }
                }
            }
            if (allocatedSupplied) {
                if (!selected.isEmpty() && !WmSerialBusinessStore.sameItems(selected, allocated)) {
                    throw fail("收货行SN与上架分配SN不一致");
                }
                selected = allocated;
                supplied = true;
            }
        }
        return new Line(
                ordinal,
                whole ? Long.valueOf(0) : id(row.get("lineId")),
                version,
                id(row.path("policy").get("version")),
                supplied,
                List.copyOf(selected));
    }

    public List<Line> lines() {
        return lines;
    }

    public Long headId(SerialBusinessRoute route) {
        return id(body.get(route.headProperty()));
    }

    public String command() {
        JsonNode node = body.get("commandId");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.textValue().isBlank() || node.textValue().length() > 128) {
            throw fail("commandId必须为1至128字符");
        }
        return node.textValue();
    }

    public boolean hasSelection() {
        return lines.stream().anyMatch(Line::supplied);
    }

    public static List<SnItem> items(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw fail("snItems必须为数组");
        }
        List<SnItem> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (var row : value) {
            Long identity = id(row.get("identityId"));
            if (identity == null || identity <= 0 || !seen.add(identity)) {
                throw fail("SN identityId缺失或重复");
            }
            result.add(
                    new SnItem(
                            identity,
                            id(row.get("sourceStockId")),
                            id(row.get("targetLocationId")),
                            id(row.get("originalTxId")),
                            id(row.get("batchId"))));
        }
        return result;
    }

    private static String version(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw fail("detailVersion必须原样传递服务端字符串");
        }
        return value.textValue();
    }

    public static Long id(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isEmpty()) {
            return null;
        }
        try {
            if (!node.isTextual() && !node.isIntegralNumber()) {
                throw new NumberFormatException();
            }
            long id = Long.parseLong(node.asText());
            if (id < 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw fail("ID必须为有效整数字符串");
        }
    }

    static ServiceException fail(String message) {
        return new ServiceException(message);
    }
}

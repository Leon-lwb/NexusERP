package com.nexus.common.mq.iqc;

import com.nexus.common.core.exception.ServiceException;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 核验在服务自身已掌控的连接上读取的 MySQL 原生目录信息。
 * 表结构有效不等于质量授权，也不能补足缺失的历史数量单位。
 * 本工具不签发结果凭证、外部已验证标记或原始数量单位契约。
 */
public final class IqcNativeSchema {
    private IqcNativeSchema() { }

    public static void requireErpDefinition(List<Column> columns, List<IndexPart> indexes) {
        Set<String> tables = Set.of("wm_material_receive", "wm_material_receive_line", "bd_material",
            "bd_unit_of_measure", "wm_certified_iqc_evidence");
        Map<String, Column> nativeColumns = requireColumns(columns, tables);
        column(nativeColumns, "wm_material_receive", "receive_id", "bigint", null, null, true);
        column(nativeColumns, "wm_material_receive_line", "line_id", "bigint", null, null, true);
        column(nativeColumns, "wm_material_receive_line", "quantity_received", "decimal", 18, 4, true);
        column(nativeColumns, "bd_material", "material_id", "bigint", null, null, true);
        column(nativeColumns, "bd_unit_of_measure", "uom_id", "bigint", null, null, true);
        for (String name : List.of("evidence_id", "inspection_id", "receive_id", "material_id", "base_uom_id")) {
            column(nativeColumns, "wm_certified_iqc_evidence", name, "bigint", null, null, true);
        }
        column(nativeColumns, "wm_certified_iqc_evidence", "tenant_id", "varchar", null, null, true);
        column(nativeColumns, "wm_certified_iqc_evidence", "first_message_id", "varchar", null, null, true);
        column(nativeColumns, "wm_certified_iqc_evidence", "inspected_quantity", "decimal", 14, 4, true);
        Map<String, List<IndexPart>> nativeIndexes = requireIndexes(indexes, tables);
        primary(nativeIndexes, "wm_material_receive", "receive_id");
        primary(nativeIndexes, "wm_material_receive_line", "line_id");
        primary(nativeIndexes, "bd_material", "material_id");
        primary(nativeIndexes, "bd_unit_of_measure", "uom_id");
        primary(nativeIndexes, "wm_certified_iqc_evidence", "evidence_id");
        unique(nativeIndexes, "wm_certified_iqc_evidence", null, "tenant_id", "inspection_id");
        unique(nativeIndexes, "wm_certified_iqc_evidence", null, "tenant_id", "receive_id", "material_id");
        unique(nativeIndexes, "wm_certified_iqc_evidence", null, "tenant_id", "first_message_id");
    }

    public static void requireMesDefinition(List<Column> columns, List<IndexPart> indexes) {
        Set<String> tables = Set.of("qc_iqc", "qc_inspect_detail");
        Map<String, Column> nativeColumns = requireColumns(columns, tables);
        column(nativeColumns, "qc_iqc", "iqc_id", "bigint", null, null, true);
        column(nativeColumns, "qc_iqc", "tenant_id", "varchar", null, null, true);
        column(nativeColumns, "qc_iqc", "material_id", "bigint", null, null, true);
        // 未关联收货单的手工 IQC 允许原生 receive_id 为空；读取当前记录时，必须先确认
        // 真实 receive_id 非空，才能核验其实际身份和范围。
        column(nativeColumns, "qc_iqc", "receive_id", "bigint", null, null, false);
        column(nativeColumns, "qc_iqc", "quantity", "decimal", 14, 4, true);
        column(nativeColumns, "qc_inspect_detail", "detail_id", "bigint", null, null, true);
        Map<String, List<IndexPart>> nativeIndexes = requireIndexes(indexes, tables);
        primary(nativeIndexes, "qc_iqc", "iqc_id");
        primary(nativeIndexes, "qc_inspect_detail", "detail_id");
        unique(nativeIndexes, "qc_iqc", null, "tenant_id", "receive_id", "material_id");
    }

    /** 不合格保护需要已提交的原生身份，不依赖原生数量、单位或证据。 */
    public static void requireMesIdentityDefinition(List<Column> columns, List<IndexPart> indexes) {
        if (columns == null || indexes == null) throw failed();
        var identityColumns = columns.stream().filter(c -> c != null && "qc_iqc".equals(lower(c.getTableName()))).toList();
        var identityIndexes = indexes.stream().filter(i -> i != null && "qc_iqc".equals(lower(i.getTableName()))).toList();
        var nativeColumns = requireColumns(identityColumns, Set.of("qc_iqc"));
        column(nativeColumns, "qc_iqc", "iqc_id", "bigint", null, null, true);
        column(nativeColumns, "qc_iqc", "tenant_id", "varchar", null, null, true);
        column(nativeColumns, "qc_iqc", "receive_id", "bigint", null, null, false);
        column(nativeColumns, "qc_iqc", "material_id", "bigint", null, null, true);
        primary(requireIndexes(identityIndexes, Set.of("qc_iqc")), "qc_iqc", "iqc_id");
    }

    /** 既有 ERP 不合格保护投影需要具备事务保障的身份，不依赖合格证据。 */
    public static void requireErpIsolationDefinition(List<Column> columns, List<IndexPart> indexes) {
        Set<String> tables = Set.of("wm_material_receive", "wm_material_receive_line", "wm_material_receive_detail", "wm_material_stock_tx",
            "wm_batch", "wm_material_stock", "wm_arrival_notice_line", "wm_warehouse", "wm_storage_area", "wm_storage_location");
        var nativeColumns = requireColumns(columns, tables);
        var nativeIndexes = requireIndexes(indexes, tables);
        for (String[] id : new String[][] {{"wm_material_receive", "receive_id"}, {"wm_material_receive_line", "line_id"},
            {"wm_batch", "batch_id"}, {"wm_material_stock", "stock_id"}, {"wm_arrival_notice_line", "line_id"},
            {"wm_material_receive_detail", "detail_id"}, {"wm_material_stock_tx", "tx_id"}, {"wm_warehouse", "warehouse_id"},
            {"wm_storage_area", "area_id"}, {"wm_storage_location", "location_id"}}) {
            column(nativeColumns, id[0], id[1], "bigint", null, null, true); primary(nativeIndexes, id[0], id[1]);
        }
    }

    /** ERP出生登记必须原生精确且具有永久完整唯一性，不凭记录数推断身份。 */
    public static void requireErpBirthDefinition(List<Column> columns, List<IndexPart> indexes) {
        String table = "wm_receipt_iqc_birth";
        var nativeColumns = requireColumns(columns, Set.of(table));
        for (String name : List.of("birth_id", "receive_id", "material_id", "base_uom_id", "vendor_id", "captured_at")) {
            column(nativeColumns, table, name, "bigint", null, null, true);
        }
        column(nativeColumns, table, "tenant_id", "varchar", null, null, true);
        column(nativeColumns, table, "quantity", "decimal", 14, 4, true);
        column(nativeColumns, table, "quantity_scale", "tinyint", null, null, true);
        column(nativeColumns, table, "receipt_scope_sha256", "char", null, null, true);
        column(nativeColumns, table, "birth_sha256", "char", null, null, true);
        var nativeIndexes = requireIndexes(indexes, Set.of(table));
        primary(nativeIndexes, table, "birth_id");
        unique(nativeIndexes, table, null, "tenant_id", "receive_id", "material_id");
    }

    private static Map<String, Column> requireColumns(List<Column> rows, Set<String> tables) {
        if (rows == null || rows.isEmpty()) throw failed();
        Map<String, Column> result = new HashMap<>();
        for (Column row : rows) {
            if (row == null || !tables.contains(lower(row.getTableName())) || blank(row.getColumnName())
                || !"innodb".equals(lower(row.getEngine())) || blank(row.getDataType())
                || !("YES".equals(row.getIsNullable()) || "NO".equals(row.getIsNullable()))) throw failed();
            String key = lower(row.getTableName()) + "." + lower(row.getColumnName());
            if (result.put(key, row) != null) throw failed();
        }
        return result;
    }
    private static void column(Map<String, Column> rows, String table, String name, String type,
                               Integer precision, Integer scale, boolean nonNull) {
        Column row = rows.get(table + "." + name);
        if (row == null || !type.equals(lower(row.getDataType())) || (nonNull && !"NO".equals(row.getIsNullable()))
            || (precision != null && !precision.equals(row.getNumericPrecision()))
            || (scale != null && !scale.equals(row.getNumericScale()))) throw failed();
    }
    private static Map<String, List<IndexPart>> requireIndexes(List<IndexPart> rows, Set<String> tables) {
        if (rows == null || rows.isEmpty()) throw failed();
        Map<String, List<IndexPart>> result = new HashMap<>();
        for (IndexPart row : rows) {
            if (row == null || !tables.contains(lower(row.getTableName())) || blank(row.getIndexName())
                || blank(row.getColumnName()) || row.getNonUnique() == null
                || !(row.getNonUnique() == 0 || row.getNonUnique() == 1)
                || row.getSeqInIndex() == null || row.getSeqInIndex() <= 0) throw failed();
            String key = lower(row.getTableName()) + "." + lower(row.getIndexName());
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }
    private static void primary(Map<String, List<IndexPart>> rows, String table, String column) {
        unique(rows, table, "primary", column);
    }
    private static void unique(Map<String, List<IndexPart>> indexes, String table, String name, String... columns) {
        for (var entry : indexes.entrySet()) {
            if (!entry.getKey().startsWith(table + ".")
                || (name != null && !entry.getKey().equals(table + "." + name))) continue;
            List<IndexPart> parts = new ArrayList<>(entry.getValue());
            parts.sort(Comparator.comparing(IndexPart::getSeqInIndex));
            if (parts.size() != columns.length) continue;
            boolean exact = true;
            for (int i = 0; i < columns.length; i++) {
                IndexPart part = parts.get(i);
                if (part.getNonUnique() != 0 || part.getSubPart() != null || part.getSeqInIndex() != i + 1
                    || !"btree".equals(lower(part.getIndexType())) || !columns[i].equals(lower(part.getColumnName()))) {
                    exact = false; break;
                }
            }
            if (exact) return;
        }
        throw failed();
    }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ServiceException failed() { return new ServiceException("IQC_NATIVE_SCHEMA_UNSUPPORTED"); }

    @Data public static class Column {
        private String tableName;
        private String engine;
        private String columnName;
        private String dataType;
        private Integer numericPrecision;
        private Integer numericScale;
        private String isNullable;
    }
    @Data public static class IndexPart {
        private String tableName;
        private String indexName;
        private Integer nonUnique;
        private Integer seqInIndex;
        private String columnName;
        private Integer subPart;
        private String indexType;
    }
}

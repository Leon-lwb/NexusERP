package com.nexus.ai.service.query;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ERP 表结构缓存管理器
 * <p>
 * 应用启动时自动加载 ERP 业务表结构到内存缓存，避免 Agent 每次查询都执行
 * INFORMATION_SCHEMA / SHOW CREATE TABLE，降低 Token 消耗和响应延迟。
 * </p>
 * <p>
 * 使用 DataSource 直接获取 Connection，通过 JDBC DatabaseMetaData API 获取完整表结构信息
 * （表注释、列信息、主键等），比 JdbcTemplate 更适合元数据操作。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@DS("erp")
public class ErpTableSchemaManager {

    @Autowired
    private DataSource erpDataSource;

    /** 允许的表名前缀 */
    @Value("${nexus.ai.allowed-table-prefixes:bd_,so_,pu_,prd_,wm_,sch_}")
    private List<String> allowedTablePrefixes;

    /** 表结构缓存 (表名 -> 表结构) */
    private final Map<String, TableStructure> schemaCache = new ConcurrentHashMap<>();

    /** 缓存初始化标志 */
    private volatile boolean initialized = false;

    /** 缓存初始化时间戳（用于 TTL 过期检查） */
    private volatile long lastRefreshTime = 0;

    /** 默认缓存 TTL（毫秒），24 小时 */
    private static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L;

    // ==================== 初始化 ====================

    /**
     * 初始化表结构缓存（由 ErpTableSchemaInitializer 在 ApplicationReadyEvent 时调用）
     */
    public void initializeSchema() {
        if (initialized) {
            log.debug("[SchemaCache] 已初始化，跳过重复加载");
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try {
                long startTime = System.currentTimeMillis();
                log.info("[SchemaCache] 开始加载 ERP 表结构缓存...");
                loadAllTableSchemas();
                initialized = true;
                lastRefreshTime = System.currentTimeMillis();
                long elapsed = lastRefreshTime - startTime;
                log.info("[SchemaCache] 缓存加载完成，共 {} 张表，耗时 {}ms", schemaCache.size(), elapsed);
            } catch (Exception e) {
                log.error("[SchemaCache] 缓存加载失败", e);
            }
        }
    }

    /**
     * 强制刷新所有表结构缓存
     */
    public void refreshAll() {
        synchronized (this) {
            long startTime = System.currentTimeMillis();
            log.info("[SchemaCache] 开始刷新所有表结构缓存...");
            schemaCache.clear();
            loadAllTableSchemas();
            lastRefreshTime = System.currentTimeMillis();
            long elapsed = lastRefreshTime - startTime;
            log.info("[SchemaCache] 缓存刷新完成，共 {} 张表，耗时 {}ms", schemaCache.size(), elapsed);
        }
    }

    /**
     * 刷新单个表的结构缓存
     */
    public void refreshTableSchema(String tableName) {
        try {
            TableStructure schema = loadTableSchema(tableName);
            if (schema != null) {
                schemaCache.put(tableName, schema);
                log.info("[SchemaCache] 已刷新表结构: {}", tableName);
            }
        } catch (Exception e) {
            log.error("[SchemaCache] 刷新表结构失败: {}", tableName, e);
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 获取所有缓存的表结构
     */
    public List<TableStructure> getAllTableSchemas() {
        ensureInitialized();
        return new ArrayList<>(schemaCache.values());
    }

    /**
     * 获取指定表的缓存结构
     *
     * @param tableName 表名
     * @return 表结构，如果不存在返回 null
     */
    public TableStructure getTableSchema(String tableName) {
        ensureInitialized();
        return schemaCache.get(tableName);
    }

    /**
     * 获取核心表的 Schema 摘要（用于嵌入 Agent SystemMessage）
     * <p>只包含 bd_*、so_*、pu_* 前缀的核心表，控制在 2000 Token 以内</p>
     *
     * @return 核心表 Schema 摘要文本
     */
    public String getCoreSchemaSummary() {
        ensureInitialized();
        return schemaCache.values().stream()
            .filter(t -> t.getTableName().startsWith("bd_")
                || t.getTableName().startsWith("so_")
                || t.getTableName().startsWith("pu_"))
            .sorted((a, b) -> a.getTableName().compareTo(b.getTableName()))
            .map(this::formatTableSummary)
            .collect(Collectors.joining("\n"));
    }

    /**
     * 获取所有表的简要列表（表名: 注释）
     */
    public String getAllTablesSummary() {
        ensureInitialized();
        return schemaCache.values().stream()
            .sorted((a, b) -> a.getTableName().compareTo(b.getTableName()))
            .map(t -> "- " + t.getTableName() + ": " + t.getTableComment())
            .collect(Collectors.joining("\n"));
    }

    /**
     * 检查缓存是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取缓存的表数量
     */
    public int getTableCount() {
        return schemaCache.size();
    }

    // ==================== 内部方法 ====================

    /**
     * 确保缓存已初始化（兜底：如果启动时加载失败，首次查询时重试）
     */
    private void ensureInitialized() {
        if (!initialized) {
            initializeSchema();
        }
        // 检查 TTL 过期
        if (System.currentTimeMillis() - lastRefreshTime > DEFAULT_TTL_MS) {
            log.info("[SchemaCache] 缓存已过期，自动刷新");
            refreshAll();
        }
    }

    /**
     * 加载所有允许的表结构
     */
    private void loadAllTableSchemas() {
        try (Connection conn = erpDataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            // 获取所有表
            List<String> tableNames = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (isAllowedTable(tableName)) {
                        tableNames.add(tableName);
                    }
                }
            }

            log.info("[SchemaCache] 发现 {} 张允许的 ERP 业务表", tableNames.size());

            // 逐表加载结构
            for (String tableName : tableNames) {
                try {
                    TableStructure schema = loadTableSchemaFromMetaData(metaData, catalog, tableName);
                    if (schema != null) {
                        // 额外获取 DDL（SHOW CREATE TABLE）
                        schema.setDdl(loadDdl(tableName));
                        schemaCache.put(tableName, schema);
                    }
                } catch (Exception e) {
                    log.warn("[SchemaCache] 加载表结构失败: {}", tableName, e);
                }
            }
        } catch (SQLException e) {
            log.error("[SchemaCache] 获取数据库连接失败", e);
        }
    }

    /**
     * 从 DatabaseMetaData 加载单个表的结构信息
     * <p>包含列信息、复合主键、外键关系、枚举值提取</p>
     */
    private TableStructure loadTableSchemaFromMetaData(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        TableStructure table = new TableStructure();
        table.setTableName(tableName);

        // 获取表注释
        try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                table.setTableComment(rs.getString("REMARKS"));
            }
        }

        // 获取列信息
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(catalog, null, tableName, null)) {
            while (rs.next()) {
                ColumnInfo col = new ColumnInfo();
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setColumnType(rs.getString("TYPE_NAME"));
                int columnSize = rs.getInt("COLUMN_SIZE");
                if (columnSize > 0 && !isNumericType(col.getColumnType())) {
                    col.setColumnType(col.getColumnType() + "(" + columnSize + ")");
                }
                String remark = rs.getString("REMARKS");
                col.setColumnComment(remark);
                col.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                col.setDefaultValue(rs.getString("COLUMN_DEF"));
                // 从注释中提取枚举值
                col.setEnumValues(extractEnumValues(remark));
                columns.add(col);
            }
        }

        // 获取主键信息（支持复合主键）
        List<String> pkColumns = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, tableName)) {
            while (rs.next()) {
                String pkName = rs.getString("COLUMN_NAME");
                pkColumns.add(pkName);
                for (ColumnInfo col : columns) {
                    if (col.getColumnName().equals(pkName)) {
                        col.setPrimaryKey(true);
                    }
                }
            }
        }
        table.setPrimaryKeyColumns(pkColumns);
        // 兼容旧逻辑：primaryKey 字段存储第一个主键列名
        if (!pkColumns.isEmpty()) {
            table.setPrimaryKey(pkColumns.get(0));
        }

        // 获取外键关系
        List<ForeignKeyRelation> foreignKeys = discoverForeignKeys(metaData, catalog, tableName);
        table.setForeignKeys(foreignKeys);

        table.setColumns(columns);
        return table;
    }

    /**
     * 通过 SHOW CREATE TABLE 加载 DDL
     */
    private String loadDdl(String tableName) {
        try (Connection conn = erpDataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
            if (rs.next()) {
                return rs.getString("Create Table");
            }
        } catch (SQLException e) {
            log.warn("[SchemaCache] 获取 DDL 失败: {}", tableName, e.getMessage());
        }
        return null;
    }

    /**
     * 从缓存加载单个表结构（用于 refreshTableSchema）
     */
    private TableStructure loadTableSchema(String tableName) throws SQLException {
        try (Connection conn = erpDataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            TableStructure schema = loadTableSchemaFromMetaData(metaData, conn.getCatalog(), tableName);
            if (schema != null) {
                schema.setDdl(loadDdl(tableName));
            }
            return schema;
        }
    }

    /**
     * 判断表名是否在允许的前缀列表中
     */
    private boolean isAllowedTable(String tableName) {
        for (String prefix : allowedTablePrefixes) {
            if (tableName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为数值类型（不需要显示长度）
     */
    private boolean isNumericType(String typeName) {
        if (typeName == null) return true;
        String upper = typeName.toUpperCase();
        return upper.contains("INT") || upper.contains("DECIMAL") || upper.contains("NUMERIC")
            || upper.contains("FLOAT") || upper.contains("DOUBLE") || upper.contains("BIT");
    }

    /**
     * 格式化单个表的摘要（用于 SystemMessage 嵌入）
     */
    private String formatTableSummary(TableStructure table) {
        StringBuilder sb = new StringBuilder();
        sb.append(table.getTableName()).append(": ").append(table.getTableComment()).append("(");

        List<String> colParts = new ArrayList<>();
        for (ColumnInfo col : table.getColumns()) {
            StringBuilder colSb = new StringBuilder();
            colSb.append(col.getColumnName());
            if (col.isPrimaryKey()) {
                colSb.append(" PK");
            }
            if (col.getColumnComment() != null && !col.getColumnComment().isEmpty()) {
                colSb.append(" ").append(col.getColumnComment());
            }
            colParts.add(colSb.toString());
        }
        sb.append(String.join(", ", colParts));
        sb.append(")");
        return sb.toString();
    }

    /**
     * 获取全量 Schema 摘要（覆盖所有允许前缀的表）
     * <p>替代 getCoreSchemaSummary()，包含 bd_/so_/pu_/prd_/wm_/sch_ 全部前缀的表</p>
     * <p>输出增强格式：含字段类型、枚举值、外键关系</p>
     *
     * @return 全量表结构摘要文本
     */
    public String getFullSchemaSummary() {
        ensureInitialized();
        return schemaCache.values().stream()
            .filter(t -> allowedTablePrefixes.stream().anyMatch(t.getTableName()::startsWith))
            .sorted(Comparator.comparing(TableStructure::getTableName))
            .map(this::formatEnhancedTableSummary)
            .collect(Collectors.joining("\n\n"));
    }

    // ==================== 表优先级与优化摘要 ====================

    /**
     * 表优先级枚举
     * <p>
     * CORE — 核心表，几乎所有查询都需要，始终完整输出<br>
     * IMPORTANT — 重要表，常用查询涉及，空间允许时完整输出<br>
     * AUXILIARY — 辅助表，低频查询涉及，仅输出表名和注释
     * </p>
     */
    public enum TablePriority {
        /** 核心表：高频查询必用，始终包含 */
        CORE,
        /** 重要表：常用查询涉及，空间允许时包含 */
        IMPORTANT,
        /** 辅助表：低频查询涉及，仅列出表名 */
        AUXILIARY
    }

    /** 表优先级映射（静态初始化，覆盖所有 ERP 业务表） */
    private static final Map<String, TablePriority> TABLE_PRIORITY_MAP;

    static {
        Map<String, TablePriority> map = new LinkedHashMap<>();
        // === CORE：核心主数据 + 高频查询表 ===
        map.put("bd_material", TablePriority.CORE);
        map.put("bd_material_category", TablePriority.CORE);
        map.put("bd_client", TablePriority.CORE);
        map.put("bd_vendor", TablePriority.CORE);
        map.put("wm_material_stock", TablePriority.CORE);
        map.put("wm_warehouse", TablePriority.CORE);
        map.put("so_sales_order_header", TablePriority.CORE);
        map.put("so_sales_order_line", TablePriority.CORE);

        // === IMPORTANT：常用业务表 ===
        map.put("bd_workshop", TablePriority.IMPORTANT);
        map.put("bd_workstation", TablePriority.IMPORTANT);
        map.put("bd_bom", TablePriority.IMPORTANT);
        map.put("bd_bom_detail", TablePriority.IMPORTANT);
        map.put("bd_unit_of_measure", TablePriority.IMPORTANT);
        map.put("wm_material_stock_tx", TablePriority.IMPORTANT);
        map.put("wm_storage_area", TablePriority.IMPORTANT);
        map.put("wm_storage_location", TablePriority.IMPORTANT);
        map.put("wm_batch", TablePriority.IMPORTANT);
        map.put("wm_stock_alert", TablePriority.IMPORTANT);
        map.put("pu_purchase_order", TablePriority.IMPORTANT);
        map.put("pu_purchase_order_line", TablePriority.IMPORTANT);
        map.put("pu_vendor_evaluation", TablePriority.IMPORTANT);
        map.put("prd_work_order", TablePriority.IMPORTANT);
        map.put("prd_work_report", TablePriority.IMPORTANT);
        map.put("so_customer_order", TablePriority.IMPORTANT);
        map.put("so_return", TablePriority.IMPORTANT);
        map.put("so_return_line", TablePriority.IMPORTANT);
        map.put("so_quotation", TablePriority.IMPORTANT);
        map.put("so_quotation_line", TablePriority.IMPORTANT);
        map.put("so_price_list", TablePriority.IMPORTANT);
        map.put("so_price_list_line", TablePriority.IMPORTANT);

        // === AUXILIARY：低频/辅助表（未显式列出的表默认为 AUXILIARY） ===
        TABLE_PRIORITY_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 获取表的优先级
     *
     * @param tableName 表名
     * @return 优先级（未在映射中的表默认返回 AUXILIARY）
     */
    public TablePriority getTablePriority(String tableName) {
        return TABLE_PRIORITY_MAP.getOrDefault(tableName, TablePriority.AUXILIARY);
    }

    /**
     * 获取按优先级裁剪的 Schema 摘要
     * <p>
     * 优先级策略：
     * <ol>
     *   <li>CORE 级别表：始终完整输出（含字段类型、枚举值、外键关系）</li>
     *   <li>IMPORTANT 级别表：空间允许时完整输出，超出预算后仅输出表名+注释</li>
     *   <li>AUXILIARY 级别表：仅输出表名+注释+字段数</li>
     * </ol>
     * </p>
     *
     * @param maxChars 最大字符数预算（默认 4000，约 2000 中文字）
     * @return 裁剪后的 Schema 摘要文本
     */
    public String getOptimizedSchemaSummary(int maxChars) {
        ensureInitialized();

        List<TableStructure> allTables = schemaCache.values().stream()
            .filter(t -> allowedTablePrefixes.stream().anyMatch(t.getTableName()::startsWith))
            .sorted(Comparator.comparing(TableStructure::getTableName))
            .collect(Collectors.toList());

        // 按优先级分组
        List<TableStructure> coreTables = new ArrayList<>();
        List<TableStructure> importantTables = new ArrayList<>();
        List<TableStructure> auxiliaryTables = new ArrayList<>();

        for (TableStructure table : allTables) {
            switch (getTablePriority(table.getTableName())) {
                case CORE -> coreTables.add(table);
                case IMPORTANT -> importantTables.add(table);
                case AUXILIARY -> auxiliaryTables.add(table);
            }
        }

        StringBuilder sb = new StringBuilder();
        int remaining = maxChars;

        // 1. 输出 CORE 表完整摘要
        for (TableStructure table : coreTables) {
            String summary = formatEnhancedTableSummary(table);
            if (sb.length() + summary.length() + 2 <= maxChars) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(summary);
                remaining = maxChars - sb.length();
            } else {
                // 空间不足，降级为简要格式
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(formatBriefTableSummary(table));
                remaining = maxChars - sb.length();
            }
        }

        // 2. 输出 IMPORTANT 表（空间允许时完整，否则简要）
        for (TableStructure table : importantTables) {
            String summary = formatEnhancedTableSummary(table);
            String brief = formatBriefTableSummary(table);
            String toAppend = (remaining >= summary.length() + 2) ? summary : brief;

            if (sb.length() + toAppend.length() + 2 <= maxChars) {
                sb.append("\n\n").append(toAppend);
                remaining = maxChars - sb.length();
            } else if (sb.length() + brief.length() + 2 <= maxChars) {
                sb.append("\n\n").append(brief);
                remaining = maxChars - sb.length();
            }
            // 空间完全不够则跳过
        }

        // 3. 输出 AUXILIARY 表（仅表名+注释+字段数，一行一个）
        if (!auxiliaryTables.isEmpty() && remaining > 50) {
            sb.append("\n\n--- 其他辅助表 ---\n");
            for (TableStructure table : auxiliaryTables) {
                String line = formatBriefTableSummary(table);
                if (sb.length() + line.length() + 1 <= maxChars) {
                    sb.append(line).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 格式化表的简要摘要（仅表名+注释+字段数，一行）
     *
     * @param table 表结构
     * @return 简要摘要文本
     */
    private String formatBriefTableSummary(TableStructure table) {
        String comment = (table.getTableComment() != null && !table.getTableComment().isEmpty())
            ? "（" + table.getTableComment() + "）" : "";
        int fieldCount = table.getColumns() != null ? table.getColumns().size() : 0;
        return table.getTableName() + comment + " [" + fieldCount + "个字段]";
    }

    /**
     * 增强版表摘要格式（含字段类型、枚举值、外键关系）
     *
     * @param table 表结构
     * @return 增强版摘要文本
     */
    public String formatEnhancedTableSummary(TableStructure table) {
        StringBuilder sb = new StringBuilder();
        sb.append(table.getTableName());
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            sb.append("（").append(table.getTableComment()).append("）");
        }
        sb.append(":\n");

        // 字段信息（含类型、枚举值）
        for (ColumnInfo col : table.getColumns()) {
            sb.append("  ").append(col.getColumnName());
            if (col.getColumnType() != null) {
                sb.append(" ").append(col.getColumnType());
            }
            if (col.isPrimaryKey()) {
                sb.append(" PK");
            }
            if (col.getEnumValues() != null && !col.getEnumValues().isEmpty()) {
                sb.append(" [").append(col.getEnumValues()).append("]");
            }
            if (col.getColumnComment() != null && !col.getColumnComment().isEmpty()) {
                sb.append(" -- ").append(col.getColumnComment());
            }
            sb.append("\n");
        }

        // 外键关系
        if (table.getForeignKeys() != null && !table.getForeignKeys().isEmpty()) {
            sb.append("  关联: ");
            for (ForeignKeyRelation fk : table.getForeignKeys()) {
                sb.append(fk.getColumnName()).append("->").append(fk.getReferencedTable())
                    .append(".").append(fk.getReferencedColumn()).append("  ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 发现表的外键关系
     *
     * @param metaData 数据库元数据
     * @param catalog  数据库目录
     * @param tableName 表名
     * @return 外键关系列表
     */
    private List<ForeignKeyRelation> discoverForeignKeys(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        List<ForeignKeyRelation> relations = new ArrayList<>();
        try (ResultSet rs = metaData.getImportedKeys(catalog, null, tableName)) {
            while (rs.next()) {
                ForeignKeyRelation fk = new ForeignKeyRelation();
                fk.setColumnName(rs.getString("FKCOLUMN_NAME"));
                fk.setReferencedTable(rs.getString("PKTABLE_NAME"));
                fk.setReferencedColumn(rs.getString("PKCOLUMN_NAME"));
                relations.add(fk);
            }
        }
        return relations;
    }

    /**
     * 从字段注释中提取枚举值
     * <p>支持格式：注释[ENUM1/ENUM2/ENUM3] 或 注释[1-选项1/2-选项2/3-选项3]</p>
     *
     * @param comment 字段注释
     * @return 枚举值字符串，无匹配时返回 null
     */
    private String extractEnumValues(String comment) {
        if (comment == null || comment.isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\[([^\\]]+)\\]").matcher(comment);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // ==================== 内部类 ====================

    /**
     * 表结构信息
     */
    @lombok.Data
    public static class TableStructure {
        /** 表名 */
        private String tableName;
        /** 表注释 */
        private String tableComment;
        /** 主键列名（兼容旧逻辑，复合主键用逗号分隔） */
        private String primaryKey;
        /** 主键列名列表（支持复合主键） */
        private List<String> primaryKeyColumns = new ArrayList<>();
        /** 列信息 */
        private List<ColumnInfo> columns = new ArrayList<>();
        /** 外键关系 */
        private List<ForeignKeyRelation> foreignKeys = new ArrayList<>();
        /** 建表 DDL */
        private String ddl;
    }

    /**
     * 列信息
     */
    @lombok.Data
    public static class ColumnInfo {
        /** 列名 */
        private String columnName;
        /** 列类型 */
        private String columnType;
        /** 列注释 */
        private String columnComment;
        /** 是否主键 */
        private boolean isPrimaryKey;
        /** 是否可空 */
        private boolean nullable;
        /** 默认值 */
        private String defaultValue;
        /** 枚举值（从注释中提取，如 "DRAFT/CONFIRMED/DONE"） */
        private String enumValues;
    }

    /**
     * 外键关系
     */
    @lombok.Data
    public static class ForeignKeyRelation {
        /** 本表列名 */
        private String columnName;
        /** 引用表名 */
        private String referencedTable;
        /** 引用列名 */
        private String referencedColumn;
    }
}

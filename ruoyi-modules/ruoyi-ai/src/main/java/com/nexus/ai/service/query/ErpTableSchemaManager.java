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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
                col.setColumnComment(rs.getString("REMARKS"));
                col.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                columns.add(col);
            }
        }

        // 获取主键信息
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, tableName)) {
            if (rs.next()) {
                String pkName = rs.getString("COLUMN_NAME");
                table.setPrimaryKey(pkName);
                for (ColumnInfo col : columns) {
                    if (col.getColumnName().equals(pkName)) {
                        col.setPrimaryKey(true);
                        break;
                    }
                }
            }
        }

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

    // ==================== 内部类 ====================

    /**
     * 表结构信息
     */
    @lombok.Data
    public static class TableStructure {
        private String tableName;
        private String tableComment;
        private String primaryKey;
        private List<ColumnInfo> columns = new ArrayList<>();
        private String ddl;
    }

    /**
     * 列信息
     */
    @lombok.Data
    public static class ColumnInfo {
        private String columnName;
        private String columnType;
        private String columnComment;
        private boolean isPrimaryKey;
        private boolean nullable;
    }
}

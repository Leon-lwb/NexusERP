package com.nexus.ai.service.query;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ERP 数据源 SQL 执行服务
 * <p>
 * 关键：标注 @DS("erp") 切换到 ERP 数据源（ry-erp 库）。
 * ruoyi-ai 原版使用 SpringUtils.getBean(DataSource.class) 会拿到 master 数据源（ry-vue），
 * 查不到 ERP 业务表。此 Service 通过 @DS 注解正确切换到 ERP 数据源。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpQueryService {

    private final JdbcTemplate jdbcTemplate;

    /** 表名合法字符正则（仅允许字母、数字、下划线） */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** 表名存在性缓存（避免每次查库） */
    private final ConcurrentHashMap<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();

    /**
     * 执行 SELECT 查询
     * <p>使用局部 JdbcTemplate 设置查询参数，避免修改单例 Bean 状态导致线程安全问题</p>
     *
     * @param sql            SQL 语句
     * @param maxRows        最大返回行数
     * @param timeoutSeconds 超时秒数
     * @return 查询结果列表
     */
    public List<Map<String, Object>> executeQuery(String sql, int maxRows, int timeoutSeconds) {
        log.debug("[ErpQuery] 执行SQL: {}, maxRows={}, timeout={}s", sql, maxRows, timeoutSeconds);
        // 使用 query 回调方式设置参数，避免修改 JdbcTemplate 单例状态
        List<Map<String, Object>> results = jdbcTemplate.query(
            con -> {
                var ps = con.prepareStatement(sql);
                ps.setQueryTimeout(timeoutSeconds);
                ps.setMaxRows(maxRows);
                return ps;
            },
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                return row;
            }
        );
        log.debug("[ErpQuery] 查询完成，返回 {} 行", results.size());
        return results;
    }

    /**
     * 查询所有 ERP 业务表
     * <p>排除系统表（以 sys_/gen_/qrtz_/flyway_ 开头），返回所有业务表</p>
     *
     * @return ERP 业务表名列表
     */
    public List<String> queryErpTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()",
            String.class
        ).stream()
         .filter(name -> !name.matches("^(sys_|gen_|qrtz_|flyway_|ACT_|FLW_|camunda_).*"))
         .toList();
    }

    /**
     * 查询表结构（SHOW CREATE TABLE，间接含 COMMENT）
     *
     * @param tableName 表名
     * @return CREATE TABLE DDL 语句
     */
    public String queryTableSchema(String tableName) {
        log.debug("[ErpQuery] 查询表结构: {}", tableName);
        // 安全校验：表名只允许字母、数字、下划线，防止 SQL 注入
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            log.warn("[ErpQuery] 非法表名被拒绝: {}", tableName);
            throw new IllegalArgumentException("非法表名: " + tableName);
        }
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SHOW CREATE TABLE `" + tableName + "`"
            );
            if (!results.isEmpty()) {
                Object createTable = results.get(0).get("Create Table");
                String schema = createTable != null ? createTable.toString() : null;
                log.debug("[ErpQuery] 表结构查询完成: {}, DDL长度={}", tableName, schema != null ? schema.length() : 0);
                return schema;
            }
            return null;
        } catch (Exception e) {
            log.error("[ErpQuery] 查询表结构失败: {}", tableName, e);
            throw e;
        }
    }

    /**
     * 检查表是否存在于当前 ERP 数据库中（带缓存）
     * <p>用于 ExecuteSqlQueryTool 的动态白名单校验，避免硬编码表名前缀导致遗漏</p>
     *
     * @param tableName 表名
     * @return true 如果表存在
     */
    public boolean tableExists(String tableName) {
        return tableExistsCache.computeIfAbsent(tableName, name -> {
            if (!TABLE_NAME_PATTERN.matcher(name).matches()) {
                return false;
            }
            try {
                List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    name
                );
                boolean exists = !results.isEmpty();
                log.debug("[ErpQuery] 表存在性检查: {}={}", name, exists);
                return exists;
            } catch (Exception e) {
                log.warn("[ErpQuery] 表存在性检查失败: {}", name, e);
                return false;
            }
        });
    }
}

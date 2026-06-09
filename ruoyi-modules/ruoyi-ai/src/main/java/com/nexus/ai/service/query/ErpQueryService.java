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

    /**
     * 执行 SELECT 查询
     *
     * @param sql            SQL 语句
     * @param maxRows        最大返回行数
     * @param timeoutSeconds 超时秒数
     * @return 查询结果列表
     */
    public List<Map<String, Object>> executeQuery(String sql, int maxRows, int timeoutSeconds) {
        log.debug("[ErpQuery] 执行SQL: {}, maxRows={}, timeout={}s", sql, maxRows, timeoutSeconds);
        jdbcTemplate.setQueryTimeout(timeoutSeconds);
        jdbcTemplate.setMaxRows(maxRows);
        List<Map<String, Object>> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            return row;
        });
        log.debug("[ErpQuery] 查询完成，返回 {} 行", results.size());
        return results;
    }

    /**
     * 查询所有 ERP 业务表（只返回 bd_ / so_ / pu_ / prd_ / wm_ / sch_ 前缀的表）
     *
     * @return ERP 业务表名列表
     */
    public List<String> queryErpTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()",
            String.class
        ).stream()
         .filter(name -> name.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*"))
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
}

package com.nexus.ai.service.query;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** ERP JDBC 边界；每次执行及元数据读取均要求重新校验服务端授权。 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpQueryService {
    private final JdbcTemplate jdbcTemplate;
    private final ErpQueryAuthorizationGuard authorization;
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    // 仅缓存物理表存在性；身份、业务权限及数据范围不缓存。
    private final ConcurrentHashMap<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();

    public List<Map<String, Object>> executeQuery(String sql, int maxRows, int timeoutSeconds) {
        return executeParameterizedQuery(sql, new Object[0], maxRows, timeoutSeconds);
    }

    public List<Map<String, Object>> executeParameterizedQuery(String sql, Object[] params, int maxRows, int timeoutSeconds) {
        var approved = authorization.authorize(sql);
        var arguments = params == null ? new Object[0] : params.clone();
        int rowLimit = authorization.maxRows(maxRows);
        int timeout = authorization.timeoutSeconds(timeoutSeconds);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(connection -> {
                var statement = connection.prepareStatement(approved.sql());
                statement.setQueryTimeout(timeout);
                statement.setMaxRows(rowLimit);
                for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
                return statement;
            }, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                return row;
            });
            // SqlSafetyValidator 需要 EXPLAIN 预校验；受限调用方不能获取物理执行计划。
            return approved.hideExplain() ? List.of() : rows;
        } catch (DataAccessException failure) {
            throw new IllegalArgumentException("业务查询执行失败，请检查受控字段、参数及语法");
        }
    }

    public List<String> queryErpTables() {
        var actor = authorization.currentActor();
        if (!actor.superAdmin()) return authorization.visibleTables(actor).stream().filter(this::physicalTableExists).toList();
        return physicalTables().stream().filter(name -> authorization.canReadTable(actor, name)).sorted().toList();
    }

    public String queryTableSchema(String tableName) {
        var actor = authorization.currentActor();
        if (!validTable(tableName) || !authorization.canReadTable(actor, tableName))
            throw new IllegalArgumentException("当前身份无权查询此业务表结构");
        if (!physicalTableExists(tableName)) return null;
        if (!actor.superAdmin()) return authorization.tableSchema(actor, tableName);
        var rows = jdbcTemplate.queryForList("SHOW CREATE TABLE `" + tableName + "`");
        return rows.isEmpty() ? null : Objects.toString(rows.get(0).get("Create Table"), null);
    }

    public boolean tableExists(String tableName) {
        var actor = authorization.currentActor();
        return validTable(tableName) && authorization.canReadTable(actor, tableName) && physicalTableExists(tableName);
    }

    private boolean physicalTableExists(String name) {
        return tableExistsCache.computeIfAbsent(name, table -> physicalTables().stream().anyMatch(table::equalsIgnoreCase));
    }

    private List<String> physicalTables() {
        return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            List<String> tables = new ArrayList<>();
            try (var result = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null,
                new String[]{"TABLE", "BASE TABLE"})) {
                while (result.next()) tables.add(result.getString("TABLE_NAME"));
            }
            return tables;
        });
    }

    private static boolean validTable(String name) { return name != null && TABLE_NAME_PATTERN.matcher(name).matches(); }
}

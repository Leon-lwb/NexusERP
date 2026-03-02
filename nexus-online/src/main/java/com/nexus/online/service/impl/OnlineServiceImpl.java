package com.nexus.online.service.impl;

import com.nexus.common.core.page.TableDataInfo;
import com.nexus.generator.service.IGenTableService;
import com.nexus.online.service.IOnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class OnlineServiceImpl implements IOnlineService {

    @Autowired(required = false)
    private IGenTableService genTableService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean isValidIdentifier(String name) {
        return StringUtils.hasText(name) && name.matches("^[A-Za-z0-9_]+$");
    }

    private boolean isWhitelistedTable(String tableName) {
        if (!isValidIdentifier(tableName)) return false;
        if (genTableService == null) return false;
        List<?> tables = genTableService.selectDbTableListByNames(new String[]{tableName});
        return tables != null && !tables.isEmpty();
    }

    @Override
    public TableDataInfo listTableData(String tableName, int pageNum, int pageSize) {
        TableDataInfo info = new TableDataInfo();
        if (!isWhitelistedTable(tableName)) {
            info.setRows(Collections.emptyList());
            info.setTotal(0L);
            return info;
        }
        int offset = (Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1);
        String countSql = "SELECT COUNT(1) FROM " + tableName;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        String dataSql = "SELECT * FROM " + tableName + " LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, pageSize, offset);
        info.setRows(rows);
        info.setTotal(total == null ? 0L : total);
        return info;
    }

    @Override
    public Map<String, Object> getDetail(String tableName, String id) {
        if (!isWhitelistedTable(tableName)) {
            return Collections.emptyMap();
        }
        // 尝试使用通用主键名 id
        String sql = "SELECT * FROM " + tableName + " WHERE id = ? LIMIT 1";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.get(0);
    }

    @Override
    public int upsertByName(String tableName, Map<String, Object> data) {
        if (!isWhitelistedTable(tableName)) {
            return 0;
        }
        if (data == null || data.isEmpty()) {
            return 0;
        }
        String pk = detectPk(tableName);
        if (!StringUtils.hasText(pk)) {
            pk = "id";
        }
        Set<String> columns = fetchColumns(tableName);
        if (columns.isEmpty()) {
            return 0;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String key = e.getKey();
            if (isValidIdentifier(key) && columns.contains(key)) {
                filtered.put(key, e.getValue());
            }
        }
        if (filtered.isEmpty()) {
            return 0;
        }
        Object pkVal = filtered.get(pk);
        boolean exists = false;
        if (pkVal != null) {
            String existsSql = "SELECT COUNT(1) FROM " + tableName + " WHERE " + pk + " = ?";
            Long cnt = jdbcTemplate.queryForObject(existsSql, Long.class, pkVal);
            exists = cnt != null && cnt > 0;
        }
        if (exists) {
            // update
            List<String> setCols = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> e : filtered.entrySet()) {
                if (e.getKey().equals(pk)) continue;
                setCols.add(e.getKey() + " = ?");
                params.add(e.getValue());
            }
            params.add(pkVal);
            if (setCols.isEmpty()) {
                return 0;
            }
            String sql = "UPDATE " + tableName + " SET " + String.join(", ", setCols) + " WHERE " + pk + " = ?";
            return jdbcTemplate.update(sql, params.toArray());
        } else {
            // insert
            List<String> insertCols = new ArrayList<>(filtered.keySet());
            List<Object> params = new ArrayList<>(filtered.values());
            String placeholders = String.join(", ", Collections.nCopies(insertCols.size(), "?"));
            String sql = "INSERT INTO " + tableName + " (" + String.join(", ", insertCols) + ") VALUES (" + placeholders + ")";
            return jdbcTemplate.update(sql, params.toArray());
        }
    }

    @Override
    public int deleteByIds(String tableName, List<String> ids) {
        if (!isWhitelistedTable(tableName) || ids == null || ids.isEmpty()) {
            return 0;
        }
        String pk = detectPk(tableName);
        if (!StringUtils.hasText(pk)) {
            pk = "id";
        }
        String inPlaceholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM " + tableName + " WHERE " + pk + " IN (" + inPlaceholders + ")";
        return jdbcTemplate.update(sql, ids.toArray());
    }

    private Set<String> fetchColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        List<String> cols = jdbcTemplate.queryForList(sql, String.class, tableName);
        return new HashSet<>(cols);
    }

    private String detectPk(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_KEY = 'PRI' LIMIT 1";
        List<String> list = jdbcTemplate.queryForList(sql, String.class, tableName);
        return list.isEmpty() ? null : list.get(0);
    }
}

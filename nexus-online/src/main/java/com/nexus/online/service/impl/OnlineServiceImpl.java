package com.nexus.online.service.impl;

import com.nexus.common.core.domain.entity.SysDictData;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.utils.StringUtils;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.domain.GenTableColumn;
import com.nexus.generator.mapper.GenTableMapper;
import com.nexus.generator.service.IGenTableService;
import com.nexus.online.domain.vo.DictVo;
import com.nexus.online.domain.vo.OnlFieldVo;
import com.nexus.online.domain.vo.TableVo;
import com.nexus.online.service.IOnlineService;
import com.nexus.system.service.ISysDictDataService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OnlineServiceImpl implements IOnlineService {

    @Autowired(required = false)
    private IGenTableService genTableService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private GenTableMapper genTableMapper;

    @Autowired(required = false)
    private ISysDictDataService sysDictDataService;

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
    public TableDataInfo listOnlineData(Long tableId, String tableName, int pageNum, int pageSize) {
        // 获取表单结构数据
        Map<String, Object> formData = getOnlineFormData(tableId);
        
        // 查询数据列表
        TableDataInfo info = new TableDataInfo();
        if (!isWhitelistedTable(tableName)) {
            info.setRows(Collections.emptyList());
            info.setTotal(0L);
            info.setMap(formData);
            return info;
        }
        
        int offset = (Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1);
        String countSql = "SELECT COUNT(1) FROM " + tableName;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        String dataSql = "SELECT * FROM " + tableName + " LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, pageSize, offset);
        
        // 处理 longblob 类型数据
        if (formData != null && !formData.isEmpty()) {
            TableVo tableVo = null;
            String type = (String) formData.get("type");
            if ("single".equals(type)) {
                tableVo = (TableVo) formData.get("data");
            } else if ("main-sub".equals(type)) {
                tableVo = (TableVo) formData.get("main");
            } else if ("sub".equals(type)) {
                tableVo = (TableVo) formData.get("data");
            }
            
            if (tableVo != null && tableVo.getColumnsVo() != null) {
                for (Map<String, Object> row : rows) {
                    for (OnlFieldVo field : tableVo.getColumnsVo()) {
                        if ("longblob".equalsIgnoreCase(field.getColumnType())) {
                            Object blob = row.get(field.getColumnName());
                            if (blob != null && blob instanceof byte[]) {
                                String content = new String((byte[]) blob, java.nio.charset.StandardCharsets.UTF_8);
                                row.put(field.getColumnName(), content);
                            }
                        }
                    }
                }
            }
        }
        
        info.setRows(rows);
        info.setTotal(total == null ? 0L : total);
        info.setMap(formData);
        return info;
    }

    @Override
    public Map<String, Object> getDetail(String tableName, String id) {
        if (!isWhitelistedTable(tableName)) {
            return Collections.emptyMap();
        }
        String sql = "SELECT * FROM " + tableName + " WHERE id = ? LIMIT 1";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.get(0);
    }

    @Override
    public Map<String, Object> getOnlineData(Long tableId, String tableName, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        
        if (!isWhitelistedTable(tableName)) {
            return Collections.emptyMap();
        }
        
        // 获取表结构信息
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            return Collections.emptyMap();
        }
        
        List<GenTableColumn> columnsList = table.getColumns();
        
        // 提取主键列名
        String pkName = columnsList.stream()
            .filter(column -> "1".equals(column.getIsPk()))
            .findFirst()
            .map(GenTableColumn::getColumnName)
            .orElse("id");
        
        // 获取表单结构数据
        Map<String, Object> formData = getOnlineFormData(tableId);
        String formType = (String) formData.get("type");
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        
        // 处理单表模式
        if ("single".equals(formType)) {
            TableVo tableVo = (TableVo) formData.get("data");
            String sql = "SELECT * FROM " + tableName + " WHERE " + pkName + " = ? LIMIT 1";
            List<Map<String, Object>> dataList = jdbcTemplate.queryForList(sql, id);
            if (!dataList.isEmpty()) {
                Map<String, Object> data = dataList.get(0);
                processBlobFields(tableVo.getColumnsVo(), data);
                removeSystemFields(data);
                result.putAll(data);
            }
        }
        // 处理主子表模式
        else if ("main-sub".equals(formType)) {
            // 获取主表配置
            TableVo mainTable = (TableVo) formData.get("main");
            // 获取子表配置
            TableVo subTable = (TableVo) formData.get("sub");
            
            // 查询主表数据
            String mainSql = "SELECT * FROM " + tableName + " WHERE " + pkName + " = ? LIMIT 1";
            List<Map<String, Object>> mainDataList = jdbcTemplate.queryForList(mainSql, id);
            if (!mainDataList.isEmpty()) {
                Map<String, Object> mainData = mainDataList.get(0);
                processBlobFields(mainTable.getColumnsVo(), mainData);
                removeSystemFields(mainData);
                result.put("main", mainData);
                
                // 查询子表数据
                String subTableName = subTable.getTableName();
                String fkName = table.getSubTableFkName();
                Object mainId = mainData.get(pkName);
                
                String subSql = "SELECT * FROM " + subTableName + " WHERE " + fkName + " = ?";
                List<Map<String, Object>> subData = jdbcTemplate.queryForList(subSql, mainId);
                processBlobFields(subTable.getColumnsVo(), subData);
                subData.forEach(this::removeSystemFields);
                result.put(subTableName, subData);
            }
        }
        
        return result;
    }

    private void removeSystemFields(Map<String, Object> data) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : data.keySet()) {
            if (isSystemField(key)) {
                keysToRemove.add(key);
            }
        }
        keysToRemove.forEach(data::remove);
    }

    private void processBlobFields(List<OnlFieldVo> columns, Map<String, Object> data) {
        if (columns == null || data == null) {
            return;
        }
        for (OnlFieldVo field : columns) {
            if ("longblob".equalsIgnoreCase(field.getColumnType())) {
                Object value = data.get(field.getColumnName());
                if (value instanceof byte[]) {
                    data.put(field.getColumnName(), new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
    }

    private void processBlobFields(List<OnlFieldVo> columns, List<Map<String, Object>> dataList) {
        if (dataList == null) {
            return;
        }
        for (Map<String, Object> data : dataList) {
            processBlobFields(columns, data);
        }
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

    @Override
    public GenTable getOnlineForm(Long tableId) {
        if (genTableService == null) {
            return null;
        }
        return genTableService.selectGenTableById(tableId);
    }

    @Override
    public Map<String, Object> getOnlineFormData(Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            return Collections.emptyMap();
        }
        
        TableVo tableVo = new TableVo();
        BeanUtils.copyProperties(table, tableVo);
        tableVo.setColumnsVo(processColumns(table.getColumns()));

        Map<String, Object> result = new HashMap<>();
        if ("main".equals(table.getTplCategory())) {
            GenTable subTable = genTableMapper.selectGenTableByName(table.getSubTableName());
            if (subTable != null) {
                TableVo subVo = new TableVo();
                BeanUtils.copyProperties(subTable, subVo);
                subVo.setColumnsVo(processColumns(subTable.getColumns()));
                List<OnlFieldVo> filteredColumns = processColumns(subTable.getColumns()).stream()
                    .filter(column ->
                        !"id".equals(column.getColumnName()) &&  
                        !table.getSubTableFkName().equals(column.getColumnName())  
                    )
                    .collect(Collectors.toList());
                subVo.setColumnsVo(filteredColumns);

                result.put("type", "main-sub");
                result.put("main", tableVo);
                result.put("sub", subVo);
                result.put("foreignKey", table.getSubTableFkName());
            }
        } else if ("crud".equals(table.getTplCategory()) || "np_crud".equals(table.getTplCategory())) {
            result.put("type", "single");
            result.put("data", tableVo);
        } else if ("sub".equals(table.getTplCategory()) || "np_sub".equals(table.getTplCategory())) {
            result.put("type", "sub");
            result.put("data", tableVo);
        }
        return result;
    }

    private List<OnlFieldVo> processColumns(List<GenTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return Collections.emptyList();
        }
        return columns.stream()
            .filter(col -> !isSystemField(col.getColumnName()))
            .map(col -> {
                OnlFieldVo vo = new OnlFieldVo();
                BeanUtils.copyProperties(col, vo);
                if (StringUtils.isNotEmpty(col.getDictType())) {
                    vo.setCommonDictCode(getColumnDict(col));
                }
                return vo;
            }).collect(Collectors.toList());
    }

    private boolean isSystemField(String columnName) {
        return StringUtils.equalsAnyIgnoreCase(columnName,
            "create_by", "create_time", "create_dept", "update_by", "update_time");
    }

    private List<DictVo> getColumnDict(GenTableColumn onlColumn) {
        String dictType = onlColumn.getDictType();
        if (StringUtils.isNotEmpty(dictType) && sysDictDataService != null) {
            SysDictData query = new SysDictData();
            query.setDictType(dictType);
            query.setStatus("0");
            List<SysDictData> dictDataList = sysDictDataService.selectDictDataList(query);
            if (dictDataList != null && !dictDataList.isEmpty()) {
                return dictDataList.stream()
                    .map(data -> {
                        DictVo vo = new DictVo();
                        vo.setId(data.getDictValue());
                        vo.setName(data.getDictLabel());
                        return vo;
                    })
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
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

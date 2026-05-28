package com.nexus.generator.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.domain.GenTableColumn;
import com.nexus.generator.domain.GenTableDesign;
import com.nexus.generator.mapper.GenTableColumnMapper;
import com.nexus.generator.mapper.GenTableDesignMapper;
import com.nexus.generator.mapper.GenTableMapper;
import com.nexus.generator.service.IGenTableService;
import com.nexus.generator.service.IUnifiedGenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 统一代码生成与Online设计服务实现
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UnifiedGenServiceImpl implements IUnifiedGenService {

    private final GenTableMapper genTableMapper;
    private final GenTableColumnMapper genTableColumnMapper;
    private final GenTableDesignMapper designMapper;
    private final IGenTableService genTableService;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final String[] TABLE_IGNORE = new String[]{"sj_", "flow_", "gen_"};

    // ==================== 设计管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdateDesign(GenTableDesign design) {
        if (design.getDesignId() == null) {
            designMapper.insert(design);
        } else {
            designMapper.updateById(design);
        }
        return design.getDesignId();
    }

    @Override
    public GenTableDesign getDesignById(Long designId) {
        return designMapper.selectById(designId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDesignById(Long designId) {
        designMapper.deleteById(designId);
    }

    @Override
    public List<GenTableDesign> listDesigns(String keyword) {
        LambdaQueryWrapper<GenTableDesign> qw = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            qw.like(GenTableDesign::getDesignName, keyword)
              .or().like(GenTableDesign::getTableName, keyword)
              .or().like(GenTableDesign::getTableComment, keyword);
        }
        qw.orderByDesc(GenTableDesign::getUpdateTime);
        return designMapper.selectList(qw);
    }

    @Override
    public String generateCreateTableSql(String designJson) {
        JSONObject root = JSON.parseObject(designJson);
        JSONArray tables = root.getJSONArray("tables");
        JSONArray relations = root.getJSONArray("relations");

        // 构建表->字段映射，便于外键校验
        Map<String, JSONArray> tableToFields = new HashMap<>();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                JSONObject t = tables.getJSONObject(i);
                tableToFields.put(StringUtils.defaultString(t.getString("name")), t.getJSONArray("fields"));
            }
        }

        StringBuilder full = new StringBuilder();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                JSONObject t = tables.getJSONObject(i);
                full.append(buildCreateTableSql(t));
            }
        }
        if (relations != null) {
            for (int i = 0; i < relations.size(); i++) {
                JSONObject r = relations.getJSONObject(i);
                String fromTable = r.getString("fromTable");
                String fromField = r.getString("fromField");
                String toTable = r.getString("toTable");
                String toField = r.getString("toField");
                if (StringUtils.isAnyBlank(fromTable, fromField, toTable, toField)) continue;

                // 字段存在且基础类型一致才生成外键
                JSONObject fromFld = findField(tableToFields.get(fromTable), fromField);
                JSONObject toFld = findField(tableToFields.get(toTable), toField);
                if (fromFld == null || toFld == null) continue;
                String fromBase = normalizeBaseType(fromFld.getString("type"));
                String toBase = normalizeBaseType(toFld.getString("type"));
                if (!StringUtils.equals(fromBase, toBase)) continue;

                String fkName = String.format("fk_%s_%s_%d", fromTable, fromField, i);
                full.append("ALTER TABLE `").append(fromTable).append("` ADD CONSTRAINT `")
                    .append(fkName).append("` FOREIGN KEY (`").append(fromField)
                    .append("`) REFERENCES `").append(toTable).append("`(`")
                    .append(toField).append("`);")
                    .append('\n');
            }
        }
        return full.toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeCreateTableSql(String sql, boolean overwriteIfExists) {
        if (StringUtils.isBlank(sql)) {
            return;
        }
        // 简单分句：按分号拆分，忽略空行
        String[] parts = sql.split(";\n|;\r\n|;\r|;");
        for (String raw : parts) {
            String s = StringUtils.trim(raw);
            if (StringUtils.isBlank(s)) continue;

            // 去除开头的单行与块注释再判断是否为 CREATE TABLE
            String sNoComments = removeLeadingComments(s);

            if (overwriteIfExists && isCreateTableStatement(sNoComments)) {
                String tableName = extractCreateTableName(sNoComments);
                if (StringUtils.isNotBlank(tableName)) {
                    try {
                        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
                    } catch (Exception e) {
                        throw new ServiceException("删除已有表失败：" + tableName);
                    }
                }
            }
            // 执行前移除所有注释，避免驱动不接受带注释语句
            String executable = removeAllComments(s);
            executable = StringUtils.trim(executable);
            if (StringUtils.isBlank(executable)) continue;
            try {
                jdbcTemplate.execute(executable);
            } catch (Exception e) {
                // 友好提示
                String lower = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (isCreateTableStatement(sNoComments)) {
                    if (lower.contains("exists") || lower.contains("already") || lower.contains("1050")) {
                        throw new ServiceException("表已经存在，创建失败！");
                    }
                    throw new ServiceException("创建表失败，请检查表字段类型与长度设置是否正确。");
                }
                throw new ServiceException("SQL执行失败，请检查设计配置。");
            }
        }
    }

    // ==================== 设计导入代码生成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importFromDesign(Long designId, String dataName, boolean overwriteIfExists) {
        GenTableDesign design = designMapper.selectById(designId);
        if (design == null) {
            throw new ServiceException("设计不存在");
        }

        try {
            JSONObject root = JSON.parseObject(design.getContent());
            JSONArray tables = root.getJSONArray("tables");
            if (tables == null || tables.isEmpty()) {
                throw new ServiceException("没有表数据");
            }

            List<GenTable> list = new ArrayList<>();
            for (int i = 0; i < tables.size(); i++) {
                JSONObject t = tables.getJSONObject(i);
                String tableName = t.getString("name");

                if (StringUtils.isBlank(tableName)) {
                    continue;
                }

                GenTable gt = new GenTable();
                gt.setDataName(StringUtils.defaultIfBlank(dataName, "master"));
                gt.setTableName(tableName);
                gt.setTableComment(StringUtils.defaultString(t.getString("comment"), tableName));
                gt.setClassName(capitalize(tableName));
                gt.setTplCategory("crud");
                gt.setPackageName("com.nexus.modules");
                gt.setModuleName("modules");
                gt.setFunctionName(gt.getTableComment());
                gt.setFunctionAuthor("system");
                gt.setBusinessName(tableName);
                gt.setSourceType("design");
                gt.setDesignId(designId);
                gt.setDesignContent(design.getContent());
                gt.setIsOnlineForm("1");

                List<GenTableColumn> cols = new ArrayList<>();
                JSONArray fields = t.getJSONArray("fields");
                if (fields != null) {
                    int sort = 1;
                    for (int j = 0; j < fields.size(); j++) {
                        JSONObject f = fields.getJSONObject(j);
                        String fieldName = f.getString("name");
                        String fieldType = f.getString("type");

                        if (StringUtils.isBlank(fieldName)) {
                            continue;
                        }

                        GenTableColumn c = new GenTableColumn();
                        c.setColumnName(fieldName);
                        c.setColumnComment(StringUtils.defaultString(f.getString("comment"), fieldName));
                        c.setColumnType(resolveColumnType(f));
                        c.setJavaType(resolveJavaType(fieldType));
                        c.setJavaField(toCamelCase(fieldName));
                        c.setIsPk(booleanToOne(f.getBoolean("isPrimaryKey")));
                        c.setIsIncrement("0");
                        c.setIsRequired(booleanToOne(f.getBoolean("isNotNull")));
                        c.setIsInsert("1");
                        c.setIsEdit("1");
                        c.setIsList("1");
                        c.setIsQuery("1");
                        c.setQueryType("LIKE");
                        c.setHtmlType(resolveHtmlType(fieldType, f));
                        c.setFormSpan(24);
                        c.setSort(sort++);
                        cols.add(c);
                    }
                }
                gt.setColumns(cols);
                list.add(gt);
            }

            if (list.isEmpty()) {
                throw new ServiceException("没有有效的表数据可以导入");
            }

            // 导入到代码生成
            genTableService.importGenTable(list, StringUtils.defaultIfBlank(dataName, "master"));

            // 更新设计的关联表ID
            GenTable importedTable = genTableMapper.selectGenTableByName(list.get(0).getTableName());
            if (importedTable != null) {
                design.setGenTableId(importedTable.getTableId());
                designMapper.updateById(design);
                return importedTable.getTableId();
            }
        } catch (Exception e) {
            log.error("导入表设计到代码生成失败", e);
            throw new ServiceException("导入失败: " + e.getMessage());
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncDesignToGenTable(Long tableId) {
        GenTable table = genTableMapper.selectById(tableId);
        if (table == null || table.getDesignId() == null) {
            return;
        }

        GenTableDesign design = designMapper.selectById(table.getDesignId());
        if (design == null) {
            return;
        }

        // 更新设计内容
        table.setDesignContent(design.getContent());
        genTableMapper.updateById(table);

        // 可以在这里添加更多同步逻辑
    }

    // ==================== Online表单数据操作 ====================

    @Override
    public Map<String, Object> getOnlineForm(Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> map = new HashMap<>();

        // 根据模板类型返回不同的数据结构
        String tplCategory = table.getTplCategory();
        if ("main".equals(tplCategory) && StringUtils.isNotBlank(table.getSubTableName())) {
            // 主子表模式
            GenTable subTable = genTableMapper.selectGenTableByName(table.getSubTableName());
            map.put("type", "main-sub");
            map.put("main", convertToTableVo(table));
            if (subTable != null) {
                map.put("sub", convertToTableVo(subTable));
                map.put("foreignKey", table.getSubTableFkName());
            }
        } else if ("sub".equals(tplCategory)) {
            // 子表模式
            map.put("type", "sub");
            map.put("data", convertToTableVo(table));
        } else {
            // 单表模式
            map.put("type", "single");
            map.put("data", convertToTableVo(table));
        }

        result.put("map", map);
        result.put("tableId", tableId);
        result.put("tableName", table.getTableName());
        result.put("tableComment", table.getTableComment());
        result.put("isOnlineForm", table.isOnlineFormEnabled());

        return result;
    }

    /**
     * 将 GenTable 转换为前端需要的 TableVo 结构
     */
    private Map<String, Object> convertToTableVo(GenTable table) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("tableId", table.getTableId());
        vo.put("tableName", table.getTableName());
        vo.put("tableComment", table.getTableComment());
        vo.put("className", table.getClassName());
        vo.put("tplCategory", table.getTplCategory());
        vo.put("columnsVo", convertColumns(table.getColumns()));
        return vo;
    }

    /**
     * 转换列信息
     */
    private List<Map<String, Object>> convertColumns(List<GenTableColumn> columns) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (columns == null) return result;

        for (GenTableColumn col : columns) {
            if (col == null) continue;
            Map<String, Object> vo = new HashMap<>();
            vo.put("columnId", col.getColumnId());
            vo.put("columnName", col.getColumnName());
            vo.put("columnComment", col.getColumnComment());
            vo.put("columnType", col.getColumnType());
            vo.put("javaType", col.getJavaType());
            vo.put("javaField", col.getJavaField());
            vo.put("isPk", col.isPk());
            vo.put("isRequired", col.isRequired());
            vo.put("isInsert", col.isInsert());
            vo.put("isEdit", col.isEdit());
            vo.put("isList", col.isList());
            vo.put("isQuery", col.isQuery());
            vo.put("queryType", col.getQueryType());
            vo.put("htmlType", col.getHtmlType());
            vo.put("dictType", col.getDictType());
            vo.put("sort", col.getSort());
            // Online扩展字段
            vo.put("formRules", col.getFormRules());
            vo.put("linkConfig", col.getLinkConfig());
            vo.put("formSpan", col.getFormSpan());
            vo.put("isReadonly", col.isReadonly());
            vo.put("fieldTip", col.getFieldTip());
            // 查询字典数据
            if (StringUtils.isNotBlank(col.getDictType())) {
                vo.put("commonDictCode", getDictData(col.getDictType()));
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 查询字典数据
     */
    private List<Map<String, Object>> getDictData(String dictType) {
        if (StringUtils.isBlank(dictType)) {
            return null;
        }
        try {
            String sql = "SELECT dict_value as id, dict_label as name FROM sys_dict_data WHERE dict_type = ?  ORDER BY dict_sort";
            List<Map<String, Object>> dictList = jdbcTemplate.queryForList(sql, dictType);
            return dictList.isEmpty() ? null : dictList;
        } catch (Exception e) {
            log.warn("查询字典数据失败, dictType: {}", dictType, e);
            return null;
        }
    }

    @Override
    public TableDataInfo<Map<String, Object>> listOnlineData(Long tableId, PageQuery pageQuery) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        // 构建允许查询的列
        Set<String> allowedColumns = buildAllowedColumns(table);

        // 构建查询SQL
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", allowedColumns));
        sql.append(" FROM ").append(table.getTableName());

        // 分页查询
        Page<Map<String, Object>> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString());

        // 获取总数
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table.getTableName(), Long.class);

        // 处理longblob类型字段
        List<Map<String, Object>> processedRecords = processBlobFields(records, table.getColumns());

        page.setRecords(processedRecords);
        page.setTotal(total != null ? total : 0);

        // 构建返回结果，包含map信息（与前端兼容）
        TableDataInfo<Map<String, Object>> result = TableDataInfo.build(page);

        // 构建map数据（表单配置信息）
        Map<String, Object> map = new HashMap<>();
        String tplCategory = table.getTplCategory();
        if ("main".equals(tplCategory) && StringUtils.isNotBlank(table.getSubTableName())) {
            // 主子表模式
            GenTable subTable = genTableMapper.selectGenTableByName(table.getSubTableName());
            map.put("type", "main-sub");
            map.put("main", convertToTableVo(table));
            if (subTable != null) {
                map.put("sub", convertToTableVo(subTable));
                map.put("foreignKey", table.getSubTableFkName());
            }
        } else if ("sub".equals(tplCategory)) {
            // 子表模式
            map.put("type", "sub");
            map.put("data", convertToTableVo(table));
        } else {
            // 单表模式
            map.put("type", "single");
            map.put("data", convertToTableVo(table));
        }
        result.setMap(map);

        return result;
    }

    /**
     * 处理BLOB类型字段，将byte[]转换为String
     */
    private List<Map<String, Object>> processBlobFields(List<Map<String, Object>> records, List<GenTableColumn> columns) {
        if (records == null || columns == null) return records;

        // 找出所有blob类型的字段
        List<String> blobFields = columns.stream()
            .filter(col -> col != null && StringUtils.containsIgnoreCase(col.getColumnType(), "blob"))
            .map(GenTableColumn::getColumnName)
            .collect(Collectors.toList());

        if (blobFields.isEmpty()) return records;

        // 处理每条记录的blob字段
        for (Map<String, Object> record : records) {
            for (String fieldName : blobFields) {
                Object value = record.get(fieldName);
                if (value instanceof byte[]) {
                    try {
                        String content = new String((byte[]) value, StandardCharsets.UTF_8);
                        record.put(fieldName, content);
                    } catch (Exception e) {
                        log.warn("转换BLOB字段失败, field: {}", fieldName, e);
                        record.put(fieldName, "");
                    }
                }
            }
        }
        return records;
    }

    @Override
    public Map<String, Object> getOnlineDataById(Long tableId, Long dataId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        // 获取主键列
        String pkColumn = getPkColumnName(table);
        if (pkColumn == null) {
            throw new ServiceException("表未配置主键: " + table.getTableName());
        }

        String sql = "SELECT * FROM " + table.getTableName() + " WHERE " + pkColumn + " = ?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, dataId);

        if (list.isEmpty()) {
            return null;
        }

        // 处理BLOB字段
        Map<String, Object> record = list.get(0);
        processBlobFieldsForSingle(record, table.getColumns());

        return record;
    }

    /**
     * 处理单条记录的BLOB字段
     */
    private void processBlobFieldsForSingle(Map<String, Object> record, List<GenTableColumn> columns) {
        if (record == null || columns == null) return;

        // 找出所有blob类型的字段
        List<String> blobFields = columns.stream()
            .filter(col -> col != null && StringUtils.containsIgnoreCase(col.getColumnType(), "blob"))
            .map(GenTableColumn::getColumnName)
            .collect(Collectors.toList());

        // 处理blob字段
        for (String fieldName : blobFields) {
            Object value = record.get(fieldName);
            if (value instanceof byte[]) {
                try {
                    String content = new String((byte[]) value, StandardCharsets.UTF_8);
                    record.put(fieldName, content);
                } catch (Exception e) {
                    log.warn("转换BLOB字段失败, field: {}", fieldName, e);
                    record.put(fieldName, "");
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addOrUpdateOnlineData(Long tableId, Map<String, Object> data) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        return addOrUpdateOnlineDataInternal(table, data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> data) {
        GenTable table = genTableMapper.selectGenTableByName(tableName);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableName);
        }

        return addOrUpdateOnlineDataInternal(table, data);
    }

    private int addOrUpdateOnlineDataInternal(GenTable table, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return 0;
        }

        // 过滤字段
        Set<String> allowedColumns = buildAllowedColumns(table);
        Map<String, Object> filteredData = data.entrySet().stream()
            .filter(e -> allowedColumns.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (filteredData.isEmpty()) {
            return 0;
        }

        // 判断是新增还是更新
        String pkColumn = getPkColumnName(table);
        Object pkValue = pkColumn != null ? filteredData.get(pkColumn) : null;

        if (pkValue != null) {
            // 更新
            StringBuilder sql = new StringBuilder("UPDATE ").append(table.getTableName()).append(" SET ");
            List<Object> params = new ArrayList<>();

            boolean first = true;
            for (Map.Entry<String, Object> entry : filteredData.entrySet()) {
                if (entry.getKey().equals(pkColumn)) continue;
                if (!first) sql.append(", ");
                sql.append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
                first = false;
            }

            sql.append(" WHERE ").append(pkColumn).append(" = ?");
            params.add(pkValue);

            return jdbcTemplate.update(sql.toString(), params.toArray());
        } else {
            // 新增
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(table.getTableName()).append(" (");
            StringBuilder values = new StringBuilder("VALUES (");
            List<Object> params = new ArrayList<>();

            boolean first = true;
            for (Map.Entry<String, Object> entry : filteredData.entrySet()) {
                if (!first) {
                    sql.append(", ");
                    values.append(", ");
                }
                sql.append(entry.getKey());
                values.append("?");
                params.add(entry.getValue());
                first = false;
            }

            sql.append(") ").append(values).append(")");
            return jdbcTemplate.update(sql.toString(), params.toArray());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOnlineData(Long tableId, List<Long> ids) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        String pkColumn = getPkColumnName(table);
        if (pkColumn == null) {
            throw new ServiceException("表未配置主键: " + table.getTableName());
        }

        String sql = "DELETE FROM " + table.getTableName() + " WHERE " + pkColumn + " IN (" +
                     ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        jdbcTemplate.execute(sql);
    }

    // ==================== 动态表查询 ====================

    @Override
    public TableDataInfo<Map<String, Object>> selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page) {
        if (!isSafeIdentifier(tableName)) {
            throw new ServiceException("非法表名");
        }

        String sql = "SELECT * FROM " + tableName;
        if (wrapper != null) {
            String customSqlSegment = wrapper.getCustomSqlSegment();
            if (StringUtils.isNotBlank(customSqlSegment)) {
                sql += " " + customSqlSegment;
            }
        }

        String countSql = "SELECT COUNT(*) FROM " + tableName;
        if (wrapper != null) {
            String customSqlSegment = wrapper.getCustomSqlSegment();
            if (StringUtils.isNotBlank(customSqlSegment)) {
                countSql += " " + customSqlSegment;
            }
        }

        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql + " LIMIT " + page.getSize() + " OFFSET " + ((page.getCurrent() - 1) * page.getSize()));

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Page<Map<String, Object>> resultPage = new Page<>();
        resultPage.setCurrent(page.getCurrent());
        resultPage.setSize(page.getSize());
        resultPage.setRecords(records);
        resultPage.setTotal(total != null ? total : 0);

        return TableDataInfo.build(resultPage);
    }

    @Override
    public Map<String, Object> selectDynamicTableById(String tableName, String id) {
        if (!isSafeIdentifier(tableName)) {
            throw new ServiceException("非法表名");
        }

        String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Map<String, Object> getPopupDetail(Long tableId, String dictTableName, String id) {
        // 验证表名权限
        assertAllowedPopupDictTableByContext(tableId, dictTableName);

        if (!isSafeIdentifier(dictTableName)) {
            throw new ServiceException("非法表名");
        }

        String sql = "SELECT * FROM " + dictTableName + " WHERE id = ?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public TableDataInfo<Map<String, Object>> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery) {
        // 验证表名权限
        assertAllowedPopupDictTableByContext(tableId, dictTableName);

        if (!isSafeIdentifier(dictTableName)) {
            throw new ServiceException("非法表名");
        }

        String sql = "SELECT * FROM " + dictTableName;
        Page<Map<String, Object>> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql + " LIMIT " + page.getSize() + " OFFSET " + ((page.getCurrent() - 1) * page.getSize()));

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + dictTableName, Long.class);
        page.setRecords(records);
        page.setTotal(total != null ? total : 0);

        return TableDataInfo.build(page);
    }

    // ==================== 代码生成（委托给原有服务） ====================

    @Override
    public Map<String, String> previewCode(Long tableId) {
        return genTableService.previewCode(tableId);
    }

    @Override
    public byte[] downloadCode(Long tableId) {
        return genTableService.downloadCode(tableId);
    }

    @Override
    public byte[] downloadCodeBatch(Long[] tableIds) {
        String[] ids = Arrays.stream(tableIds).map(String::valueOf).toArray(String[]::new);
        return genTableService.downloadCode(ids);
    }

    @Override
    public void generatorCode(Long tableId) {
        genTableService.generatorCode(tableId);
    }

    @Override
    public void synchDb(Long tableId) {
        genTableService.synchDb(tableId);
    }

    // ==================== 私有工具方法 ====================

    private boolean isSafeIdentifier(String name) {
        return StringUtils.isNotBlank(name) && SAFE_IDENTIFIER.matcher(name).matches();
    }

    private Set<String> buildAllowedColumns(GenTable table) {
        Set<String> cols = new HashSet<>();
        if (table != null && table.getColumns() != null) {
            for (GenTableColumn c : table.getColumns()) {
                if (c == null) continue;
                String name = c.getColumnName();
                if (isSafeIdentifier(name)) cols.add(name);
            }
        }
        // 系统字段（只添加表中实际存在的字段）
        // 注意：不自动添加id，因为主键可能是其他名称（如notice_id）
        cols.add("create_by");
        cols.add("create_time");
        cols.add("update_by");
        cols.add("update_time");
        return cols;
    }

    private String getPkColumnName(GenTable table) {
        if (table.getColumns() != null) {
            for (GenTableColumn c : table.getColumns()) {
                if (c.isPk()) {
                    return c.getColumnName();
                }
            }
        }
        // 默认使用id
        return "id";
    }

    private void assertAllowedPopupDictTableByContext(Long tableId, String dictTableName) {
        if (tableId == null) {
            throw new ServiceException("tableId不能为空");
        }
        if (!isSafeIdentifier(dictTableName)) {
            throw new ServiceException("非法表名");
        }

        GenTable ctx = genTableService.selectGenTableById(tableId);
        if (ctx == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        boolean allowed = false;
        if (ctx.getColumns() != null) {
            for (GenTableColumn c : ctx.getColumns()) {
                if (c == null) continue;
                if (StringUtils.equalsIgnoreCase("popup", c.getHtmlType())
                    && StringUtils.isNotBlank(c.getDictType())
                    && StringUtils.equals(c.getDictType(), dictTableName)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            throw new ServiceException("无权限访问弹窗来源表: " + dictTableName);
        }
    }

    private String buildCreateTableSql(JSONObject table) {
        String tableName = table.getString("name");
        String engine = StringUtils.defaultIfBlank(table.getString("engine"), "InnoDB");
        String charset = StringUtils.defaultIfBlank(table.getString("charset"), "utf8mb4");
        String comment = StringUtils.defaultString(table.getString("comment"));
        JSONArray fields = table.getJSONArray("fields");
        List<String> columnDefs = new ArrayList<>();
        List<String> pks = new ArrayList<>();
        List<String> unique = new ArrayList<>();
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                JSONObject f = fields.getJSONObject(i);
                String name = f.getString("name");
                String type = StringUtils.defaultIfBlank(f.getString("type"), "varchar");
                Integer length = f.getInteger("length");
                Integer precision = f.getInteger("precision");
                Integer scale = f.getInteger("scale");
                boolean notNull = f.getBooleanValue("isNotNull");
                boolean isPk = f.getBooleanValue("isPrimaryKey");
                boolean isUnique = f.getBooleanValue("isUnique");
                String defVal = f.getString("defaultValue");
                String colComment = f.getString("comment");

                String typeLower = type.toLowerCase();
                String typeDef = type;
                if (supportsLength(typeLower)) {
                    int effLen = (length != null) ? length : 255;
                    typeDef = typeLower + "(" + effLen + ")";
                } else if (isDecimalType(typeLower) && (precision != null || scale != null)) {
                    int p = precision != null ? precision : 10;
                    int s = scale != null ? scale : 0;
                    typeDef = typeLower + "(" + p + "," + s + ")";
                }

                StringBuilder col = new StringBuilder("  `").append(name).append("` ").append(typeDef);
                if (notNull) col.append(" NOT NULL");
                if (StringUtils.isNotBlank(defVal)) {
                    if (isNumericType(typeLower)) {
                        col.append(" DEFAULT ").append(defVal);
                    } else if ("current_timestamp".equals(defVal.toLowerCase())) {
                        col.append(" DEFAULT CURRENT_TIMESTAMP");
                    } else {
                        col.append(" DEFAULT '").append(defVal.replace("'", "''")).append("'");
                    }
                }
                if (StringUtils.isNotBlank(colComment)) {
                    col.append(" COMMENT '").append(colComment.replace("'", "''")).append("'");
                }
                columnDefs.add(col.toString());
                if (isPk) pks.add(name);
                if (isUnique && !isPk) unique.add(name);
            }
        }
        if (!pks.isEmpty()) {
            columnDefs.add("  PRIMARY KEY (" + String.join(", ", pks.stream().map(n -> "`" + n + "`").toList()) + ")");
        }
        for (String uq : unique) {
            columnDefs.add("  UNIQUE KEY `uk_" + tableName + "_" + uq + "` (`" + uq + "`)");
        }
        StringBuilder sql = new StringBuilder();
        sql.append("-- 创建表 ").append(tableName).append('\n');
        sql.append("CREATE TABLE `").append(tableName).append("` (\n");
        sql.append(String.join(",\n", columnDefs));
        sql.append("\n) ENGINE=").append(engine).append(" DEFAULT CHARSET=").append(charset);
        if (StringUtils.isNotBlank(comment)) sql.append(" COMMENT='").append(comment.replace("'", "''")).append("'");
        sql.append(";\n\n");
        return sql.toString();
    }

    private JSONObject findField(JSONArray fields, String name) {
        if (fields == null || StringUtils.isBlank(name)) return null;
        for (int i = 0; i < fields.size(); i++) {
            JSONObject f = fields.getJSONObject(i);
            if (StringUtils.equals(name, f.getString("name"))) return f;
        }
        return null;
    }

    private String normalizeBaseType(String type) {
        if (type == null) return "";
        String lower = type.toLowerCase();
        int p = lower.indexOf('(');
        return p > 0 ? lower.substring(0, p) : lower;
    }

    private boolean supportsLength(String type) {
        return List.of("varchar", "char", "varbinary").contains(type.toLowerCase());
    }

    private boolean isDecimalType(String type) {
        return List.of("decimal", "numeric", "number").contains(type.toLowerCase());
    }

    private boolean isNumericType(String type) {
        return List.of("int", "integer", "bigint", "smallint", "tinyint", "float", "double", "decimal", "numeric").contains(type.toLowerCase());
    }

    private String removeLeadingComments(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return sqlStmt;
        String[] lines = sqlStmt.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : lines) {
            String l = line;
            String trimmed = l.trim();
            if (!inBlock) {
                if (trimmed.startsWith("/*")) {
                    inBlock = true;
                    if (trimmed.endsWith("*/")) {
                        inBlock = false;
                    }
                    continue;
                }
                if (trimmed.startsWith("--")) {
                    continue;
                }
                sb.append(line).append('\n');
            } else {
                if (trimmed.endsWith("*/")) {
                    inBlock = false;
                }
            }
        }
        return sb.toString().trim();
    }

    private String removeAllComments(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return sqlStmt;
        // 去除块注释 /* ... */
        String noBlock = sqlStmt.replaceAll("/\\*([\\s\\S]*?)\\*/", " ");
        // 去除行注释 -- 直到行尾
        String[] lines = noBlock.split("\\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int idx = line.indexOf("--");
            String part = idx >= 0 ? line.substring(0, idx) : line;
            sb.append(part).append('\n');
        }
        return sb.toString();
    }

    private boolean isCreateTableStatement(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return false;
        String lower = sqlStmt.trim().toLowerCase();
        return lower.startsWith("create table");
    }

    private String extractCreateTableName(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:`([^`]+)`|([a-zA-Z0-9_]+))");
        java.util.regex.Matcher m = p.matcher(sqlStmt);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return StringUtils.isNotBlank(g1) ? g1 : g2;
        }
        return null;
    }

    private String resolveColumnType(JSONObject f) {
        String type = StringUtils.defaultIfBlank(f.getString("type"), "varchar").toLowerCase();
        Integer length = f.getInteger("length");
        Integer precision = f.getInteger("precision");
        Integer scale = f.getInteger("scale");
        if (supportsLength(type)) {
            int effLen = (length != null) ? length : 255;
            return type + "(" + effLen + ")";
        }
        if (isDecimalType(type) && (precision != null || scale != null)) {
            int p = precision != null ? precision : 10;
            int s = scale != null ? scale : 0;
            return type + "(" + p + "," + s + ")";
        }
        return type;
    }

    private String resolveJavaType(String fieldType) {
        if (StringUtils.isBlank(fieldType)) return "String";

        String type = fieldType.toLowerCase();
        if (type.contains("int") || type.contains("bigint") || type.contains("smallint") || type.contains("tinyint")) {
            return "Long";
        } else if (type.contains("decimal") || type.contains("numeric") || type.contains("number")) {
            return "BigDecimal";
        } else if (type.contains("float") || type.contains("double")) {
            return "Double";
        } else if (type.contains("date") || type.contains("time") || type.contains("timestamp")) {
            return "Date";
        } else if (type.contains("text") || type.contains("longtext")) {
            return "String";
        } else {
            return "String";
        }
    }

    private String resolveHtmlType(String fieldType, JSONObject field) {
        if (StringUtils.isBlank(fieldType)) return "input";

        String type = fieldType.toLowerCase();
        if (type.contains("date") || type.contains("time")) {
            return "datetime";
        } else if (type.contains("text") || type.contains("longtext")) {
            return "textarea";
        } else if (type.contains("int") || type.contains("decimal") || type.contains("numeric")) {
            return "input";
        } else {
            return "input";
        }
    }

    private String booleanToOne(Boolean value) {
        return (value != null && value) ? "1" : "0";
    }

    private String capitalize(String s) {
        if (StringUtils.isBlank(s)) return "Entity";
        String[] parts = s.split("_+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : "");
        }
        return b.toString();
    }

    private String toCamelCase(String fieldName) {
        if (StringUtils.isBlank(fieldName)) return fieldName;

        String[] parts = fieldName.split("_+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (StringUtils.isBlank(part)) continue;

            if (i == 0) {
                result.append(part.toLowerCase());
            } else {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
}

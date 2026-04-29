package org.dromara.online.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.alibaba.fastjson2.JSONObject;

import org.dromara.common.core.exception.ServiceException;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.generator.domain.GenTable;
import org.dromara.generator.domain.GenTableColumn;
import org.dromara.generator.mapper.GenTableMapper;
import org.dromara.generator.service.IGenTableService;
import org.dromara.online.domain.vo.DictVo;
import org.dromara.online.domain.vo.OnlFieldVo;
import org.dromara.online.domain.vo.TableVo;
import org.dromara.online.mapper.OnlineMapper;
import org.dromara.online.service.IFormService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * online表单 服务层实现
 *
 * @author nbacheng
 */

@Slf4j
@RequiredArgsConstructor
@Service
public class FormServiceImpl implements IFormService {

	private final GenTableMapper baseMapper;
	private final IGenTableService genTableService;
	private final OnlineMapper onlineMapper;

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private boolean isSafeIdentifier(String name) {
        return StringUtils.isNotBlank(name) && SAFE_IDENTIFIER.matcher(name).matches();
    }

    /**
     * 以 tableId 为上下文，校验本次请求允许访问的数据表（主表/子表）。
     */
    private void assertAllowedDataTableByContext(Long tableId, String tableName) {
        if (tableId == null) {
            throw new ServiceException("tableId不能为空");
        }
        if (!isSafeIdentifier(tableName)) {
            throw new ServiceException("非法表名");
        }

        GenTable ctx = genTableService.selectGenTableById(tableId);
        if (ctx == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }

        // 允许访问：主表本身
        if (StringUtils.equals(ctx.getTableName(), tableName)) return;

        // 允许访问：子表（主子表场景）
        if (StringUtils.isNotBlank(ctx.getSubTableName()) && StringUtils.equals(ctx.getSubTableName(), tableName)) return;

        throw new ServiceException("无权限访问表: " + tableName);
    }

    /**
     * 以 tableId 为上下文，校验本次请求允许访问的 popup 来源表（字段配置里的 popup.dictType）。
     */
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

    /**
     * 无 tableId 上下文时的表名白名单：仅允许访问已导入到代码生成的表。
     */
    private GenTable requireGenTableByName(String tableName) {
        if (!isSafeIdentifier(tableName)) {
            throw new ServiceException("非法表名");
        }
        GenTable t = baseMapper.selectGenTableByName(tableName);
        if (t == null) {
            throw new ServiceException("表未纳入online配置白名单: " + tableName);
        }
        // Some mapper methods may not hydrate columns; reload by id for column whitelist.
        if (CollectionUtils.isEmpty(t.getColumns()) && t.getTableId() != null) {
            GenTable hydrated = genTableService.selectGenTableById(t.getTableId());
            if (hydrated != null) {
                t = hydrated;
            }
        }
        return t;
    }

    private Set<String> buildAllowedColumns(GenTable table) {
        return buildAllowedColumns(table, false);
    }

    private Set<String> buildAllowedColumns(GenTable table, boolean excludePk) {
        Set<String> cols = new java.util.HashSet<>();
        String pkName = "";
        if (excludePk && table != null && table.getColumns() != null) {
            // 获取主键名
            for (GenTableColumn c : table.getColumns()) {
                if (c == null) continue;
                if ("1".equals(c.getIsPk())) {
                    pkName = c.getColumnName();
                    break;
                }
            }
        }
        if (table != null && table.getColumns() != null) {
            for (GenTableColumn c : table.getColumns()) {
                if (c == null) continue;
                String name = c.getColumnName();
                // 排除主键列（新增时）
                if (excludePk && StringUtils.equals(name, pkName)) continue;
                if (isSafeIdentifier(name)) cols.add(name);
            }
        }
        // system fields (if present in physical table, they can be written/read)
        cols.add("tenant_id");
        cols.add("create_by");
        cols.add("create_dept");
        cols.add("create_time");
        cols.add("update_by");
        cols.add("update_time");
        return cols;
    }

    private void filterMapKeysInPlace(Map<String, Object> data, Set<String> allowed) {
        if (data == null || data.isEmpty()) return;
        data.keySet().removeIf(k -> !isSafeIdentifier(k) || !allowed.contains(k));
    }

	@Override
	public Map<String, Object> getOnlineForm(Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        if (table == null) {
            throw new ServiceException("表配置不存在: " + tableId);
        }
        TableVo tableVo = new TableVo();
        BeanUtils.copyProperties(table, tableVo);
        tableVo.setColumnsVo(processColumns(table.getColumns()));

        Map<String, Object> result = new HashMap<>();
        if ("main".equals(table.getTplCategory())) {
            GenTable subTable = baseMapper.selectGenTableByName(table.getSubTableName());
            if (subTable == null) {
                throw new ServiceException("子表配置不存在: " + table.getSubTableName());
            }
            TableVo subVo = new TableVo();
            BeanUtils.copyProperties(subTable, subVo);
            subVo.setColumnsVo(processColumns(subTable.getColumns()));
            //过滤子表ID列（新增过滤逻辑）
            List<OnlFieldVo> filteredColumns = processColumns(subTable.getColumns()).stream()
                .filter(column ->
                    !"id".equals(column.getColumnName()) &&  // 过滤 id
                        !table.getSubTableFkName().equals(column.getColumnName())  // 过滤外键（main_id）
                )
                .collect(Collectors.toList());
            subVo.setColumnsVo(filteredColumns);

            result.put("type", "main-sub");
            result.put("main", tableVo);
            result.put("sub", subVo);
            result.put("foreignKey", table.getSubTableFkName());
        } else if ("single".equals(table.getTplCategory())) {
            result.put("type", "single");
            result.put("data", tableVo);
        } else if ("sub".equals(table.getTplCategory())) {
            result.put("type", "sub");
            result.put("data", tableVo);
        }
        return result;
	}

    // 处理字段公共方法
    private List<OnlFieldVo> processColumns(List<GenTableColumn> columns) {
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
            "create_by", "create_time", "create_dept", "update_by", "update_time", "tenant_id");
    }

    // 修改保存方法
    @Transactional
    public void saveMainSubData(Long mainTableId, Map<String, Object> mainData, List<Map<String, Object>> subDataList) {
        // 保存主表
        GenTable mainTable = genTableService.selectGenTableById(mainTableId);
        String pkName = getPkName(mainTableId);
        int mainResult = addOrUpdateOnlineData(mainTableId, mainTable.getTableName(), mainData);

        if (mainResult > 0 && !CollectionUtils.isEmpty(subDataList)) {
            Object mainId = mainData.get(pkName);
            // 删除旧子表数据
            deleteSubByForeignKey(mainTable, mainId);

            // 保存新子表数据
            subDataList.forEach(sub -> {
                sub.put(mainTable.getSubTableFkName(), mainId);
                addOrUpdateOnlineData(
                    baseMapper.selectGenTableByName(mainTable.getSubTableName()).getTableId(),
                    mainTable.getSubTableName(),
                    sub
                );
            });
        }
    }

    private void deleteSubByForeignKey(GenTable mainTable, Object mainId) {
        onlineMapper.deleteSubByForeignKey(
            mainTable.getSubTableName(),
            mainTable.getSubTableFkName(),
            mainId
        );
    }

	@Override
	public TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery) {
		Page<GenTable> page = baseMapper.selectPage(pageQuery.build(), this.buildGenTableQueryWrapper(genTable));
        return TableDataInfo.build(page);
	}

	private QueryWrapper<GenTable> buildGenTableQueryWrapper(GenTable genTable) {
        Map<String, Object> params = genTable.getParams();
        QueryWrapper<GenTable> wrapper = Wrappers.query();
        wrapper
            .eq(StringUtils.isNotEmpty(genTable.getDataName()),"data_name", genTable.getDataName())
            .like(StringUtils.isNotBlank(genTable.getTableName()), "lower(table_name)", StringUtils.lowerCase(genTable.getTableName()))
            .like(StringUtils.isNotBlank(genTable.getTableComment()), "lower(table_comment)", StringUtils.lowerCase(genTable.getTableComment()))
            .between(params.get("beginTime") != null && params.get("endTime") != null,
                "create_time", params.get("beginTime"), params.get("endTime"));
        return wrapper;
    }

    public  List<DictVo> getColumnDict(GenTableColumn onlColumn){
    	String dictType = onlColumn.getDictType();
    	if (StringUtils.isNotEmpty(dictType)) {
    		List<DictVo> dictVoList = onlineMapper.getDictData(dictType, TenantHelper.getTenantId());
    		return dictVoList;
    	}
		return null;
    }

	@Override
	public TableDataInfo<Map> listOnlineData(Long tableId, String tableName, PageQuery pageQuery) {
        // data table whitelist
        assertAllowedDataTableByContext(tableId, tableName);

        // column whitelist for query params (based on the actual data tableName)
        GenTable dataTable = requireGenTableByName(tableName);
        Set<String> allowedCols = buildAllowedColumns(dataTable);

		QueryWrapper<Map> wrapper = Wrappers.query();
		wrapper.eq("tenant_id", TenantHelper.getTenantId());
		JSONObject params = pageQuery.getParams();
		if (ObjectUtils.isNotEmpty(params)) {
			//遍历查询JSON对象
			for(String key : params.keySet()) {
				Object queryValue = params.get(key);
                if (allowedCols.contains(key) && isSafeIdentifier(key)) {
                    wrapper.eq(key,queryValue);
                }
			}
		}
		Page<Map> page = onlineMapper.getOnlineData(tableName, pageQuery.build(), wrapper);
		List<Map> records = page.getRecords();
		Map<String, Object> onlineField = getOnlineForm(tableId);
        TableVo tableVo = new TableVo();
        if ( onlineField.get("type").equals("single") ) {
            tableVo = (TableVo) onlineField.get("data");
        }
        else if( onlineField.get("type").equals("main-sub") ) {
            tableVo = (TableVo) onlineField.get("main");
        }
        else if( onlineField.get("type").equals("sub") ) {
            tableVo = (TableVo) onlineField.get("data");
        }
		List<OnlFieldVo> columnsList = tableVo.getColumnsVo();
		for (Map map : records) { //对longblob数据类型做特殊处理
			for (OnlFieldVo onlineFieldVo : columnsList) {
				if ( StringUtils.equalsAny(onlineFieldVo.getColumnType(), "longblob")) {
					Object blob = (Object) map.get(onlineFieldVo.getColumnName());
					String content;
                    if(ObjectUtils.isNotEmpty(blob)) {
                        content = new String((byte[])blob, StandardCharsets.UTF_8);
                    }
                    else {
                        content = "";
                    }
                    map.put(onlineFieldVo.getColumnName(), content); // 更新longblob内容
                }
			}
		}
		page.setRecords(records);
		return TableDataInfo.build(page,onlineField);
	}

    @Override
    @Transactional
    public int addOrUpdateOnlineData(Long tableId, String tableName, Map<String, Object> map) {
        // data table whitelist
        assertAllowedDataTableByContext(tableId, tableName);

        // 判断是否为主子表结构
        boolean isMainSub = map.containsKey("main") && map.containsKey("sub");

        // 处理主表数据
        Map<String, Object> mainData = isMainSub ? (Map<String, Object>) map.get("main") : map;
        // 获取表结构信息
        GenTable table = genTableService.selectGenTableById(tableId);

        String mainPkName = getPkName(tableId);

        // whitelist columns: main table
        GenTable mainTableMeta = requireGenTableByName(tableName);

        int result = 0;

        try {
            if (isMainSub) {
                // ============= 主子表处理逻辑 =============
                Map<String, Object> subData = (Map<String, Object>) map.get("sub");
                String subTableName = (String) subData.get("tableName");
                List<Map<String, Object>> subRows = (List<Map<String, Object>>) subData.get("data");

                // enforce sub table name
                if (!StringUtils.equals(table.getSubTableName(), subTableName)) {
                    throw new ServiceException("子表名不匹配: " + subTableName);
                }
                GenTable subTableMeta = requireGenTableByName(subTableName);
                String subPkName = getPkName(subTableMeta.getTableId());

                // 判断主表是新增还是更新
                Object mainPkValue = mainData.get(mainPkName);
                boolean isMainUpdate = mainData.containsKey(mainPkName) && ObjectUtils.isNotEmpty(mainPkValue) && !"".equals(mainPkValue);

                // 新增时排除主键列，更新时包含主键列
                Set<String> mainAllowed = buildAllowedColumns(mainTableMeta, !isMainUpdate);
                Set<String> subAllowed = buildAllowedColumns(subTableMeta, true); // 子表总是新增，排除主键

                // 处理主表
                filterMapKeysInPlace(mainData, mainAllowed);
                Object mainId = processMainTable(tableName, mainData, mainPkName);
                String fkName = getSubTableFk(table); // 这里table是主表的GenTable对象
                if (!subAllowed.contains(fkName)) {
                    throw new ServiceException("子表外键字段未纳入白名单: " + fkName);
                }

                // 处理子表
                if (mainId != null && subRows != null && !subRows.isEmpty()) {
                    // filter each sub row keys
                    for (Map<String, Object> r : subRows) {
                        filterMapKeysInPlace(r, subAllowed);
                    }
                    processSubTable(subTableName, fkName, mainId, subRows, subPkName);
                }

                result = 1;
            } else {
                // ============= 单表处理逻辑 =============
                // 判断是新增还是更新
                Object pkValue = mainData.get(mainPkName);
                boolean isUpdate = mainData.containsKey(mainPkName) && ObjectUtils.isNotEmpty(pkValue) && !"".equals(pkValue);

                // 新增时排除主键列，更新时包含主键列
                Set<String> mainAllowed = buildAllowedColumns(mainTableMeta, !isUpdate);

                filterMapKeysInPlace(mainData, mainAllowed);
                result = processSingleTable(tableName, mainData, mainPkName);
            }

        } catch (Exception e) {
            throw new ServiceException("操作失败：" + e.getMessage());
        }
        return result;
    }

    private Object processMainTable(String tableName, Map<String, Object> data, String pkName) {
        Object pkValue = data.get(pkName);
        boolean isUpdate = data.containsKey(pkName) && ObjectUtils.isNotEmpty(pkValue) && !"".equals(pkValue);
        Object mainId = null;

        if (isUpdate) {
            // 更新主表
            handleCommonFields(data, false);
            Map<String, Object> where = new HashMap<>();
            where.put(pkName, data.get(pkName));
            data.remove(pkName);
            onlineMapper.dynamicUpdate(tableName, data, where);
            mainId = where.get(pkName);
        } else {
            // 新增主表 - 移除主键字段，让数据库自增
            // 强制移除主键字段，确保数据库能自动生成自增ID
            if (StringUtils.isNotBlank(pkName)) {
                data.remove(pkName);
            }
            handleCommonFields(data, true);
            onlineMapper.insertDynamic(tableName, data);
            // 获取自增主键（根据具体数据库实现）
            mainId = data.get(pkName);
        }
        return mainId;
    }

    private void processSubTable(String subTableName, String fkName, Object mainId,
                                 List<Map<String, Object>> subRows, String subPkName) {
        // 删除旧子表数据（更新时）
        if (mainId != null) {
            Map<String, Object> deleteWhere = new HashMap<>();
            deleteWhere.put(fkName, mainId);
            onlineMapper.dynamicDelete(subTableName, deleteWhere);
        }

        // 插入新子表数据
        for (Map<String, Object> row : subRows) {
            row.put(fkName, mainId); // 关联主表ID
            // 移除子表主键，让数据库自增
            if (subPkName != null) {
                row.remove(subPkName);
            }
            handleCommonFields(row, true);
            onlineMapper.insertDynamic(subTableName, row);
        }
    }

    private int processSingleTable(String tableName, Map<String, Object> data, String pkName) {
        Object pkValue = data.get(pkName);
        if (data.containsKey(pkName) && ObjectUtils.isNotEmpty(pkValue) && !"".equals(pkValue)) {
            // 更新操作
            handleCommonFields(data, false);
            Map<String, Object> where = new HashMap<>();
            where.put(pkName, data.get(pkName));
            data.remove(pkName);
            return onlineMapper.dynamicUpdate(tableName, data, where);
        } else {
            // 新增操作 - 移除主键字段，让数据库自增
            // 强制移除主键字段，确保数据库能自动生成自增ID
            if (StringUtils.isNotBlank(pkName)) {
                data.remove(pkName);
            }
            handleCommonFields(data, true);
            return onlineMapper.insertDynamic(tableName, data);
        }
    }

    private void handleCommonFields(Map<String, Object> data, boolean isCreate) {
        // 公共字段处理
        Long userId = LoginHelper.getUserId();
        Long deptId = LoginHelper.getDeptId();
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (isCreate) {
            data.put("tenant_id", TenantHelper.getTenantId());
            data.put("create_by", userId);
            data.put("create_dept", deptId);
            data.put("create_time", time);
        } else {
            data.put("update_by", userId);
            data.put("update_time", time);
        }

        // 移除可能存在的空字段
        data.entrySet().removeIf(entry -> entry.getValue() == null);
    }

    @Override
    @Transactional
    public void deleteOnlineData(Long tableId, String tableName, List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new IllegalArgumentException("删除ID不能为空");
        }

        assertAllowedDataTableByContext(tableId, tableName);

        // 获取主表元数据
        GenTable table = genTableService.selectGenTableById(tableId);
        String pkName = getPkName(tableId);

        // 处理子表数据删除（如果存在）
        if (isHasSubTable(table)) {  // 新增判断是否存在子表的方法
            String fkName = getSubTableFk(table);
            String subName = table.getSubTableName();

            Map<String, Object> deleteWhere = new HashMap<>();
            deleteWhere.put(fkName, ids);
            onlineMapper.dynamicDelete(subName, deleteWhere);
        }

        // 删除主表数据
        onlineMapper.deleteDynamicTable(tableName, pkName, ids);
    }

    // 判断是否存在子表
    private boolean isHasSubTable(GenTable table) {
        return table != null
            && StringUtils.isNotBlank(table.getSubTableName())
            && StringUtils.isNotBlank(getSubTableFk(table));
    }

	@Override
	public Map<String, Object> getOnlineData(Long tableId, String tableName, Long id) {
        // 参数校验
        if (id == null) {
            throw new IllegalArgumentException("ID不能为空");
        }

        assertAllowedDataTableByContext(tableId, tableName);

        // 获取表结构信息
        GenTable table = genTableService.selectGenTableById(tableId);
        List<GenTableColumn> columnsList = table.getColumns();

        // 提取主键列名
        String pkName = columnsList.stream()
            .filter(column -> "1".equals(column.getIsPk()))
            .findFirst()
            .map(GenTableColumn::getColumnName)
            .orElseThrow(() -> new RuntimeException("表" + tableName + "无主键"));

        // 获取表的字段配置信息
        Map<String, Object> onlineField = getOnlineForm(tableId);
        String formType = (String) onlineField.get("type");

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();

        // 处理单表模式
        if ("single".equals(formType)) {
            TableVo tableVo = (TableVo) onlineField.get("data");
            Map<String, Object> data = onlineMapper.selectDynamicTableById(tableName, pkName, id);
            processBlobFields(tableVo.getColumnsVo(), data);
            result.putAll(data);
        }
        // 处理主子表模式
        else if ("main-sub".equals(formType)) {
            // 获取主表配置
            TableVo mainTable = (TableVo) onlineField.get("main");
            // 获取子表配置
            TableVo subTable = (TableVo) onlineField.get("sub");

            // 查询主表数据
            Map<String, Object> mainData = onlineMapper.selectDynamicTableById(tableName, pkName, id);
            processBlobFields(mainTable.getColumnsVo(), mainData);
            removeSystemFields(mainData);  // 新增过滤逻辑
            result.put("main", mainData);

            // 查询子表数据（假设子表通过外键关联）
            String subTableName = subTable.getTableName();
            // 需要传入主表对象
            String fkName = getSubTableFk(table); // 这里table是主表的GenTable对象

            List<Map<String, Object>> subData = onlineMapper.selectSubTableByFk(
                subTableName,
                fkName,
                (Long) mainData.get(pkName)
            );
            processBlobFields(subTable.getColumnsVo(), subData);
            subData.forEach(this::removeSystemFields);  // 新增过滤逻辑
            result.put(subTableName, subData);
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

    // 处理longblob字段的通用方法
    private void processBlobFields(List<OnlFieldVo> columns, Map<String, Object> data) {
        for (OnlFieldVo field : columns) {
            if ("longblob".equals(field.getColumnType())) {
                Object value = data.get(field.getColumnName());
                if (value instanceof byte[]) {
                    data.put(field.getColumnName(), new String((byte[]) value, StandardCharsets.UTF_8));
                }
            }
        }
    }

    // 处理子表列表的blob字段
    private void processBlobFields(List<OnlFieldVo> columns, List<Map<String, Object>> dataList) {
        for (Map<String, Object> data : dataList) {
            processBlobFields(columns, data);
        }
    }

    private String getPkName(Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        List<GenTableColumn> columnsList = table.getColumns();
        String pkName="";
        //获取pk
        for (GenTableColumn genTableColumn : columnsList) {
            String isPk = (String) genTableColumn.getIsPk();
            if (StringUtils.equals(isPk, "1")) {
                pkName = genTableColumn.getColumnName();
            }
        }
        return pkName;
    }

    private String getSubTableFk(GenTable mainTable) {
        // 从主表配置中直接获取子表外键字段名
        String fkName = mainTable.getSubTableFkName();

        if (StringUtils.isBlank(fkName)) {
            throw new RuntimeException("子表外键未配置，请检查表["
                + mainTable.getTableName()
                + "]的sub_table_fk_name字段配置");
        }

        return fkName;
    }

    // 动态表分页查询
    @Override
    public TableDataInfo selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page) {
        // 校验表名合法性（防止SQL注入）
        if (!isValidTableName(tableName)) {
            throw new ServiceException("非法表名");
        }
        // table whitelist
        requireGenTableByName(tableName);

        // 使用MyBatis Plus的分页查询
        Page<Map<String, Object>> resultPage = onlineMapper.selectDynamicPage(
            page,
            tableName,
            wrapper
        );

        return TableDataInfo.build(resultPage);
    }

    // 动态表单条查询
    @Override
    public Map<String, Object> selectDynamicTableById(String tableName, String id) {
        if (!isValidTableName(tableName)) {
            throw new ServiceException("非法表名");
        }
        // table whitelist
        requireGenTableByName(tableName);

        QueryWrapper<Map<String, Object>> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id); // 假设主键字段为id

        return onlineMapper.selectDynamicOne(tableName, wrapper);
    }

    // 校验表名合法性（字母数字下划线）
    private boolean isValidTableName(String tableName) {
        return tableName.matches("^[a-zA-Z0-9_]+$");
    }

	@Override
	public TableDataInfo<Map> listTableData(String tableName, PageQuery pageQuery) {
        // table whitelist
        GenTable meta = requireGenTableByName(tableName);
        Set<String> allowedCols = buildAllowedColumns(meta);

		QueryWrapper<Map> wrapper = Wrappers.query();
		wrapper.eq("tenant_id", TenantHelper.getTenantId());
		JSONObject params = pageQuery.getParams();
		if (ObjectUtils.isNotEmpty(params)) {
			//遍历查询JSON对象
			for(String key : params.keySet()) {
				Object queryValue = params.get(key);
                if (allowedCols.contains(key) && isSafeIdentifier(key)) {
                    wrapper.eq(key,queryValue);
                }
			}
		}
		Page<Map> page = onlineMapper.getOnlineData(tableName, pageQuery.build(), wrapper);
		//List<Map> records = page.getRecords();
		return TableDataInfo.build(page);
	}

    @Override
    public Map<String, Object> getPopupDetail(Long tableId, String tableName, String id) {
        assertAllowedPopupDictTableByContext(tableId, tableName);
        return selectDynamicTableById(tableName, id);
    }

    @Override
    public TableDataInfo<Map> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery) {
        assertAllowedPopupDictTableByContext(tableId, dictTableName);
        return listTableData(dictTableName, pageQuery);
    }

    @Override
    public int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> map) {
        // 判断是否为主子表结构
        boolean isMainSub = map.containsKey("main") && map.containsKey("sub");

        // 处理主表数据
        Map<String, Object> mainData = isMainSub ? (Map<String, Object>) map.get("main") : map;
        // 获取表结构信息
        GenTable table = baseMapper.selectGenTableByName(tableName);
        if (table == null) {
            throw new ServiceException("表未纳入online配置白名单: " + tableName);
        }

        String mainPkName = getPkName(table.getTableId());

        int result = 0;

        try {
            if (isMainSub) {
                // ============= 主子表处理逻辑 =============
                Map<String, Object> subData = (Map<String, Object>) map.get("sub");
                String subTableName = (String) subData.get("tableName");
                List<Map<String, Object>> subRows = (List<Map<String, Object>>) subData.get("data");

                if (!StringUtils.equals(table.getSubTableName(), subTableName)) {
                    throw new ServiceException("子表名不匹配: " + subTableName);
                }
                GenTable subTableMeta = requireGenTableByName(subTableName);
                String subPkName = getPkName(subTableMeta.getTableId());

                // 判断主表是新增还是更新
                Object mainPkValue = mainData.get(mainPkName);
                boolean isMainUpdate = mainData.containsKey(mainPkName) && ObjectUtils.isNotEmpty(mainPkValue) && !"".equals(mainPkValue);

                // 新增时排除主键列，更新时包含主键列
                Set<String> mainAllowed = buildAllowedColumns(table, !isMainUpdate);
                Set<String> subAllowed = buildAllowedColumns(subTableMeta, true); // 子表总是新增，排除主键

                // 处理主表
                filterMapKeysInPlace(mainData, mainAllowed);
                Object mainId = processMainTable(tableName, mainData, mainPkName);
                String fkName = getSubTableFk(table); // 这里table是主表的GenTable对象
                if (!subAllowed.contains(fkName)) {
                    throw new ServiceException("子表外键字段未纳入白名单: " + fkName);
                }

                // 处理子表
                if (mainId != null && subRows != null && !subRows.isEmpty()) {
                    for (Map<String, Object> r : subRows) {
                        filterMapKeysInPlace(r, subAllowed);
                    }
                    processSubTable(subTableName, fkName, mainId, subRows, subPkName);
                }

                result = 1;
            } else {
                // ============= 单表处理逻辑 =============
                // 判断是新增还是更新
                Object pkValue = mainData.get(mainPkName);
                boolean isUpdate = mainData.containsKey(mainPkName) && ObjectUtils.isNotEmpty(pkValue) && !"".equals(pkValue);

                // 新增时排除主键列，更新时包含主键列
                Set<String> mainAllowed = buildAllowedColumns(table, !isUpdate);

                filterMapKeysInPlace(mainData, mainAllowed);
                result = processSingleTable(tableName, mainData, mainPkName);
            }

        } catch (Exception e) {
            throw new ServiceException("操作失败：" + e.getMessage());
        }
        return result;
    }
}

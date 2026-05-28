package com.nexus.online.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;

/**
 * 在线表单Service接口
 *
 * @author wanghuan
 */
public interface IFormService {

	Map<String, Object> getOnlineForm(Long tableId);

	TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery);

	TableDataInfo<Map> listOnlineData(Long tableId, String tableName, PageQuery pageQuery);

	int addOrUpdateOnlineData(Long tableId, String tableName, Map<String, Object> map);

	void deleteOnlineData(Long tableId, String tableName, List<Long> ids);

	Map<String, Object> getOnlineData(Long tableId, String tableName, Long id);

    // 动态表分页查询
    TableDataInfo selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page);

    // 动态表单条查询
	Map<String, Object> selectDynamicTableById(String tableName, String id);

    /**
     * Popup 详情（带 tableId 上下文做表名授权校验）
     */
    Map<String, Object> getPopupDetail(Long tableId, String tableName, String id);

    /**
     * Popup 列表数据（带 tableId 上下文做表名授权校验）
     */
    TableDataInfo<Map> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery);

	TableDataInfo<Map> listTableData(String tableName, PageQuery pageQuery);

    int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> map);
}

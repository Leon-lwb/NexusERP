package com.nexus.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;

import java.util.List;
import java.util.Map;

/**
 * Online表单服务接口
 * 从ruoyi-online模块迁移过来，供Flowable等模块调用
 *
 * @author nexus
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

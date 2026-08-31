package com.nexus.generator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.service.IFormService;
import com.nexus.generator.service.IUnifiedGenService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Online表单服务适配器
 * 适配原有的IFormService接口，委托给IUnifiedGenService实现
 * 供Flowable等原有模块调用
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Primary
public class FormServiceAdapter implements IFormService {

    private final IUnifiedGenService unifiedGenService;

    /**
     * 获取指定表的 Online 表单配置（委托统一服务）。
     *
     * @param tableId 业务表ID
     * @return 表单配置映射
     */
    @Override
    public Map<String, Object> getOnlineForm(Long tableId) {
        return unifiedGenService.getOnlineForm(tableId);
    }

    /**
     * 分页查询代码生成业务列表（适配旧接口，当前未实现，返回 null）。
     *
     * @param genTable  查询条件
     * @param pageQuery 分页参数
     * @return 业务表分页结果（当前为 null）
     */
    @Override
    public TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery) {
        // 委托给原有的GenTableService
        return null;
    }

    /**
     * 分页查询 Online 表单业务数据（委托统一服务并转换类型）。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param pageQuery 分页参数
     * @return 业务数据分页结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public TableDataInfo<Map> listOnlineData(Long tableId, String tableName, PageQuery pageQuery) {
        TableDataInfo<Map<String, Object>> result = unifiedGenService.listOnlineData(tableId, pageQuery);
        return (TableDataInfo<Map>) (TableDataInfo<?>) result;
    }

    /**
     * 新增或更新 Online 表单业务数据（委托统一服务）。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param map       业务数据
     * @return 影响行数
     */
    @Override
    public int addOrUpdateOnlineData(Long tableId, String tableName, Map<String, Object> map) {
        return unifiedGenService.addOrUpdateOnlineData(tableId, map);
    }

    /**
     * 批量删除 Online 表单业务数据（委托统一服务）。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param ids       数据ID列表
     */
    @Override
    public void deleteOnlineData(Long tableId, String tableName, List<Long> ids) {
        unifiedGenService.deleteOnlineData(tableId, ids);
    }

    /**
     * 根据主键查询 Online 表单单条业务数据（委托统一服务）。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param id        数据ID
     * @return 数据详情映射
     */
    @Override
    public Map<String, Object> getOnlineData(Long tableId, String tableName, Long id) {
        return unifiedGenService.getOnlineDataById(tableId, id);
    }

    /**
     * 动态表分页查询（委托统一服务）。
     *
     * @param tableName 表名
     * @param wrapper   查询条件封装
     * @param page      分页对象
     * @return 分页结果
     */
    @Override
    public TableDataInfo selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page) {
        return unifiedGenService.selectDynamicTablePage(tableName, wrapper, page);
    }

    /**
     * 根据主键查询动态表单条数据（委托统一服务）。
     *
     * @param tableName 表名
     * @param id        数据ID
     * @return 数据映射
     */
    @Override
    public Map<String, Object> selectDynamicTableById(String tableName, String id) {
        return unifiedGenService.selectDynamicTableById(tableName, id);
    }

    /**
     * 查询 Popup 弹窗来源表单条数据（委托统一服务）。
     *
     * @param tableId   业务表ID（权限上下文）
     * @param tableName Popup 来源表名
     * @param id        数据ID
     * @return 数据详情映射
     */
    @Override
    public Map<String, Object> getPopupDetail(Long tableId, String tableName, String id) {
        return unifiedGenService.getPopupDetail(tableId, tableName, id);
    }

    /**
     * 分页查询 Popup 弹窗来源表数据（委托统一服务并转换类型）。
     *
     * @param tableId       业务表ID（权限上下文）
     * @param dictTableName Popup 来源表名
     * @param pageQuery     分页参数
     * @return 来源表数据分页结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public TableDataInfo<Map> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery) {
        TableDataInfo<Map<String, Object>> result = unifiedGenService.listPopupTableData(tableId, dictTableName, pageQuery);
        return (TableDataInfo<Map>) (TableDataInfo<?>) result;
    }

    /**
     * 按表名分页查询动态表数据（适配旧接口，当前未实现，返回 null）。
     *
     * @param tableName 表名
     * @param pageQuery 分页参数
     * @return 动态表分页结果（当前为 null）
     */
    @Override
    public TableDataInfo<Map> listTableData(String tableName, PageQuery pageQuery) {
        // 通过表名查询tableId，然后调用listOnlineData
        // 这里简化处理
        return null;
    }

    /**
     * 通过表名新增或更新 Online 表单数据（委托统一服务，供 Flowable 调用）。
     *
     * @param tableName 表名
     * @param map       业务数据
     * @return 影响行数
     */
    @Override
    public int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> map) {
        return unifiedGenService.addOrUpdateOnlineDataByName(tableName, map);
    }
}

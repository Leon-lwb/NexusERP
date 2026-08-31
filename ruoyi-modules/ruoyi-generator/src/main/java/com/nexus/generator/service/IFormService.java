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

    /**
     * 获取指定表对应的 Online 表单配置（含字段与布局）。
     *
     * @param tableId 业务表ID
     * @return 表单配置映射
     */
    Map<String, Object> getOnlineForm(Long tableId);

    /**
     * 分页查询代码生成业务列表（兼容原表单服务接口）。
     *
     * @param genTable  查询条件
     * @param pageQuery 分页参数
     * @return 业务表分页结果
     */
    TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery);

    /**
     * 分页查询 Online 表单业务数据。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数，当前以 tableId 为准）
     * @param pageQuery 分页参数
     * @return 业务数据分页结果
     */
    TableDataInfo<Map> listOnlineData(Long tableId, String tableName, PageQuery pageQuery);

    /**
     * 新增或更新 Online 表单业务数据。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param map       业务数据
     * @return 影响行数
     */
    int addOrUpdateOnlineData(Long tableId, String tableName, Map<String, Object> map);

    /**
     * 批量删除 Online 表单业务数据。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param ids       数据ID列表
     */
    void deleteOnlineData(Long tableId, String tableName, List<Long> ids);

    /**
     * 根据主键查询 Online 表单单条业务数据。
     *
     * @param tableId   业务表ID
     * @param tableName 表名（兼容参数）
     * @param id        数据ID
     * @return 数据详情映射
     */
    Map<String, Object> getOnlineData(Long tableId, String tableName, Long id);

    // 动态表分页查询
    /**
     * 动态表分页查询（按表名与自定义查询条件）。
     *
     * @param tableName 表名
     * @param wrapper   查询条件封装
     * @param page      分页对象
     * @return 分页结果
     */
    TableDataInfo selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page);

    // 动态表单条查询
    /**
     * 根据主键查询动态表单条数据。
     *
     * @param tableName 表名
     * @param id        数据ID
     * @return 数据映射
     */
    Map<String, Object> selectDynamicTableById(String tableName, String id);

    /**
     * 查询 Popup 弹窗来源表单条数据（带表名授权校验）。
     *
     * @param tableId   业务表ID（用于权限上下文）
     * @param tableName Popup 来源表名
     * @param id        数据ID
     * @return 数据详情映射
     */
    Map<String, Object> getPopupDetail(Long tableId, String tableName, String id);

    /**
     * 分页查询 Popup 弹窗来源表数据（带表名授权校验）。
     *
     * @param tableId       业务表ID（用于权限上下文）
     * @param dictTableName Popup 来源表名
     * @param pageQuery     分页参数
     * @return 来源表数据分页结果
     */
    TableDataInfo<Map> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery);

    /**
     * 按表名分页查询动态表数据（通用入口）。
     *
     * @param tableName 表名
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<Map> listTableData(String tableName, PageQuery pageQuery);

    /**
     * 通过表名新增或更新 Online 表单数据，供 Flowable 调用。
     *
     * @param tableName 表名
     * @param map       业务数据
     * @return 影响行数
     */
    int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> map);
}

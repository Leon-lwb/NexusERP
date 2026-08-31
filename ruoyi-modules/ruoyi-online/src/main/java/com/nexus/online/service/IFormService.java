package com.nexus.online.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;

/**
 * 在线表单 Service 接口
 * <p>定义在线表单的元数据查询、动态表数据增删改查、字典/弹窗来源表数据查询等契约，
 * 属于在线低代码表单构建器模块（ruoyi-online）。</p>
 *
 * @author wanghuan
 */
public interface IFormService {

	/**
	 * 获取指定在线表单的设计元数据（含字段与子表配置）。
	 *
	 * @param tableId 在线表单对应的表 ID
	 * @return 表单设计结构，区分 single / main-sub / sub 三种类型
	 */
	Map<String, Object> getOnlineForm(Long tableId);

	/**
	 * 分页查询在线表单（代码生成表）列表。
	 *
	 * @param genTable 查询条件，可包含表名、表注释、数据源名及时间范围
	 * @param pageQuery 分页参数
	 * @return 分页结果，包含符合条件的在线表单表配置列表
	 */
	TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery);

	/**
	 * 分页查询在线表单表数据（基于 tableId 做授权与字段白名单校验）。
	 *
	 * @param tableId 在线表单对应的表 ID
	 * @param tableName 实际数据表名
	 * @param pageQuery 分页参数
	 * @return 分页结果，含表数据与表单字段元信息
	 */
	TableDataInfo<Map> listOnlineData(Long tableId, String tableName, PageQuery pageQuery);

	/**
	 * 新增或更新在线表单表数据（支持单表与主子表结构）。
	 *
	 * @param tableId 在线表单对应的表 ID
	 * @param tableName 实际数据表名
	 * @param map 数据内容；主子表结构时含 main / sub 两部分
	 * @return 受影响记录数
	 */
	int addOrUpdateOnlineData(Long tableId, String tableName, Map<String, Object> map);

	/**
	 * 批量删除在线表单表数据（含关联子表数据）。
	 *
	 * @param tableId 在线表单对应的表 ID
	 * @param tableName 实际数据表名
	 * @param ids 待删除记录的主键 ID 列表
	 */
	void deleteOnlineData(Long tableId, String tableName, List<Long> ids);

	/**
	 * 查询在线表单指定记录的详细数据。
	 *
	 * @param tableId 在线表单对应的表 ID
	 * @param tableName 实际数据表名
	 * @param id 待查询记录的主键 ID
	 * @return 记录详情，主子表结构时含主表与子表数据
	 */
	Map<String, Object> getOnlineData(Long tableId, String tableName, Long id);

	/**
	 * 动态表分页查询（按表名，无 tableId 上下文，需表名合法且已纳入白名单）。
	 *
	 * @param tableName 数据表名
	 * @param wrapper 动态查询条件
	 * @param page 分页对象
	 * @return 分页结果
	 */
    TableDataInfo selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page);

	/**
	 * 动态表单单条查询（按表名与 ID，需表名合法且已纳入白名单）。
	 *
	 * @param tableName 数据表名
	 * @param id 记录 ID
	 * @return 记录数据映射
	 */
	Map<String, Object> selectDynamicTableById(String tableName, String id);

    /**
     * Popup 详情（带 tableId 上下文做表名授权校验）
     */
    Map<String, Object> getPopupDetail(Long tableId, String tableName, String id);

    /**
     * Popup 列表数据（带 tableId 上下文做表名授权校验）
     */
    TableDataInfo<Map> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery);

	/**
	 * 按表名分页查询字典/关联表数据（需已纳入 online 配置白名单）。
	 *
	 * @param tableName 目标字典或关联表名
	 * @param pageQuery 分页参数
	 * @return 分页结果
	 */
	TableDataInfo<Map> listTableData(String tableName, PageQuery pageQuery);

	/**
	 * 按表名新增或更新在线表单表数据（无需 tableId 上下文）。
	 *
	 * @param tableName 实际数据表名（需已纳入 online 配置白名单）
	 * @param map 数据内容；主子表结构时含 main / sub 两部分
	 * @return 受影响记录数
	 */
    int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> map);
}

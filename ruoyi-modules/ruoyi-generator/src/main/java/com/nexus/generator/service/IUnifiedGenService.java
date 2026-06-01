package com.nexus.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTableDesign;

import java.util.List;
import java.util.Map;

/**
 * 统一代码生成与Online设计服务接口
 * 整合代码生成和Online表单设计功能
 *
 * @author nexus
 */
public interface IUnifiedGenService {

    // ==================== 设计管理 ====================

    /**
     * 保存或更新表设计
     *
     * @param design 设计信息
     * @return 设计ID
     */
    Long saveOrUpdateDesign(GenTableDesign design);

    /**
     * 获取设计详情
     *
     * @param designId 设计ID
     * @return 设计信息
     */
    GenTableDesign getDesignById(Long designId);

    /**
     * 删除设计
     *
     * @param designId 设计ID
     */
    void removeDesignById(Long designId);

    /**
     * 查询设计列表
     *
     * @param keyword 关键字
     * @return 设计列表
     */
    List<GenTableDesign> listDesigns(String keyword);

    /**
     * 生成建表SQL
     *
     * @param designJson 设计JSON
     * @return SQL语句
     */
    String generateCreateTableSql(String designJson);

    /**
     * 执行建表SQL
     *
     * @param sql SQL语句
     * @param overwriteIfExists 是否覆盖已存在的表
     */
    void executeCreateTableSql(String sql, boolean overwriteIfExists);

    // ==================== 设计导入代码生成 ====================

    /**
     * 从设计导入到代码生成
     *
     * @param designId 设计ID
     * @param dataName 数据源名称
     * @param overwriteIfExists 是否覆盖已存在的配置
     * @return 生成的表ID
     */
    Long importFromDesign(Long designId, String dataName, boolean overwriteIfExists);

    /**
     * 同步设计到代码生成配置
     *
     * @param tableId 代码生成表ID
     */
    void syncDesignToGenTable(Long tableId);

    // ==================== Online表单数据操作（供Flowable调用） ====================

    /**
     * 获取Online表单配置
     *
     * @param tableId 表ID
     * @return 表单配置
     */
    Map<String, Object> getOnlineForm(Long tableId);

    /**
     * 分页查询Online表单数据
     *
     * @param tableId 表ID
     * @param pageQuery 分页参数
     * @return 数据列表
     */
    TableDataInfo<Map<String, Object>> listOnlineData(Long tableId, PageQuery pageQuery);

    /**
     * 根据ID获取Online表单数据
     *
     * @param tableId 表ID
     * @param dataId 数据ID
     * @return 数据
     */
    Map<String, Object> getOnlineDataById(Long tableId, Long dataId);

    /**
     * 新增或修改Online表单数据
     *
     * @param tableId 表ID
     * @param data 数据
     * @return 影响行数
     */
    int addOrUpdateOnlineData(Long tableId, Map<String, Object> data);

    /**
     * 根据表名新增或修改Online表单数据（供Flowable调用）
     *
     * @param tableName 表名
     * @param data 数据
     * @return 影响行数
     */
    int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> data);

    /**
     * 删除Online表单数据
     *
     * @param tableId 表ID
     * @param ids 数据ID列表
     */
    void deleteOnlineData(Long tableId, List<Long> ids);

    // ==================== 动态表查询（Popup等场景） ====================

    /**
     * 动态表分页查询
     *
     * @param tableName 表名
     * @param wrapper 查询条件
     * @param page 分页对象
     * @return 分页结果
     */
    TableDataInfo<Map<String, Object>> selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page);

    /**
     * 根据ID查询动态表数据
     *
     * @param tableName 表名
     * @param id 数据ID
     * @return 数据
     */
    Map<String, Object> selectDynamicTableById(String tableName, String id);

    /**
     * 获取Popup详情（带tableId上下文做表名授权校验）
     *
     * @param tableId 表ID
     * @param dictTableName 字典表名
     * @param id 数据ID
     * @return 数据
     */
    Map<String, Object> getPopupDetail(Long tableId, String dictTableName, String id);

    /**
     * 获取Popup列表数据（带tableId上下文做表名授权校验）
     *
     * @param tableId 表ID
     * @param dictTableName 字典表名
     * @param pageQuery 分页参数
     * @return 数据列表
     */
    TableDataInfo<Map<String, Object>> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery);

    // ==================== 代码生成 ====================

    /**
     * 预览代码
     *
     * @param tableId 表ID
     * @return 代码预览
     */
    Map<String, String> previewCode(Long tableId);

    /**
     * 下载代码
     *
     * @param tableId 表ID
     * @return 代码字节数组
     */
    byte[] downloadCode(Long tableId);

    /**
     * 批量下载代码
     *
     * @param tableIds 表ID数组
     * @return 代码字节数组
     */
    byte[] downloadCodeBatch(Long[] tableIds);

    /**
     * 生成代码到自定义路径
     *
     * @param tableId 表ID
     */
    void generatorCode(Long tableId);

    /**
     * 同步数据库结构到代码生成配置
     *
     * @param tableId 表ID
     */
    void synchDb(Long tableId);
}

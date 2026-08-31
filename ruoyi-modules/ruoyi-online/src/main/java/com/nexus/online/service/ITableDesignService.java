package com.nexus.online.service;

import com.nexus.generator.domain.GenTableDesign;

import java.util.List;

/**
 * 在线表单设计 Service 接口
 * <p>定义表设计的保存、查询、删除、SQL 生成、物理建表与导入代码生成等契约，
 * 属于在线低代码表单构建器模块（ruoyi-online）。</p>
 *
 * @author wanghuan
 */
public interface ITableDesignService {
    /**
     * 保存或更新表设计。
     *
     * @param design 表设计实体，含设计 ID（更新时）、名称、表名、表注释与内容
     * @return 保存后的设计 ID
     */
    Long saveOrUpdate(GenTableDesign design);
    /**
     * 按关键字查询表设计列表。
     *
     * @param keyword 可选查询关键字，匹配设计名、表名或表注释
     * @return 表设计列表，按更新时间倒序
     */
    List<GenTableDesign> list(String keyword);
    /**
     * 查询指定表设计的详情。
     *
     * @param id 表设计 ID
     * @return 表设计实体
     */
    GenTableDesign getById(Long id);
    /**
     * 删除指定表设计。
     *
     * @param id 待删除的表设计 ID
     */
    void removeById(Long id);

    /**
     * 根据设计 JSON 生成建表及外键 SQL（不执行）。
     *
     * @param designJson 表设计 JSON（含 tables 与 relations）
     * @return 生成的完整 SQL 字符串
     */
    String generateSql(String designJson);
    /**
     * 执行建表 SQL，在数据库中创建物理表。
     *
     * @param sql 完整建表 SQL（可含多条语句）
     * @param overwriteIfExists 表已存在时是否先删除后重建
     */
    void createTables(String sql, boolean overwriteIfExists);

    /**
     * 将表设计导入代码生成器（生成 GenTable / GenTableColumn 配置）。
     *
     * @param designJson 表设计 JSON
     * @param dataName 目标数据源名称，默认 master
     * @param overwriteIfExists 同名表已存在时是否覆盖导入
     */
    void importToGen(String designJson, String dataName, boolean overwriteIfExists);
}

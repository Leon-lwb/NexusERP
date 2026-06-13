package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiSqlExampleBo;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI SQL Few-Shot 示例 Service 接口
 *
 * @author NexusERP
 */
public interface IAiSqlExampleService {

    /**
     * 分页查询 SQL 示例
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiSqlExampleVo> selectPageList(AiSqlExampleBo bo, PageQuery pageQuery);

    /**
     * 查询 SQL 示例列表（不分页）
     *
     * @param bo 查询条件
     * @return 示例列表
     */
    List<AiSqlExampleVo> selectList(AiSqlExampleBo bo);

    /**
     * 根据 ID 查询 SQL 示例
     *
     * @param id 主键
     * @return 示例视图对象
     */
    AiSqlExampleVo selectById(Long id);

    /**
     * 新增 SQL 示例
     *
     * @param bo 示例业务对象
     * @return 影响行数
     */
    int insertByBo(AiSqlExampleBo bo);

    /**
     * 修改 SQL 示例
     *
     * @param bo 示例业务对象
     * @return 影响行数
     */
    int updateByBo(AiSqlExampleBo bo);

    /**
     * 批量删除 SQL 示例
     *
     * @param ids 主键数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}

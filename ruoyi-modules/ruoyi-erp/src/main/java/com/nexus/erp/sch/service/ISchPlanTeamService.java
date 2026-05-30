package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchPlanTeamBo;
import com.nexus.erp.sch.domain.vo.SchPlanTeamVo;

import java.util.List;

/**
 * 计划班组关联Service接口
 *
 * @author nexus
 * @date 2025-12-26
 */
public interface ISchPlanTeamService {

    /**
     * 分页查询计划班组关联列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 计划班组关联分页列表
     */
    TableDataInfo<SchPlanTeamVo> selectPageList(SchPlanTeamBo bo, PageQuery pageQuery);

    /**
     * 查询计划班组关联列表
     *
     * @param bo 查询参数
     * @return 计划班组关联列表
     */
    List<SchPlanTeamVo> selectList(SchPlanTeamBo bo);

    /**
     * 根据ID获取计划班组关联详情
     *
     * @param id 关联记录ID
     * @return 计划班组关联详情
     */
    SchPlanTeamVo selectById(Long id);

    /**
     * 新增计划班组关联
     *
     * @param bo 计划班组关联业务对象
     * @return 影响行数
     */
    int insertByBo(SchPlanTeamBo bo);

    /**
     * 修改计划班组关联
     *
     * @param bo 计划班组关联业务对象
     * @return 影响行数
     */
    int updateByBo(SchPlanTeamBo bo);

    /**
     * 批量删除计划班组关联
     *
     * @param ids 关联记录ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}

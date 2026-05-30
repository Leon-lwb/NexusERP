package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchTeamBo;
import com.nexus.erp.sch.domain.vo.SchTeamVo;

import java.util.List;

/**
 * 班组设置Service接口
 *
 * @author nexus
 * @date 2025-12-23
 */
public interface ISchTeamService {

    /**
     * 分页查询班组设置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班组设置分页列表
     */
    TableDataInfo<SchTeamVo> selectPageList(SchTeamBo bo, PageQuery pageQuery);

    /**
     * 查询班组设置列表
     *
     * @param bo 查询参数
     * @return 班组设置列表
     */
    List<SchTeamVo> selectList(SchTeamBo bo);

    /**
     * 根据ID获取班组设置详情
     *
     * @param id 班组ID
     * @return 班组设置详情
     */
    SchTeamVo selectById(Long id);

    /**
     * 新增班组设置
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    int insertByBo(SchTeamBo bo);

    /**
     * 修改班组设置
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    int updateByBo(SchTeamBo bo);

    /**
     * 批量删除班组设置
     *
     * @param ids 班组ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}

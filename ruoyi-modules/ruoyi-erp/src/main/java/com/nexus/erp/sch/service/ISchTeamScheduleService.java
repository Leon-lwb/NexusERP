package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchTeamScheduleBo;
import com.nexus.erp.sch.domain.vo.SchTeamScheduleVo;

import java.util.List;

/**
 * 班组排班（模板）Service接口
 *
 * @author nexus
 * @date 2025-12-30
 */
public interface ISchTeamScheduleService {

    /**
     * 分页查询班组排班模板列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班组排班模板分页列表
     */
    TableDataInfo<SchTeamScheduleVo> selectPageList(SchTeamScheduleBo bo, PageQuery pageQuery);

    /**
     * 查询班组排班模板列表
     *
     * @param bo 查询参数
     * @return 班组排班模板列表
     */
    List<SchTeamScheduleVo> selectList(SchTeamScheduleBo bo);

    /**
     * 根据ID获取班组排班模板详情
     *
     * @param id 排班模板ID
     * @return 班组排班模板详情
     */
    SchTeamScheduleVo selectById(Long id);

    /**
     * 新增班组排班模板
     *
     * @param bo 班组排班模板业务对象
     * @return 影响行数
     */
    int insertByBo(SchTeamScheduleBo bo);

    /**
     * 修改班组排班模板
     *
     * @param bo 班组排班模板业务对象
     * @return 影响行数
     */
    int updateByBo(SchTeamScheduleBo bo);

    /**
     * 批量删除班组排班模板
     *
     * @param ids 排班模板ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}

package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchPlanBo;
import com.nexus.erp.sch.domain.vo.SchPlanVo;

import java.util.List;

/**
 * 排班计划Service接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface ISchPlanService {

    /**
     * 分页查询排班计划列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 排班计划分页列表
     */
    TableDataInfo<SchPlanVo> selectPageList(SchPlanBo bo, PageQuery pageQuery);

    /**
     * 查询排班计划列表
     *
     * @param bo 查询参数
     * @return 排班计划列表
     */
    List<SchPlanVo> selectList(SchPlanBo bo);

    /**
     * 根据ID获取排班计划详情
     *
     * @param id 排班计划ID
     * @return 排班计划详情
     */
    SchPlanVo selectById(Long id);

    /**
     * 新增排班计划
     *
     * @param bo 排班计划业务对象
     * @return 影响行数
     */
    int insertByBo(SchPlanBo bo);

    /**
     * 修改排班计划
     *
     * @param bo 排班计划业务对象
     * @return 影响行数
     */
    int updateByBo(SchPlanBo bo);

    /**
     * 批量删除排班计划
     *
     * @param ids 排班计划ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}

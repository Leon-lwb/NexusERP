package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.domain.vo.PrdScheduleTaskVo;
import com.nexus.erp.prd.domain.vo.ScheduleGanttVo;

import java.util.List;

/**
 * APS 智能排产 Service 接口
 *
 * @author nexus
 */
public interface IPrdScheduleService {

    /**
     * 分页查询排程方案
     *
     * @param bo        查询条件(编号/名称/策略/状态)
     * @param pageQuery 分页参数
     * @return 排程方案分页列表
     */
    TableDataInfo<PrdSchedulePlanVo> selectPageList(PrdSchedulePlanBo bo, PageQuery pageQuery);

    /**
     * 查询方案下全部工序任务
     *
     * @param planId 方案ID
     * @return 任务列表(按工单+工序序号排序)
     */
    List<PrdScheduleTaskVo> selectTasksByPlanId(Long planId);

    /**
     * 查询方案甘特图聚合数据(工单行 + 工序条 + 资源利用率)
     *
     * @param planId 方案ID
     * @return 甘特图数据
     */
    ScheduleGanttVo selectGantt(Long planId);

    /**
     * 执行排程：取未完工工单 → 展开工艺路线 → 产能日历约束 → 引擎排产 → 落库
     *
     * @param bo 排程参数(起始日/跨度/策略/车间过滤)
     * @return 生成的方案
     */
    PrdSchedulePlanVo runSchedule(PrdSchedulePlanBo bo);

    /**
     * 发布方案：将末序完工日期回写工单计划开始/结束日期，方案置 PUBLISHED
     *
     * @param planId 方案ID
     * @return 是否成功
     */
    Boolean publish(Long planId);

    /**
     * 批量删除方案(级联删除任务)
     *
     * @param planIds 方案ID数组
     * @return 是否成功
     */
    Boolean deleteByIds(Long[] planIds);
}

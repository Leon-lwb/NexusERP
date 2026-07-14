package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqPatrolRecordBo;
import com.nexus.mes.eq.domain.bo.EqPatrolRouteBo;
import com.nexus.mes.eq.domain.bo.EqPatrolTaskBo;
import com.nexus.mes.eq.domain.vo.EqPatrolRecordVo;
import com.nexus.mes.eq.domain.vo.EqPatrolRouteVo;
import com.nexus.mes.eq.domain.vo.EqPatrolTaskVo;

import java.util.List;

/**
 * MES巡检管理 Service 业务层接口(路线+任务+记录)
 *
 * @author Nexus
 */
public interface IEqPatrolService {

    // ===== 路线 =====
    TableDataInfo<EqPatrolRouteVo> selectRoutePage(EqPatrolRouteBo bo, PageQuery pageQuery);
    EqPatrolRouteVo selectRouteById(Long routeId);
    List<EqPatrolRouteVo> selectRouteList(EqPatrolRouteBo bo);
    EqPatrolRouteVo insertRouteByBo(EqPatrolRouteBo bo);
    Boolean updateRouteByBo(EqPatrolRouteBo bo);
    Boolean deleteRouteByIds(Long[] ids);

    // ===== 任务 =====
    TableDataInfo<EqPatrolTaskVo> selectTaskPage(EqPatrolTaskBo bo, PageQuery pageQuery);
    EqPatrolTaskVo selectTaskById(Long taskId);
    EqPatrolTaskVo insertTaskByBo(EqPatrolTaskBo bo);
    Boolean updateTaskByBo(EqPatrolTaskBo bo);
    Boolean deleteTaskByIds(Long[] ids);
    Boolean completeTask(Long taskId);

    // ===== 记录 =====
    TableDataInfo<EqPatrolRecordVo> selectRecordPage(EqPatrolRecordBo bo, PageQuery pageQuery);
    EqPatrolRecordVo selectRecordById(Long recordId);
    List<EqPatrolRecordVo> selectRecordByTask(Long taskId);
    EqPatrolRecordVo insertRecordByBo(EqPatrolRecordBo bo);
    Boolean deleteRecordByIds(Long[] ids);
    /** 批量提交巡检记录(执行巡检) */
    List<EqPatrolRecordVo> batchSubmitRecords(List<EqPatrolRecordBo> bos);
}

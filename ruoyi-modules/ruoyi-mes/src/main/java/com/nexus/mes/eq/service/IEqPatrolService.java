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
 * <p>属于MES 设备管理模块，Eq巡检业务层接口，定义业务契约。</p>
 *
 * @author Nexus
 */
public interface IEqPatrolService {

    // ===== 路线 =====
    /**
     * 分页查询路线列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqPatrolRouteVo> selectRoutePage(EqPatrolRouteBo bo, PageQuery pageQuery);
    /**
     * 获取路线详情
     *
     * @param routeId 巡检路线ID
     * @return 返回结果
     */
    EqPatrolRouteVo selectRouteById(Long routeId);
    /**
     * 查询路线列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqPatrolRouteVo> selectRouteList(EqPatrolRouteBo bo);
    /**
     * 新增路线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqPatrolRouteVo insertRouteByBo(EqPatrolRouteBo bo);
    /**
     * 更新路线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    Boolean updateRouteByBo(EqPatrolRouteBo bo);
    /**
     * 获取路线详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteRouteByIds(Long[] ids);

    // ===== 任务 =====
    /**
     * 分页查询任务列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqPatrolTaskVo> selectTaskPage(EqPatrolTaskBo bo, PageQuery pageQuery);
    /**
     * 获取任务详情
     *
     * @param taskId 巡检任务ID
     * @return 返回结果
     */
    EqPatrolTaskVo selectTaskById(Long taskId);
    /**
     * 新增任务
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqPatrolTaskVo insertTaskByBo(EqPatrolTaskBo bo);
    /**
     * 更新任务
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    Boolean updateTaskByBo(EqPatrolTaskBo bo);
    /**
     * 获取任务详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteTaskByIds(Long[] ids);
    /**
     * 完成任务
     *
     * @param taskId 巡检任务ID
     * @return 是否成功
     */
    Boolean completeTask(Long taskId);

    // ===== 记录 =====
    /**
     * 分页查询记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqPatrolRecordVo> selectRecordPage(EqPatrolRecordBo bo, PageQuery pageQuery);
    /**
     * 获取记录详情
     *
     * @param recordId 数据记录ID
     * @return 返回结果
     */
    EqPatrolRecordVo selectRecordById(Long recordId);
    /**
     * 按任务查询记录
     *
     * @param taskId 巡检任务ID
     * @return 结果列表
     */
    List<EqPatrolRecordVo> selectRecordByTask(Long taskId);
    /**
     * 新增记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqPatrolRecordVo insertRecordByBo(EqPatrolRecordBo bo);
    /**
     * 获取记录详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteRecordByIds(Long[] ids);
    /**
     * 批量提交巡检记录(执行巡检)
     *
     * @param bos 参数 bos
     * @return 结果列表
     */
    List<EqPatrolRecordVo> batchSubmitRecords(List<EqPatrolRecordBo> bos);
}

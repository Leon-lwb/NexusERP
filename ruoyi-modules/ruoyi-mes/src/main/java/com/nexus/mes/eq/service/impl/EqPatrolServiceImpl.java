package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqPatrolRecord;
import com.nexus.mes.eq.domain.EqPatrolRoute;
import com.nexus.mes.eq.domain.EqPatrolTask;
import com.nexus.mes.eq.domain.bo.EqPatrolRecordBo;
import com.nexus.mes.eq.domain.bo.EqPatrolRouteBo;
import com.nexus.mes.eq.domain.bo.EqPatrolTaskBo;
import com.nexus.mes.eq.domain.vo.EqPatrolRecordVo;
import com.nexus.mes.eq.domain.vo.EqPatrolRouteVo;
import com.nexus.mes.eq.domain.vo.EqPatrolTaskVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqPatrolRecordMapper;
import com.nexus.mes.eq.mapper.EqPatrolRouteMapper;
import com.nexus.mes.eq.mapper.EqPatrolTaskMapper;
import com.nexus.mes.eq.service.IEqPatrolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 属于MES 设备管理模块，巡检业务层实现。
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqPatrolService")
public class EqPatrolServiceImpl implements IEqPatrolService {

    private final EqPatrolRouteMapper routeMapper;
    private final EqPatrolTaskMapper taskMapper;
    private final EqPatrolRecordMapper recordMapper;
    private final EqEquipmentMapper equipmentMapper;

    // ===== 路线 =====

    /**
     * 分页查询路线列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqPatrolRouteVo> selectRoutePage(EqPatrolRouteBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqPatrolRoute> lqw = Wrappers.<EqPatrolRoute>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getRouteName()), EqPatrolRoute::getRouteName, bo.getRouteName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqPatrolRoute::getStatus, bo.getStatus())
            .orderByDesc(EqPatrolRoute::getCreateTime);
        Page<EqPatrolRouteVo> page = routeMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取路线详情
     *
     * @param routeId 巡检路线ID
     * @return 返回结果
     */
    @Override
    public EqPatrolRouteVo selectRouteById(Long routeId) {
        return routeMapper.selectVoById(routeId);
    }

    /**
     * 查询路线列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    @Override
    public List<EqPatrolRouteVo> selectRouteList(EqPatrolRouteBo bo) {
        return routeMapper.selectVoList(Wrappers.<EqPatrolRoute>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqPatrolRoute::getStatus, bo.getStatus())
            .orderByDesc(EqPatrolRoute::getRouteName));
    }

    /**
     * 新增路线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqPatrolRouteVo insertRouteByBo(EqPatrolRouteBo bo) {
        EqPatrolRoute route = new EqPatrolRoute();
        route.setRouteName(bo.getRouteName());
        route.setDescription(bo.getDescription());
        route.setEquipmentIds(bo.getEquipmentIds());
        route.setCheckItems(bo.getCheckItems());
        route.setFrequency(StringUtils.isNotBlank(bo.getFrequency()) ? bo.getFrequency() : "DAILY");
        route.setStatus(StringUtils.isNotBlank(bo.getStatus()) ? bo.getStatus() : "ACTIVE");
        route.setRemark(bo.getRemark());
        routeMapper.insert(route);
        return routeMapper.selectVoById(route.getRouteId());
    }

    /**
     * 更新路线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateRouteByBo(EqPatrolRouteBo bo) {
        EqPatrolRoute route = new EqPatrolRoute();
        route.setRouteId(bo.getRouteId());
        route.setRouteName(bo.getRouteName());
        route.setDescription(bo.getDescription());
        route.setEquipmentIds(bo.getEquipmentIds());
        route.setCheckItems(bo.getCheckItems());
        route.setFrequency(bo.getFrequency());
        route.setStatus(bo.getStatus());
        route.setRemark(bo.getRemark());
        return routeMapper.updateById(route) > 0;
    }

    /**
     * 获取路线详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteRouteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) return false;
        // 校验无关联任务
        for (Long routeId : ids) {
            Long taskCount = taskMapper.selectCount(Wrappers.<EqPatrolTask>lambdaQuery().eq(EqPatrolTask::getRouteId, routeId));
            if (taskCount != null && taskCount > 0) {
                throw new com.nexus.common.core.exception.ServiceException("路线[" + routeId + "]下有" + taskCount + "个巡检任务, 请先删除任务");
            }
        }
        return routeMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    // ===== 任务 =====

    /**
     * 分页查询任务列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqPatrolTaskVo> selectTaskPage(EqPatrolTaskBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqPatrolTask> lqw = Wrappers.<EqPatrolTask>lambdaQuery()
            .eq(bo.getRouteId() != null, EqPatrolTask::getRouteId, bo.getRouteId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqPatrolTask::getStatus, bo.getStatus())
            .orderByDesc(EqPatrolTask::getPlanDate);
        Page<EqPatrolTaskVo> page = taskMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取任务详情
     *
     * @param taskId 巡检任务ID
     * @return 返回结果
     */
    @Override
    public EqPatrolTaskVo selectTaskById(Long taskId) {
        return taskMapper.selectVoById(taskId);
    }

    /**
     * 新增任务
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqPatrolTaskVo insertTaskByBo(EqPatrolTaskBo bo) {
        EqPatrolTask task = new EqPatrolTask();
        task.setRouteId(bo.getRouteId());
        // 冗余路线名
        EqPatrolRouteVo route = routeMapper.selectVoById(bo.getRouteId());
        task.setRouteName(route != null ? route.getRouteName() : null);
        task.setPlanDate(bo.getPlanDate());
        task.setAssignedToId(bo.getAssignedToId());
        task.setAssignedToName(bo.getAssignedToName());
        task.setStatus(StringUtils.isNotBlank(bo.getStatus()) ? bo.getStatus() : "PENDING");
        task.setRemark(bo.getRemark());
        taskMapper.insert(task);
        return taskMapper.selectVoById(task.getTaskId());
    }

    /**
     * 更新任务
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateTaskByBo(EqPatrolTaskBo bo) {
        EqPatrolTask task = new EqPatrolTask();
        task.setTaskId(bo.getTaskId());
        task.setRouteId(bo.getRouteId());
        task.setPlanDate(bo.getPlanDate());
        task.setAssignedToId(bo.getAssignedToId());
        task.setAssignedToName(bo.getAssignedToName());
        task.setStatus(bo.getStatus());
        task.setRemark(bo.getRemark());
        return taskMapper.updateById(task) > 0;
    }

    /**
     * 获取任务详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteTaskByIds(Long[] ids) {
        if (ids == null || ids.length == 0) return false;
        // 校验无关联巡检记录
        for (Long taskId : ids) {
            Long recCount = recordMapper.selectCount(Wrappers.<EqPatrolRecord>lambdaQuery().eq(EqPatrolRecord::getTaskId, taskId));
            if (recCount != null && recCount > 0) {
                throw new com.nexus.common.core.exception.ServiceException("任务[" + taskId + "]下有" + recCount + "条巡检记录, 请先删除记录");
            }
        }
        return taskMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 完成任务
     *
     * @param taskId 巡检任务ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeTask(Long taskId) {
        int rows = taskMapper.update(null, Wrappers.<EqPatrolTask>lambdaUpdate()
            .set(EqPatrolTask::getStatus, "COMPLETED")
            .set(EqPatrolTask::getActualDate, new Date())
            .eq(EqPatrolTask::getTaskId, taskId)
            .ne(EqPatrolTask::getStatus, "COMPLETED"));
        log.info("巡检任务完成: taskId={}, rows={}", taskId, rows);
        return rows > 0;
    }

    // ===== 记录 =====

    /**
     * 分页查询记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqPatrolRecordVo> selectRecordPage(EqPatrolRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqPatrolRecord> lqw = Wrappers.<EqPatrolRecord>lambdaQuery()
            .eq(bo.getTaskId() != null, EqPatrolRecord::getTaskId, bo.getTaskId())
            .eq(bo.getEquipmentId() != null, EqPatrolRecord::getEquipmentId, bo.getEquipmentId())
            .eq(StringUtils.isNotBlank(bo.getResult()), EqPatrolRecord::getResult, bo.getResult())
            .orderByDesc(EqPatrolRecord::getExecuteTime);
        Page<EqPatrolRecordVo> page = recordMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取记录详情
     *
     * @param recordId 数据记录ID
     * @return 返回结果
     */
    @Override
    public EqPatrolRecordVo selectRecordById(Long recordId) {
        return recordMapper.selectVoById(recordId);
    }

    /**
     * 按任务查询记录
     *
     * @param taskId 巡检任务ID
     * @return 结果列表
     */
    @Override
    public List<EqPatrolRecordVo> selectRecordByTask(Long taskId) {
        return recordMapper.selectVoList(Wrappers.<EqPatrolRecord>lambdaQuery()
            .eq(EqPatrolRecord::getTaskId, taskId)
            .orderByAsc(EqPatrolRecord::getExecuteTime));
    }

    /**
     * 新增记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqPatrolRecordVo insertRecordByBo(EqPatrolRecordBo bo) {
        EqPatrolRecord record = buildRecord(bo);
        recordMapper.insert(record);
        return recordMapper.selectVoById(record.getRecordId());
    }

    /**
     * 获取记录详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteRecordByIds(Long[] ids) {
        return ids != null && ids.length > 0 && recordMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 批量处理记录
     *
     * @param bos 参数 bos
     * @return 结果列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EqPatrolRecordVo> batchSubmitRecords(List<EqPatrolRecordBo> bos) {
        if (bos == null || bos.isEmpty()) return List.of();
        // 校验关联任务存在且未完成(Long taskId = bos.get(0).getTaskId())
        Long taskId = bos.get(0).getTaskId();
        if (taskId != null) {
            EqPatrolTaskVo task = taskMapper.selectVoById(taskId);
            if (task == null) {
                throw new com.nexus.common.core.exception.ServiceException("巡检任务不存在: " + taskId);
            }
            if ("COMPLETED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
                throw new com.nexus.common.core.exception.ServiceException("巡检任务已结束, 无法提交记录: " + taskId);
            }
        }
        List<EqPatrolRecordVo> result = new ArrayList<>(bos.size());
        for (EqPatrolRecordBo bo : bos) {
            EqPatrolRecord record = buildRecord(bo);
            recordMapper.insert(record);
            result.add(recordMapper.selectVoById(record.getRecordId()));
        }
        log.info("批量提交巡检记录: {} 条, taskId={}", result.size(), bos.get(0).getTaskId());
        return result;
    }

    // ===== 辅助 =====

    private EqPatrolRecord buildRecord(EqPatrolRecordBo bo) {
        EqPatrolRecord record = new EqPatrolRecord();
        record.setTaskId(bo.getTaskId());
        record.setRouteId(bo.getRouteId());
        record.setEquipmentId(bo.getEquipmentId());
        record.setCheckItem(bo.getCheckItem());
        record.setActualValue(bo.getActualValue());
        record.setResult(StringUtils.isNotBlank(bo.getResult()) ? bo.getResult() : "NORMAL");
        record.setPhotoOssId(bo.getPhotoOssId());
        record.setExecutorId(LoginHelper.getUserId());
        record.setExecutorName(LoginHelper.getUsername());
        record.setExecuteTime(new Date());
        record.setRemark(bo.getRemark());
        // 冗余设备名
        if (bo.getEquipmentId() != null) {
            EqEquipment eq = equipmentMapper.selectById(bo.getEquipmentId());
            if (eq != null) {
                record.setEquipmentCode(eq.getEquipmentCode());
                record.setEquipmentName(eq.getEquipmentName());
            }
        }
        return record;
    }
}

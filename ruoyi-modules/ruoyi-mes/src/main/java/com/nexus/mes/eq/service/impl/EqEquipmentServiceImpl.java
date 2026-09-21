package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqInspectionRecord;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqInspectionRecordMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProAndonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES设备台账 Service 业务层实现
 * <p>
 * 状态: NORMAL/MAINTENANCE/FAULT/SCRAPPED
 * 新增默认 NORMAL; 状态可由维修记录联动或手动维护。
 * 状态实际变更时通过 MesMqSendService 发布 MQ 事件, 供排产/工单模块异步感知。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqEquipmentService")
public class EqEquipmentServiceImpl implements IEqEquipmentService {

    private final EqEquipmentMapper baseMapper;
    private final EqInspectionRecordMapper inspectionRecordMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;
    private final IProAndonService proAndonService;
    private final MesMqSendService mesMqSendService;
    private final ProWorkOrderMapper proWorkOrderMapper;

    /**
     * 分页查询设备列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqEquipmentVo> selectPageList(EqEquipmentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqEquipment> lqw = buildQueryWrapper(bo);
        Page<EqEquipmentVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取设备详情
     *
     * @param equipmentId 设备ID
     * @return 返回结果
     */
    @Override
    public EqEquipmentVo selectById(Long equipmentId) {
        return baseMapper.selectVoById(equipmentId);
    }

    /**
     * 查询设备列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    @Override
    public List<EqEquipmentVo> selectList(EqEquipmentBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询设备当前关联的进行中工单(P1-6 设备-工单联动)
     * <p>
     * 取 wo_status in (RELEASED, IN_PROGRESS) 的工单, 按创建时间倒序。
     * 与设备同库(ry-mes), TenantEntity 自动按租户隔离。
     *
     * @param equipmentId 设备ID
     * @return 结果列表
     */
    @Override
    public List<ProWorkOrderVo> currentWorkOrders(Long equipmentId) {
        return proWorkOrderMapper.selectVoList(Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(ProWorkOrder::getEquipmentId, equipmentId)
            .in(ProWorkOrder::getWoStatus, List.of("RELEASED", "IN_PROGRESS"))
            .orderByDesc(ProWorkOrder::getCreateTime));
    }

    /**
     * 设备状态计数汇总(全量 COUNT, 不受分页限制, 供监控大屏)
     *
     * @return 结果映射
     */
    @Override
    public Map<String, Object> statusSummary() {
        Map<String, Object> map = new LinkedHashMap<>(8);
        map.put("total", countByStatus(null));
        map.put("normal", countByStatus(STATUS_NORMAL));
        map.put("maintenance", countByStatus(STATUS_MAINTENANCE));
        map.put("fault", countByStatus(STATUS_FAULT));
        map.put("scrapped", countByStatus(STATUS_SCRAPPED));
        return map;
    }

    private long countByStatus(String status) {
        Long cnt = baseMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
            .eq(StringUtils.isNotBlank(status), EqEquipment::getStatus, status));
        return cnt == null ? 0L : cnt;
    }

    /**
     * 新增设备
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqEquipmentVo insertByBo(EqEquipmentBo bo) {
        // 校验编码唯一
        Long existCount = baseMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
            .eq(EqEquipment::getEquipmentCode, bo.getEquipmentCode()));
        if (existCount != null && existCount > 0) {
            throw new ServiceException("设备编码已存在: " + bo.getEquipmentCode());
        }
        // IoT 关联占用校验(MES-EQ-19)
        assertIotDeviceNotBound(bo.getEquipmentId(), bo.getIotDeviceId());
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentCode(bo.getEquipmentCode());
        equipment.setEquipmentName(bo.getEquipmentName());
        equipment.setEquipmentType(bo.getEquipmentType());
        equipment.setModel(bo.getModel());
        equipment.setManufacturer(bo.getManufacturer());
        equipment.setSerialNo(bo.getSerialNo());
        equipment.setWorkcenterId(bo.getWorkcenterId());
        equipment.setWorkcenterCode(bo.getWorkcenterCode());
        equipment.setLocation(bo.getLocation());
        equipment.setPurchaseDate(bo.getPurchaseDate());
        equipment.setWarrantyDate(bo.getWarrantyDate());
        equipment.setStatus(StringUtils.isNotBlank(bo.getStatus()) ? bo.getStatus() : STATUS_NORMAL);
        equipment.setInspectionItems(bo.getInspectionItems());
        equipment.setLastInspection(bo.getLastInspection());
        equipment.setIotDeviceId(bo.getIotDeviceId());
        equipment.setPlannedMinutesPerDay(bo.getPlannedMinutesPerDay());
        equipment.setRemark(bo.getRemark());
        baseMapper.insert(equipment);
        log.info("新增设备台账: equipmentId={}, code={}", equipment.getEquipmentId(), equipment.getEquipmentCode());
        return baseMapper.selectVoById(equipment.getEquipmentId());
    }

    /**
     * 更新设备
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqEquipmentVo updateByBo(EqEquipmentBo bo) {
        if (bo.getEquipmentId() == null) {
            throw new ServiceException("设备ID不能为空");
        }
        EqEquipment equipment = baseMapper.selectById(bo.getEquipmentId());
        if (equipment == null) {
            throw new ServiceException("设备不存在: " + bo.getEquipmentId());
        }
        // 编码变更需校验唯一
        if (StringUtils.isNotBlank(bo.getEquipmentCode())
            && !bo.getEquipmentCode().equals(equipment.getEquipmentCode())) {
            Long existCount = baseMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
                .eq(EqEquipment::getEquipmentCode, bo.getEquipmentCode())
                .ne(EqEquipment::getEquipmentId, bo.getEquipmentId()));
            if (existCount != null && existCount > 0) {
                throw new ServiceException("设备编码已存在: " + bo.getEquipmentCode());
            }
            equipment.setEquipmentCode(bo.getEquipmentCode());
        }
        equipment.setEquipmentName(bo.getEquipmentName());
        equipment.setEquipmentType(bo.getEquipmentType());
        equipment.setModel(bo.getModel());
        equipment.setManufacturer(bo.getManufacturer());
        equipment.setSerialNo(bo.getSerialNo());
        equipment.setWorkcenterId(bo.getWorkcenterId());
        equipment.setWorkcenterCode(bo.getWorkcenterCode());
        equipment.setLocation(bo.getLocation());
        equipment.setPurchaseDate(bo.getPurchaseDate());
        equipment.setWarrantyDate(bo.getWarrantyDate());
        if (StringUtils.isNotBlank(bo.getStatus())) {
            equipment.setStatus(bo.getStatus());
        }
        equipment.setInspectionItems(bo.getInspectionItems());
        equipment.setLastInspection(bo.getLastInspection());
        // IoT 关联变更同样需要占用校验(MES-EQ-19)
        assertIotDeviceNotBound(bo.getEquipmentId(), bo.getIotDeviceId());
        equipment.setIotDeviceId(bo.getIotDeviceId());
        equipment.setPlannedMinutesPerDay(bo.getPlannedMinutesPerDay());
        equipment.setRemark(bo.getRemark());
        baseMapper.updateById(equipment);
        log.info("编辑设备台账: equipmentId={}", equipment.getEquipmentId());
        return baseMapper.selectVoById(equipment.getEquipmentId());
    }

    /**
     * 获取设备详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 更新设备状态(无原因), 委托带 reason 的重载方法
     *
     * @param equipmentId 设备ID
     * @param status 目标状态
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateStatus(Long equipmentId, String status) {
        return updateStatus(equipmentId, status, null);
    }

    /**
     * 更新设备状态(供维修/点检/保养联动调用), 不改 SCRAPPED 终态;
     * 状态实际变更时发布 EquipmentStatusMessage MQ 事件。
     *
     * @param equipmentId 设备ID
     * @param status 目标状态
     * @param reason 变更原因（可空）
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateStatus(Long equipmentId, String status, String reason) {
        if (equipmentId == null || StringUtils.isBlank(status)) {
            return false;
        }
        EqEquipment equipment = baseMapper.selectById(equipmentId);
        if (equipment == null) {
            log.warn("设备不存在, 跳过状态联动: equipmentId={}", equipmentId);
            return false;
        }
        // SCRAPPED 为终态, 不被报修联动覆盖
        if (STATUS_SCRAPPED.equals(equipment.getStatus()) && !STATUS_SCRAPPED.equals(status)) {
            log.info("设备已报废, 跳过状态联动: equipmentId={}, target={}", equipmentId, status);
            return false;
        }
        // 保养中不接受"故障"覆盖(MES-EQ-22): 报修/点检不合格不得抹掉保养标记(MAINTENANCE)。
        // 故障本身已记录在 eq_repair; 保养结束时 finishMaintenance 会按活跃报修重新裁决为 FAULT。
        if (STATUS_FAULT.equals(status) && STATUS_MAINTENANCE.equals(equipment.getStatus())) {
            log.warn("设备保养中, 跳过故障状态覆盖(保留 MAINTENANCE): equipmentId={}, reason={}", equipmentId, reason);
            return false;
        }
        String oldStatus = equipment.getStatus();
        // 状态未变化则不重复发事件
        if (status.equals(oldStatus)) {
            return true;
        }
        // CAS: 仅当当前状态仍为 oldStatus 时更新, 避免并发(如点检不合格与报修同时触发)覆盖终态/产生重复 MQ 事件
        int rows = baseMapper.update(null, Wrappers.<EqEquipment>lambdaUpdate()
            .set(EqEquipment::getStatus, status)
            .eq(EqEquipment::getEquipmentId, equipmentId)
            .eq(EqEquipment::getStatus, oldStatus));
        log.info("设备状态联动: equipmentId={}, {}->{} (rows={})", equipmentId, oldStatus, status, rows);
        if (rows > 0) {
            // 事务提交后发布 MQ 事件, 避免回滚产生幻影事件(M1)
            publishStatusEvent(equipmentId, equipment.getEquipmentCode(), equipment.getEquipmentName(),
                oldStatus, status, reason);
        }
        return rows > 0;
    }

    /**
     * 维修完成时联动恢复设备 NORMAL: 原子操作, 仅当该设备无其他活跃报修(REPORTED/REPAIRING)
     * 且非 SCRAPPED 终态时才置 NORMAL。以单条 UPDATE + NOT EXISTS 消除"查询活跃报修数 ->
     * 更新设备状态"两步之间的 TOCTOU(完成瞬间插入新报修导致误恢复, M3)。状态实际变更时发布 MQ 事件。
     *
     * @param equipmentId 设备ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetToNormalIfNoActiveRepair(Long equipmentId) {
        if (equipmentId == null) {
            return false;
        }
        EqEquipment equipment = baseMapper.selectById(equipmentId);
        if (equipment == null) {
            log.warn("设备不存在, 跳过状态联动: equipmentId={}", equipmentId);
            return false;
        }
        String oldStatus = equipment.getStatus();
        // 仅 FAULT 需要收口(MES-EQ-21): 维修完工恢复 NORMAL 只允许清 FAULT。
        // 原判据只跳过 NORMAL/SCRAPPED, 会把 MAINTENANCE(保养中)/OFFLINE 静默改成 NORMAL, 抹掉保养标记;
        // 保养中的设备由 finishMaintenance 在保养结束时按活跃报修自行裁决。
        if (!STATUS_FAULT.equals(oldStatus)) {
            log.debug("设备当前状态非 FAULT, 跳过维修完成恢复: equipmentId={}, status={}", equipmentId, oldStatus);
            return false;
        }
        // 原子 CAS: 仅当无其他活跃报修且非 SCRAPPED 时置 NORMAL
        int rows = baseMapper.resetToNormalIfNoActiveRepair(equipmentId);
        log.info("设备维修完成联动恢复: equipmentId={}, {}->NORMAL (rows={})", equipmentId, oldStatus, rows);
        if (rows > 0) {
            publishStatusEvent(equipmentId, equipment.getEquipmentCode(), equipment.getEquipmentName(),
                oldStatus, STATUS_NORMAL, "维修完成");
            return true;
        }
        return false;
    }

    /**
     * 结束保养: MAINTENANCE -> NORMAL(仍有活跃报修则 FAULT), CAS 限定当前状态(MES-EQ-05)
     *
     * @param equipmentId 设备ID
     * @param reason 结束原因
     * @return 是否实际结束了保养
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean finishMaintenance(Long equipmentId, String reason) {
        if (equipmentId == null) {
            return false;
        }
        EqEquipment equipment = baseMapper.selectById(equipmentId);
        if (equipment == null) {
            log.warn("设备不存在, 跳过结束保养: equipmentId={}", equipmentId);
            return false;
        }
        String oldStatus = equipment.getStatus();
        // 仅"保养中"需要收口; 其他状态一律不碰(避免误改 FAULT/SCRAPPED)
        if (!STATUS_MAINTENANCE.equals(oldStatus)) {
            return false;
        }
        int rows = baseMapper.finishMaintenance(equipmentId);
        if (rows == 0) {
            log.info("设备结束保养未命中(CAS 未命中或状态已变更): equipmentId={}", equipmentId);
            return false;
        }
        // 回读实际落库状态, 使 MQ 事件的 newStatus 与库内一致(有活跃报修时为 FAULT)
        String newStatus = STATUS_NORMAL;
        EqEquipment after = baseMapper.selectById(equipmentId);
        if (after != null && StringUtils.isNotBlank(after.getStatus())) {
            newStatus = after.getStatus();
        }
        log.info("设备结束保养: equipmentId={}, {}->{}", equipmentId, oldStatus, newStatus);
        publishStatusEvent(equipmentId, equipment.getEquipmentCode(), equipment.getEquipmentName(),
            oldStatus, newStatus, StringUtils.isNotBlank(reason) ? reason : "保养结束");
        return true;
    }

    /**
     * 发布设备状态变更 MQ 事件: 注册到事务 afterCommit 回调, 仅事务提交后才发送,
     * 避免事务回滚时已发送的幻影事件(M1)。
     * <p>
     * 租户ID必须在注册前捕获: afterCommit 回调与 @Async 发送线程均无法读取
     * Sa-Token 的 ThreadLocal(WmMaterialReceiveExecutorServiceImpl 同此约束)。
     */
    private void publishStatusEvent(Long equipmentId, String equipmentCode, String equipmentName,
                                   String oldStatus, String newStatus, String reason) {
        if (equipmentId == null) {
            return;
        }
        final String tenantId = TenantHelper.getTenantId();
        Runnable send = () -> {
            try {
                mesMqSendService.sendEquipmentStatus(equipmentId, equipmentCode, equipmentName,
                    oldStatus, newStatus, reason, tenantId);
            } catch (Exception e) {
                log.warn("设备状态变更事件发送失败: equipmentId={}", equipmentId, e);
            }
        };
        // 有事务上下文则延迟到提交后发送; 否则直接发送(回退, 兼容无事务调用)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 设备相关操作
                 *
                 */
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<EqEquipment> buildQueryWrapper(EqEquipmentBo bo) {
        return Wrappers.<EqEquipment>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getEquipmentCode()), EqEquipment::getEquipmentCode, bo.getEquipmentCode())
            .like(StringUtils.isNotBlank(bo.getEquipmentName()), EqEquipment::getEquipmentName, bo.getEquipmentName())
            .eq(StringUtils.isNotBlank(bo.getEquipmentType()), EqEquipment::getEquipmentType, bo.getEquipmentType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqEquipment::getStatus, bo.getStatus())
            .eq(bo.getWorkcenterId() != null, EqEquipment::getWorkcenterId, bo.getWorkcenterId())
            .eq(bo.getIotDeviceId() != null, EqEquipment::getIotDeviceId, bo.getIotDeviceId())
            .orderByDesc(EqEquipment::getCreateTime);
    }

    // ==================== IoT 状态查询辅助方法 ====================

    private static final String IOT_STATUS_UNBOUND = "UNBOUND";
    private static final String IOT_STATUS_UNKNOWN = "UNKNOWN";

    /**
     * 获取设备详情
     *
     * @param equipmentId 设备ID
     * @return 结果字符串
     */
    @Override
    public String getIotStatus(Long equipmentId) {
        EqEquipment eq = baseMapper.selectById(equipmentId);
        return resolveIotStatus(eq);
    }

    /**
     * 批量处理设备
     *
     * @param equipmentIds 设备ID列表（逗号分隔）
     * @return 结果映射
     */
    @Override
    public Map<Long, String> batchIotStatus(List<Long> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 一次查出设备(含 iotDeviceId), 消除前端逐行 HTTP 请求
        List<EqEquipment> eqs = baseMapper.selectByIds(equipmentIds);
        Map<Long, String> result = new HashMap<>();
        for (EqEquipment eq : eqs) {
            result.put(eq.getEquipmentId(), resolveIotStatus(eq));
        }
        return result;
    }

    /** 解析单台设备的 IoT 在线状态 */
    private String resolveIotStatus(EqEquipment eq) {
        if (eq == null || eq.getIotDeviceId() == null) {
            return IOT_STATUS_UNBOUND;
        }
        try {
            String status = iotDeviceDataQueryService.getOnlineStatus(eq.getIotDeviceId());
            return status == null ? IOT_STATUS_UNKNOWN : status;
        } catch (Exception e) {
            log.warn("查询 IoT 设备在线状态失败: equipmentId={}, iotDeviceId={}",
                eq.getEquipmentId(), eq.getIotDeviceId(), e);
            return IOT_STATUS_UNKNOWN;
        }
    }

    /**
     * 获取设备详情
     *
     * @param equipmentId 设备ID
     * @return 结果映射
     */
    @Override
    public Map<String, String> getIotLatest(Long equipmentId) {
        EqEquipment eq = baseMapper.selectById(equipmentId);
        if (eq == null || eq.getIotDeviceId() == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> data = iotDeviceDataQueryService.getLatestData(eq.getIotDeviceId());
            return data == null ? Collections.emptyMap() : data;
        } catch (Exception e) {
            log.warn("查询 IoT 设备最新数据失败: equipmentId={}, iotDeviceId={}",
                equipmentId, eq.getIotDeviceId(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 更新设备
     *
     * @param equipmentId 设备ID
     * @param iotDeviceId 关联的 IoT 设备ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateIotDeviceLink(Long equipmentId, Long iotDeviceId) {
        if (equipmentId == null) {
            return false;
        }
        // 占用校验(MES-EQ-19): 同一 IoT 设备不允许同时绑定多台 MES 设备(排除自身);
        // 并发窗口由 eq_equipment.iot_device_id 唯一索引兜底(DDL 见汇报)。
        assertIotDeviceNotBound(equipmentId, iotDeviceId);
        LambdaUpdateWrapper<EqEquipment> wrapper = Wrappers.<EqEquipment>lambdaUpdate()
            .set(EqEquipment::getIotDeviceId, iotDeviceId)
            .eq(EqEquipment::getEquipmentId, equipmentId);
        int rows = baseMapper.update(null, wrapper);
        log.info("更新设备 IoT 关联: equipmentId={}, iotDeviceId={}, rows={}",
            equipmentId, iotDeviceId, rows);
        return rows > 0;
    }

    /**
     * IoT 设备占用校验(MES-EQ-19): 同一 {@code iot_device_id} 只允许绑定一台 MES 设备。
     *
     * @param equipmentId 当前设备ID(排除自身, 新建设备时为 null 表示不排除)
     * @param iotDeviceId 待绑定的 IoT 设备ID(为 null 表示解绑, 直接放行)
     * @throws ServiceException 该 IoT 设备已被其他设备占用
     */
    private void assertIotDeviceNotBound(Long equipmentId, Long iotDeviceId) {
        if (iotDeviceId == null) {
            return;
        }
        Long occupied = baseMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
            .eq(EqEquipment::getIotDeviceId, iotDeviceId)
            .ne(equipmentId != null, EqEquipment::getEquipmentId, equipmentId));
        if (occupied != null && occupied > 0) {
            throw new ServiceException("该IoT设备已被其他设备绑定, 不可重复关联: iotDeviceId=" + iotDeviceId);
        }
    }

    /**
     * 提交点检
     *
     * @param equipmentId 设备ID
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitInspection(Long equipmentId, InspectionResultBo bo) {
        if (equipmentId == null) {
            throw new ServiceException("设备ID不能为空");
        }
        EqEquipment equipment = baseMapper.selectById(equipmentId);
        if (equipment == null) {
            throw new ServiceException("设备不存在: " + equipmentId);
        }

        // 1. 构建 last_inspection JSON
        Map<String, Object> inspectionRecord = new HashMap<>();
        inspectionRecord.put("inspectTime", new Date());
        Long userId = LoginHelper.getUserId();
        inspectionRecord.put("inspectorId", userId);
        inspectionRecord.put("inspectorName", LoginHelper.getUsername());
        inspectionRecord.put("results", bo.getResults());
        inspectionRecord.put("overallPass", bo.getOverallPass());
        inspectionRecord.put("remark", bo.getRemark());

        String lastInspectionJson = JsonUtils.toJsonString(inspectionRecord);

        // 2. 更新 last_inspection 字段(单列更新, 避免覆盖其他字段)
        LambdaUpdateWrapper<EqEquipment> wrapper = Wrappers.<EqEquipment>lambdaUpdate()
            .set(EqEquipment::getLastInspection, lastInspectionJson)
            .eq(EqEquipment::getEquipmentId, equipmentId);
        int rows = baseMapper.update(null, wrapper);

        // 3. 历史留痕: 与 PDA/工位入口同口径写入 eq_inspection_record(MES-EQ-09),
        //    last_inspection 仅作为"最近一次"快照保留
        EqInspectionRecord history = new EqInspectionRecord();
        history.setEquipmentId(equipmentId);
        history.setStationCode(equipment.getWorkcenterCode());
        history.setInspectorId(userId);
        history.setInspectorName(LoginHelper.getUsername());
        history.setOperatorCode(LoginHelper.getUsername());
        history.setInspectTime(new Date());
        history.setResults(JsonUtils.toJsonString(bo.getResults()));
        history.setOverallPass(Boolean.FALSE.equals(bo.getOverallPass()) ? "N" : "Y");
        history.setRemark(bo.getRemark());
        inspectionRecordMapper.insert(history);

        // 4. 点检不合格: 设备状态置为 FAULT + 触发 EQUIPMENT 安灯
        if (Boolean.FALSE.equals(bo.getOverallPass())) {
            updateStatus(equipmentId, STATUS_FAULT, "点检不合格");
            String reason = String.format("点检不合格: 设备[%s(%s)] 不合格项[%d]项",
                equipment.getEquipmentName(),
                equipment.getEquipmentCode(),
                bo.getResults().stream().filter(r -> Boolean.FALSE.equals(r.getPass())).count());
            try {
                proAndonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
                log.info("设备 {} 点检不合格已触发安灯", equipmentId);
            } catch (Exception e) {
                log.warn("设备 {} 点检不合格触发安灯失败: {}", equipmentId, e.getMessage());
            }
        } else {
            // 5. 点检合格: 若设备因保养异常处于 MAINTENANCE, 视为保养异常已闭环, 释放回 NORMAL/FAULT(MES-EQ-05)
            finishMaintenance(equipmentId, "点检合格, 结束保养");
        }

        return rows > 0;
    }
}

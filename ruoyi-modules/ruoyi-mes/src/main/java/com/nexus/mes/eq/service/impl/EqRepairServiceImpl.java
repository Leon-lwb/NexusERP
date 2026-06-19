package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.bo.EqRepairBo;
import com.nexus.mes.eq.domain.vo.EqRepairVo;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.eq.service.IEqRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * MES设备维修记录 Service 业务层实现
 * <p>
 * 状态机: REPORTED -> REPAIRING -> COMPLETED
 * 设备状态联动:
 *   报修(INSERT) -> 设备 status=FAULT(SCRAPPED 终态不联动)
 *   完成(COMPLETE) -> 该设备无其他活跃报修(REPORTED/REPAIRING)则恢复 NORMAL
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqRepairService")
public class EqRepairServiceImpl implements IEqRepairService {

    private final EqRepairMapper baseMapper;
    private final IEqEquipmentService equipmentService;

    @Override
    public TableDataInfo<EqRepairVo> selectPageList(EqRepairBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqRepair> lqw = buildQueryWrapper(bo);
        Page<EqRepairVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public EqRepairVo selectById(Long repairId) {
        return baseMapper.selectVoById(repairId);
    }

    /**
     * 报修(新增维修单 REPORTED), 联动置设备 FAULT
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqRepairVo insertByBo(EqRepairBo bo) {
        EqRepair repair = new EqRepair();
        repair.setRepairNo(generateNo());
        repair.setEquipmentId(bo.getEquipmentId());
        repair.setFaultType(bo.getFaultType());
        repair.setFaultDesc(bo.getFaultDesc());
        repair.setReporterId(LoginHelper.getUserId());
        repair.setReporterName(LoginHelper.getUsername());
        repair.setStatus(STATUS_REPORTED);
        repair.setRemark(bo.getRemark());
        baseMapper.insert(repair);
        log.info("新增维修单: repairId={}, repairNo={}, equipmentId={}", repair.getRepairId(), repair.getRepairNo(), repair.getEquipmentId());

        // 联动: 设备状态置 FAULT(SCRAPPED 终态由 updateStatus 内部跳过)
        equipmentService.updateStatus(bo.getEquipmentId(), IEqEquipmentService.STATUS_FAULT);
        return baseMapper.selectVoById(repair.getRepairId());
    }

    /**
     * 开工(REPORTED -> REPAIRING), 置维修人/维修开始时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startRepair(Long repairId) {
        int rows = baseMapper.update(null, Wrappers.<EqRepair>lambdaUpdate()
            .set(EqRepair::getRepairerId, LoginHelper.getUserId())
            .set(EqRepair::getRepairerName, LoginHelper.getUsername())
            .set(EqRepair::getRepairStart, new Date())
            .set(EqRepair::getStatus, STATUS_REPAIRING)
            .eq(EqRepair::getRepairId, repairId)
            .eq(EqRepair::getStatus, STATUS_REPORTED));
        if (rows == 0) {
            throw new RuntimeException("维修单状态已变更, 仅已报修可开工");
        }
        log.info("维修开工: repairId={}", repairId);
    }

    /**
     * 完成(REPAIRING -> COMPLETED), 联动: 该设备无其他活跃报修则恢复 NORMAL
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeRepair(Long repairId, BigDecimal downtimeHours, BigDecimal repairCost, String remark) {
        EqRepair repair = baseMapper.selectById(repairId);
        if (repair == null) {
            throw new RuntimeException("维修单不存在: " + repairId);
        }
        if (!STATUS_REPAIRING.equals(repair.getStatus())) {
            throw new RuntimeException("维修单状态已变更, 仅维修中可完成");
        }
        Date repairEnd = new Date();
        int rows = baseMapper.update(null, Wrappers.<EqRepair>lambdaUpdate()
            .set(EqRepair::getRepairEnd, repairEnd)
            .set(downtimeHours != null, EqRepair::getDowntimeHours, downtimeHours)
            .set(repairCost != null, EqRepair::getRepairCost, repairCost)
            .set(StringUtils.isNotBlank(remark), EqRepair::getRemark, remark)
            .set(EqRepair::getStatus, STATUS_COMPLETED)
            .eq(EqRepair::getRepairId, repairId)
            .eq(EqRepair::getStatus, STATUS_REPAIRING));
        if (rows == 0) {
            throw new RuntimeException("维修单状态已变更, 完成失败");
        }
        log.info("维修完成: repairId={}, downtimeHours={}, repairCost={}", repairId, downtimeHours, repairCost);

        // 联动: 校验该设备是否还有其他活跃报修(REPORTED/REPAIRING), 无则恢复 NORMAL
        Long activeCount = baseMapper.selectCount(Wrappers.<EqRepair>lambdaQuery()
            .eq(EqRepair::getEquipmentId, repair.getEquipmentId())
            .in(EqRepair::getStatus, STATUS_REPORTED, STATUS_REPAIRING)
            .ne(EqRepair::getRepairId, repairId));
        if (activeCount == null || activeCount == 0) {
            equipmentService.updateStatus(repair.getEquipmentId(), IEqEquipmentService.STATUS_NORMAL);
            log.info("设备无其他活跃报修, 恢复正常: equipmentId={}", repair.getEquipmentId());
        } else {
            log.info("设备仍有{}条活跃报修, 维持故障状态: equipmentId={}", activeCount, repair.getEquipmentId());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成维修单号: RP + yyyyMMdd + 3位序号(uk 唯一索引兜底)
     */
    private String generateNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = baseMapper.selectCount(Wrappers.<EqRepair>lambdaQuery()
            .likeRight(EqRepair::getRepairNo, "RP-" + today));
        int seq = (count == null ? 0 : count.intValue()) + 1;
        return "RP-" + today + "-" + String.format("%03d", seq);
    }

    private LambdaQueryWrapper<EqRepair> buildQueryWrapper(EqRepairBo bo) {
        return Wrappers.<EqRepair>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getRepairNo()), EqRepair::getRepairNo, bo.getRepairNo())
            .eq(bo.getEquipmentId() != null, EqRepair::getEquipmentId, bo.getEquipmentId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqRepair::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getFaultType()), EqRepair::getFaultType, bo.getFaultType())
            .orderByDesc(EqRepair::getCreateTime);
    }
}

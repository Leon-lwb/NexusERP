package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * MES设备台账 Service 业务层实现
 * <p>
 * 状态: NORMAL/MAINTENANCE/FAULT/SCRAPPED
 * 新增默认 NORMAL; 状态可由维修记录联动或手动维护。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqEquipmentService")
public class EqEquipmentServiceImpl implements IEqEquipmentService {

    private final EqEquipmentMapper baseMapper;

    @Override
    public TableDataInfo<EqEquipmentVo> selectPageList(EqEquipmentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqEquipment> lqw = buildQueryWrapper(bo);
        Page<EqEquipmentVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public EqEquipmentVo selectById(Long equipmentId) {
        return baseMapper.selectVoById(equipmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqEquipmentVo insertByBo(EqEquipmentBo bo) {
        // 校验编码唯一
        Long existCount = baseMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
            .eq(EqEquipment::getEquipmentCode, bo.getEquipmentCode()));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException("设备编码已存在: " + bo.getEquipmentCode());
        }
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
        equipment.setRemark(bo.getRemark());
        baseMapper.insert(equipment);
        log.info("新增设备台账: equipmentId={}, code={}", equipment.getEquipmentId(), equipment.getEquipmentCode());
        return baseMapper.selectVoById(equipment.getEquipmentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqEquipmentVo updateByBo(EqEquipmentBo bo) {
        if (bo.getEquipmentId() == null) {
            throw new RuntimeException("设备ID不能为空");
        }
        EqEquipment equipment = baseMapper.selectById(bo.getEquipmentId());
        if (equipment == null) {
            throw new RuntimeException("设备不存在: " + bo.getEquipmentId());
        }
        // 编码变更需校验唯一
        if (StringUtils.isNotBlank(bo.getEquipmentCode())
            && !bo.getEquipmentCode().equals(equipment.getEquipmentCode())) {
            Long existCount = baseMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
                .eq(EqEquipment::getEquipmentCode, bo.getEquipmentCode())
                .ne(EqEquipment::getEquipmentId, bo.getEquipmentId()));
            if (existCount != null && existCount > 0) {
                throw new RuntimeException("设备编码已存在: " + bo.getEquipmentCode());
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
        equipment.setIotDeviceId(bo.getIotDeviceId());
        equipment.setRemark(bo.getRemark());
        baseMapper.updateById(equipment);
        log.info("编辑设备台账: equipmentId={}", equipment.getEquipmentId());
        return baseMapper.selectVoById(equipment.getEquipmentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 更新设备状态(供维修Service联动调用), 不改 SCRAPPED 终态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateStatus(Long equipmentId, String status) {
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
        equipment.setStatus(status);
        int rows = baseMapper.updateById(equipment);
        log.info("设备状态联动: equipmentId={}, status={}", equipmentId, status);
        return rows > 0;
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
}

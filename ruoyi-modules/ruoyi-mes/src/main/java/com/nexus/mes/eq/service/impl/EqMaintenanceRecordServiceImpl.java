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
import com.nexus.mes.eq.domain.EqMaintenanceRecord;
import com.nexus.mes.eq.domain.bo.EqMaintenanceRecordBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.domain.vo.EqMaintenanceRecordVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqMaintenanceRecordMapper;
import com.nexus.mes.eq.service.IEqMaintenanceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES设备保养执行记录 Service 业务层实现
 *
 * <p>属于MES 设备管理模块，保养记录业务层实现。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqMaintenanceRecordService")
public class EqMaintenanceRecordServiceImpl implements IEqMaintenanceRecordService {

    private final EqMaintenanceRecordMapper baseMapper;
    private final EqEquipmentMapper equipmentMapper;

    /**
     * 分页查询保养记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqMaintenanceRecordVo> selectPageList(EqMaintenanceRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqMaintenanceRecord> lqw = buildQueryWrapper(bo);
        Page<EqMaintenanceRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillEquipmentName(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询保养记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    @Override
    public List<EqMaintenanceRecordVo> selectList(EqMaintenanceRecordBo bo) {
        List<EqMaintenanceRecordVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillEquipmentName(list);
        return list;
    }

    /**
     * 获取保养记录详情
     *
     * @param recordId 数据记录ID
     * @return 返回结果
     */
    @Override
    public EqMaintenanceRecordVo selectById(Long recordId) {
        EqMaintenanceRecordVo vo = baseMapper.selectVoById(recordId);
        if (vo != null) {
            fillEquipmentName(List.of(vo));
        }
        return vo;
    }

    /**
     * 按条件查询设备
     *
     * @param equipmentId 设备ID
     * @return 结果列表
     */
    @Override
    public List<EqMaintenanceRecordVo> selectByEquipment(Long equipmentId) {
        List<EqMaintenanceRecordVo> list = baseMapper.selectVoList(Wrappers.<EqMaintenanceRecord>lambdaQuery()
            .eq(EqMaintenanceRecord::getEquipmentId, equipmentId)
            .orderByDesc(EqMaintenanceRecord::getExecuteTime));
        fillEquipmentName(list);
        return list;
    }

    /**
     * 新增保养记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqMaintenanceRecordVo insertByBo(EqMaintenanceRecordBo bo) {
        EqMaintenanceRecord record = new EqMaintenanceRecord();
        record.setPlanId(bo.getPlanId());
        record.setEquipmentId(bo.getEquipmentId());
        record.setMaintenanceType(bo.getMaintenanceType());
        record.setExecutorId(LoginHelper.getUserId());
        record.setExecutorName(LoginHelper.getUsername());
        record.setExecuteTime(bo.getExecuteTime() != null ? bo.getExecuteTime() : new Date());
        record.setContent(bo.getContent());
        record.setResult(StringUtils.isNotBlank(bo.getResult()) ? bo.getResult() : RESULT_NORMAL);
        record.setRemark(bo.getRemark());
        baseMapper.insert(record);
        log.info("新增保养执行记录: recordId={}, equipmentId={}", record.getRecordId(), record.getEquipmentId());
        return baseMapper.selectVoById(record.getRecordId());
    }

    /**
     * 获取保养记录详情
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

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<EqMaintenanceRecord> buildQueryWrapper(EqMaintenanceRecordBo bo) {
        return Wrappers.<EqMaintenanceRecord>lambdaQuery()
            .eq(bo.getPlanId() != null, EqMaintenanceRecord::getPlanId, bo.getPlanId())
            .eq(bo.getEquipmentId() != null, EqMaintenanceRecord::getEquipmentId, bo.getEquipmentId())
            .eq(StringUtils.isNotBlank(bo.getMaintenanceType()), EqMaintenanceRecord::getMaintenanceType, bo.getMaintenanceType())
            .eq(StringUtils.isNotBlank(bo.getResult()), EqMaintenanceRecord::getResult, bo.getResult())
            .orderByDesc(EqMaintenanceRecord::getExecuteTime);
    }

    /** 批量补填设备编码/名称(联表查询在 Service 层完成, 避免 Mapper XML) */
    private void fillEquipmentName(List<EqMaintenanceRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = records.stream()
            .map(EqMaintenanceRecordVo::getEquipmentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<EqEquipmentVo> eqs = equipmentMapper.selectVoList(
            Wrappers.<EqEquipment>lambdaQuery().in(EqEquipment::getEquipmentId, ids));
        Map<Long, EqEquipmentVo> map = eqs.stream()
            .collect(Collectors.toMap(EqEquipmentVo::getEquipmentId, e -> e));
        records.forEach(r -> {
            EqEquipmentVo e = map.get(r.getEquipmentId());
            if (e != null) {
                r.setEquipmentCode(e.getEquipmentCode());
                r.setEquipmentName(e.getEquipmentName());
            }
        });
    }
}

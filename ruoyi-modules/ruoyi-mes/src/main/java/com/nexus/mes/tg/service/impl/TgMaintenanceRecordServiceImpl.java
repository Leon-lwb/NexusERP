package com.nexus.mes.tg.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.TgMaintenanceRecord;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.vo.TgMaintenanceRecordVo;
import com.nexus.mes.tg.mapper.TgMaintenanceRecordMapper;
import com.nexus.mes.tg.service.ITgMaintenanceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * MES工装保养记录 Service 业务层实现
 * <p>注: 本接口仅做记录 CRUD,不触发工装归零/状态机;
 * 归零逻辑由 TgToolingService.completeMaintain 承载(走 /maintain/complete 端点)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("tgMaintenanceRecordService")
public class TgMaintenanceRecordServiceImpl implements ITgMaintenanceRecordService {

    private final TgMaintenanceRecordMapper baseMapper;

    @Override
    public TableDataInfo<TgMaintenanceRecordVo> selectPageList(TgMaintenanceRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<TgMaintenanceRecord> lqw = buildQueryWrapper(bo);
        Page<TgMaintenanceRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TgMaintenanceRecordVo selectById(Long recordId) {
        return baseMapper.selectVoById(recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TgMaintenanceRecordBo bo) {
        TgMaintenanceRecord rec = new TgMaintenanceRecord();
        rec.setToolingId(bo.getToolingId());
        rec.setPlanId(bo.getPlanId());
        rec.setMaintainType(bo.getMaintainType() == null ? "PLANNED" : bo.getMaintainType());
        rec.setMaintainTime(bo.getMaintainTime() == null ? new Date() : bo.getMaintainTime());
        rec.setMaintainerId(bo.getMaintainerId());
        rec.setMaintainerName(bo.getMaintainerName());
        rec.setUsedCountBefore(bo.getUsedCountBefore());
        rec.setUsedCountAfter(bo.getUsedCountAfter());
        rec.setUsedHoursBefore(bo.getUsedHoursBefore());
        rec.setUsedHoursAfter(bo.getUsedHoursAfter());
        rec.setDescription(bo.getDescription());
        rec.setCost(bo.getCost());
        rec.setRemark(bo.getRemark());
        int rows = baseMapper.insert(rec);
        log.info("新增工装保养记录: toolingId={}", bo.getToolingId());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TgMaintenanceRecordBo bo) {
        TgMaintenanceRecord rec = baseMapper.selectById(bo.getRecordId());
        if (rec == null) {
            throw new RuntimeException("保养记录不存在: " + bo.getRecordId());
        }
        rec.setMaintainType(bo.getMaintainType());
        rec.setMaintainTime(bo.getMaintainTime());
        rec.setMaintainerId(bo.getMaintainerId());
        rec.setMaintainerName(bo.getMaintainerName());
        rec.setUsedCountBefore(bo.getUsedCountBefore());
        rec.setUsedCountAfter(bo.getUsedCountAfter());
        rec.setUsedHoursBefore(bo.getUsedHoursBefore());
        rec.setUsedHoursAfter(bo.getUsedHoursAfter());
        rec.setDescription(bo.getDescription());
        rec.setCost(bo.getCost());
        rec.setRemark(bo.getRemark());
        return baseMapper.updateById(rec) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteByIds(java.util.Arrays.asList(ids)) > 0;
    }

    private LambdaQueryWrapper<TgMaintenanceRecord> buildQueryWrapper(TgMaintenanceRecordBo bo) {
        return Wrappers.<TgMaintenanceRecord>lambdaQuery()
            .eq(bo.getToolingId() != null, TgMaintenanceRecord::getToolingId, bo.getToolingId())
            .eq(bo.getPlanId() != null, TgMaintenanceRecord::getPlanId, bo.getPlanId())
            .eq(StringUtils.isNotBlank(bo.getMaintainType()), TgMaintenanceRecord::getMaintainType, bo.getMaintainType())
            .orderByDesc(TgMaintenanceRecord::getMaintainTime);
    }
}

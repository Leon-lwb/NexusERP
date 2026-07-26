package com.nexus.mes.tg.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.TgUsageRecord;
import com.nexus.mes.tg.domain.bo.TgUsageRecordBo;
import com.nexus.mes.tg.domain.vo.TgUsageRecordVo;
import com.nexus.mes.tg.mapper.TgUsageRecordMapper;
import com.nexus.mes.tg.service.ITgUsageRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * MES工装使用/领用记录 Service 业务层实现
 * <p>注: 本接口仅做记录 CRUD,不触发工装状态机;
 * 领用/归还由 TgToolingService.checkout/doReturn 承载(走 /checkout /return 端点)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("tgUsageRecordService")
public class TgUsageRecordServiceImpl implements ITgUsageRecordService {

    private final TgUsageRecordMapper baseMapper;

    @Override
    public TableDataInfo<TgUsageRecordVo> selectPageList(TgUsageRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<TgUsageRecord> lqw = buildQueryWrapper(bo);
        Page<TgUsageRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TgUsageRecordVo selectById(Long usageId) {
        return baseMapper.selectVoById(usageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TgUsageRecordBo bo) {
        TgUsageRecord rec = new TgUsageRecord();
        rec.setToolingId(bo.getToolingId());
        rec.setWoId(bo.getWoId());
        rec.setWoNo(bo.getWoNo());
        rec.setWorkstationId(bo.getWorkstationId());
        rec.setWorkstationName(bo.getWorkstationName());
        rec.setOperatorId(bo.getOperatorId());
        rec.setOperatorName(bo.getOperatorName());
        rec.setCheckoutTime(new Date());
        rec.setUsageCount(bo.getUsageCount() == null ? 1 : bo.getUsageCount());
        rec.setUsageHours(bo.getUsageHours());
        rec.setStatus(bo.getStatus() == null ? "OUT" : bo.getStatus());
        rec.setRemark(bo.getRemark());
        int rows = baseMapper.insert(rec);
        log.info("新增工装使用记录: toolingId={}", bo.getToolingId());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TgUsageRecordBo bo) {
        TgUsageRecord rec = baseMapper.selectById(bo.getUsageId());
        if (rec == null) {
            throw new RuntimeException("使用记录不存在: " + bo.getUsageId());
        }
        rec.setWoId(bo.getWoId());
        rec.setWoNo(bo.getWoNo());
        rec.setWorkstationId(bo.getWorkstationId());
        rec.setWorkstationName(bo.getWorkstationName());
        rec.setOperatorId(bo.getOperatorId());
        rec.setOperatorName(bo.getOperatorName());
        rec.setUsageCount(bo.getUsageCount());
        rec.setUsageHours(bo.getUsageHours());
        rec.setStatus(bo.getStatus());
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

    private LambdaQueryWrapper<TgUsageRecord> buildQueryWrapper(TgUsageRecordBo bo) {
        return Wrappers.<TgUsageRecord>lambdaQuery()
            .eq(bo.getToolingId() != null, TgUsageRecord::getToolingId, bo.getToolingId())
            .eq(bo.getWoId() != null, TgUsageRecord::getWoId, bo.getWoId())
            .eq(bo.getOperatorId() != null, TgUsageRecord::getOperatorId, bo.getOperatorId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), TgUsageRecord::getStatus, bo.getStatus())
            .orderByDesc(TgUsageRecord::getCheckoutTime);
    }
}

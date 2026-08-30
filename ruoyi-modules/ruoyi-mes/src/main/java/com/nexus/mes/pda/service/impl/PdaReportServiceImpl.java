package com.nexus.mes.pda.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.domain.bo.PdaReportCompleteBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaDispatchItemVo;
import com.nexus.mes.pda.service.IPdaReportService;
import com.nexus.mes.pro.domain.ProDispatch;
import com.nexus.mes.pro.mapper.ProDispatchMapper;
import com.nexus.mes.pro.service.IProDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PDA 扫码报工实现 — 编排既有派工服务
 * <p>
 * 派工单即报工载体: 开工 startDispatch(PENDING→IN_PROGRESS),
 * 完工 completeDispatch(数量校验+CAS+回写工序/工单)。
 */
@Slf4j
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaReportServiceImpl implements IPdaReportService {

    private final IProDispatchService dispatchService;
    private final ProDispatchMapper dispatchMapper;

    @Override
    public TableDataInfo<PdaDispatchItemVo> todo(String keyword, PageQuery pageQuery) {
        Page<ProDispatch> page = dispatchMapper.selectPage(pageQuery.build(),
            Wrappers.<ProDispatch>lambdaQuery()
                .in(ProDispatch::getDispatchStatus, Arrays.asList(
                    IProDispatchService.STATUS_PENDING, IProDispatchService.STATUS_IN_PROGRESS))
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(ProDispatch::getWoNo, keyword)
                    .or().like(ProDispatch::getDispatchNo, keyword))
                .orderByAsc(ProDispatch::getDispatchStatus)
                .orderByDesc(ProDispatch::getCreateTime));
        List<PdaDispatchItemVo> items = new ArrayList<>(page.getRecords().size());
        for (ProDispatch d : page.getRecords()) {
            items.add(toVo(d));
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaDispatchItemVo detail(Long dispatchId) {
        ProDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            throw new ServiceException("派工单不存在");
        }
        return toVo(d);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo start(Long dispatchId) {
        ProDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            throw new ServiceException("派工单不存在");
        }
        if (IProDispatchService.STATUS_IN_PROGRESS.equals(d.getDispatchStatus())) {
            return MesActionResultVo.ok("已在进行中"); // 幂等
        }
        if (!IProDispatchService.STATUS_PENDING.equals(d.getDispatchStatus())) {
            return MesActionResultVo.fail("当前状态不允许开工: " + d.getDispatchStatus());
        }
        try {
            dispatchService.startDispatch(dispatchId);
        } catch (RuntimeException e) {
            log.warn("[PDA报工] 开工失败: {}", e.getMessage());
            return MesActionResultVo.fail(e.getMessage());
        }
        return MesActionResultVo.ok("已开工");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo complete(Long dispatchId, PdaReportCompleteBo bo) {
        ProDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            throw new ServiceException("派工单不存在");
        }
        if (IProDispatchService.STATUS_COMPLETED.equals(d.getDispatchStatus())) {
            return MesActionResultVo.ok("已完成"); // 幂等
        }
        if (!IProDispatchService.STATUS_IN_PROGRESS.equals(d.getDispatchStatus())) {
            return MesActionResultVo.fail("请先开工后再报工");
        }
        try {
            dispatchService.completeDispatch(dispatchId,
                bo.getGoodQty(),
                bo.getDefectQty() == null ? BigDecimal.ZERO : bo.getDefectQty(),
                bo.getScrapQty() == null ? BigDecimal.ZERO : bo.getScrapQty());
        } catch (RuntimeException e) {
            log.warn("[PDA报工] 完工失败: {}", e.getMessage());
            return MesActionResultVo.fail(e.getMessage());
        }
        return MesActionResultVo.ok("报工完成");
    }

    private PdaDispatchItemVo toVo(ProDispatch d) {
        PdaDispatchItemVo vo = new PdaDispatchItemVo();
        vo.setDispatchId(d.getDispatchId());
        vo.setDispatchNo(d.getDispatchNo());
        vo.setWoNo(d.getWoNo());
        vo.setProcessCode(d.getProcessCode());
        vo.setProcessName(d.getProcessName());
        vo.setWorkcenterCode(d.getWorkcenterCode());
        vo.setOperatorName(d.getOperatorName());
        vo.setPlannedQty(d.getPlannedQty());
        vo.setCompletedQty(d.getCompletedQty());
        vo.setDispatchStatus(d.getDispatchStatus());
        return vo;
    }
}

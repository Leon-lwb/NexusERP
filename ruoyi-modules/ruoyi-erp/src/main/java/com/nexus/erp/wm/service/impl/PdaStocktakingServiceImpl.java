package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.domain.bo.pda.PdaCountBo;
import com.nexus.erp.wm.domain.vo.WmStockTakingPlanVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaStocktakingDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.mapper.WmStockTakingLineMapper;
import com.nexus.erp.wm.service.IPdaStocktakingService;
import com.nexus.erp.wm.service.IWmStockTakingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PDA 盘点作业实现 — 编排既有盘点服务
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaStocktakingServiceImpl implements IPdaStocktakingService {

    private final IWmStockTakingPlanService planService;
    private final WmStockTakingLineMapper lineMapper;

    @Override
    public TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery) {
        WmStockTakingPlanBo bo = new WmStockTakingPlanBo();
        if (StringUtils.isNotBlank(keyword)) {
            bo.setPlanCode(keyword);
        }
        TableDataInfo<WmStockTakingPlanVo> page = planService.selectPageList(bo, pageQuery);
        List<PdaTodoItemVo> items = new ArrayList<>(page.getRows().size());
        for (WmStockTakingPlanVo vo : page.getRows()) {
            PdaTodoItemVo item = new PdaTodoItemVo();
            item.setId(vo.getPlanId());
            item.setCode(vo.getPlanCode());
            item.setStatus(vo.getStatus());
            item.setName(vo.getPlanName());
            item.setWarehouseName(vo.getWarehouseName());
            item.setDate(vo.getPlannedDate() != null ? DateUtil.formatDate(vo.getPlannedDate()) : null);
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaStocktakingDetailVo detail(Long planId) {
        WmStockTakingPlanVo head = planService.selectById(planId);
        if (head == null) {
            throw new ServiceException("盘点单不存在");
        }
        PdaStocktakingDetailVo vo = new PdaStocktakingDetailVo();
        vo.setPlanId(head.getPlanId());
        vo.setPlanCode(head.getPlanCode());
        vo.setPlanName(head.getPlanName());
        vo.setStatus(head.getStatus());
        vo.setWarehouseName(head.getWarehouseName());

        List<WmStockTakingLine> lines = lineMapper.selectList(
            Wrappers.<WmStockTakingLine>lambdaQuery().eq(WmStockTakingLine::getPlanId, planId));
        List<PdaStocktakingDetailVo.PdaStocktakingLineVo> lineVos = new ArrayList<>(lines.size());
        for (WmStockTakingLine line : lines) {
            PdaStocktakingDetailVo.PdaStocktakingLineVo lv = new PdaStocktakingDetailVo.PdaStocktakingLineVo();
            lv.setLineId(line.getLineId());
            lv.setMaterialId(line.getMaterialId());
            lv.setMaterialCode(line.getMaterialCode());
            lv.setMaterialName(line.getMaterialName());
            lv.setMaterialSpec(line.getMaterialSpec());
            lv.setBaseUomName(line.getBaseUomName());
            lv.setBatchCode(line.getBatchCode());
            lv.setLocationCode(line.getLocationCode());
            lv.setBookQty(line.getBookQuantity());
            lv.setCountedQty(line.getActualQuantity());
            lv.setLineStatus(line.getStatus());
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
    }

    @Override
    public PdaActionResultVo generate(Long planId) {
        WmStockTakingPlanVo head = planService.selectById(planId);
        if (head == null) {
            throw new ServiceException("盘点单不存在");
        }
        if (!IWmStockTakingPlanService.STATUS_DRAFT.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许生成明细");
        }
        planService.generateLines(planId);
        return PdaActionResultVo.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo count(Long planId, PdaCountBo bo) {
        WmStockTakingPlanVo head = planService.selectById(planId);
        if (head == null) {
            throw new ServiceException("盘点单不存在");
        }
        if (!IWmStockTakingPlanService.STATUS_GENERATED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许录入实盘数");
        }
        WmStockTakingLine line = lineMapper.selectOne(Wrappers.<WmStockTakingLine>lambdaQuery()
            .eq(WmStockTakingLine::getLineId, bo.getLineId())
            .eq(WmStockTakingLine::getPlanId, planId));
        if (line == null) {
            return PdaActionResultVo.fail("盘点行不存在");
        }
        line.setActualQuantity(bo.getCountedQty());
        BigDecimal book = line.getBookQuantity() == null ? BigDecimal.ZERO : line.getBookQuantity();
        line.setDifferenceQuantity(bo.getCountedQty().subtract(book));
        lineMapper.updateById(line);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_SCAN_MATERIAL);
    }

    @Override
    public PdaActionResultVo post(Long planId) {
        WmStockTakingPlanVo head = planService.selectById(planId);
        if (head == null) {
            throw new ServiceException("盘点单不存在");
        }
        if (!IWmStockTakingPlanService.STATUS_GENERATED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许过账");
        }
        planService.postAdjustment(planId);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }
}

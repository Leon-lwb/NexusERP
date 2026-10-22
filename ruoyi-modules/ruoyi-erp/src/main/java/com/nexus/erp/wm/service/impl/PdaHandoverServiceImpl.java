package com.nexus.erp.wm.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.wm.domain.WmMaterialHandover;
import com.nexus.erp.wm.domain.WmMaterialHandoverLine;
import com.nexus.erp.wm.domain.WmMiscIssueLine;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.pda.PdaHandoverConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaHandoverDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.mapper.WmMaterialHandoverLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialHandoverMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IPdaHandoverService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * PDA 上料交接作业实现
 * <p>
 * 流程: PC 建单(DRAFT, 来源工单+源库位→线边库位) → 材料员 PDA 行级核销(累计实交)
 * → 产线操作工 PDA 扫单签收 → 同事务逐行过账(源库位 postMiscIssue 出 + 线边库位 postMiscRecpt 入)。
 * 普通数量策略与严格单件路径均保留真实交接单来源，禁止伪装成不存在的杂项单。
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaHandoverServiceImpl implements IPdaHandoverService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    private final WmMaterialHandoverMapper handoverMapper;
    private final WmMaterialHandoverLineMapper lineMapper;
    private final IWmStockOperationService stockOperationService;
    private final WmStorageLocationMapper locationMapper;
    private final WmWarehouseMapper warehouseMapper;
  
    @Override
    public TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery) {
        Page<WmMaterialHandover> page = handoverMapper.selectPage(pageQuery.build(),
            Wrappers.<WmMaterialHandover>lambdaQuery()
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(WmMaterialHandover::getHandoverNo, keyword)
                    .or()
                    .like(WmMaterialHandover::getWoNo, keyword))
                .orderByDesc(WmMaterialHandover::getCreateTime));
        List<PdaTodoItemVo> items = new ArrayList<>(page.getRecords().size());
        for (WmMaterialHandover h : page.getRecords()) {
            PdaTodoItemVo item = new PdaTodoItemVo();
            item.setId(h.getHandoverId());
            item.setCode(h.getHandoverNo());
            item.setStatus(h.getStatus());
            item.setName("上料交接"
                + (StringUtils.isNotBlank(h.getWoNo()) ? " · " + h.getWoNo() : ""));
            item.setWarehouseName(h.getWarehouseName());
            item.setDate(h.getHandoverTime() != null ? DateUtil.formatDate(h.getHandoverTime()) : null);
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaHandoverDetailVo detail(Long handoverId) {
        WmMaterialHandover head = headOrThrow(handoverId);
        PdaHandoverDetailVo vo = new PdaHandoverDetailVo();
        vo.setHandoverId(head.getHandoverId());
        vo.setHandoverNo(head.getHandoverNo());
        vo.setWoNo(head.getWoNo());
        vo.setStatus(head.getStatus());
        vo.setWarehouseName(head.getWarehouseName());
        vo.setHandoverTime(head.getHandoverTime() != null ? DateUtil.formatDate(head.getHandoverTime()) : null);
        vo.setSignName(head.getSignName());
        vo.setSignTime(head.getSignTime() != null ? DateUtil.formatDate(head.getSignTime()) : null);

        List<WmMaterialHandoverLine> lines = linesOf(handoverId);
        List<PdaHandoverDetailVo.PdaHandoverLineVo> lineVos = new ArrayList<>(lines.size());
        for (WmMaterialHandoverLine line : lines) {
            PdaHandoverDetailVo.PdaHandoverLineVo lv = new PdaHandoverDetailVo.PdaHandoverLineVo();
            lv.setLineId(line.getLineId());
            lv.setMaterialCode(line.getMaterialCode());
            lv.setMaterialName(line.getMaterialName());
            lv.setMaterialSpec(line.getMaterialSpec());
            lv.setBaseUomName(line.getBaseUomName());
            lv.setBatchCode(line.getBatchCode());
            lv.setFromLocationCode(line.getFromLocationCode());
            lv.setToLocationCode(line.getToLocationCode());
            lv.setPlanQty(line.getQuantity());
            lv.setActualQty(line.getActualQty() == null ? BigDecimal.ZERO : line.getActualQty());
            lineVos.add(lv);
        }
        vo.setLines(lineVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo confirmLine(Long handoverId, PdaHandoverConfirmBo bo) {
        WmMaterialHandover head = headForUpdate(handoverId);
        if (WmMaterialHandover.STATUS_SIGNED.equals(head.getStatus())) {
            return PdaActionResultVo.fail("交接单已签收");
        }
        WmMaterialHandoverLine line = lineMapper.selectOne(Wrappers.<WmMaterialHandoverLine>lambdaQuery()
            .eq(WmMaterialHandoverLine::getLineId, bo.getLineId())
            .eq(WmMaterialHandoverLine::getHandoverId, handoverId));
        if (line == null) {
            return PdaActionResultVo.fail("行不属于本交接单");
        }
        if (!WmMaterialHandover.STATUS_DRAFT.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许核销");
        }
        if (bo.getQty() == null || bo.getQty().signum() <= 0 || bo.getQty().stripTrailingZeros().scale() > 4) {
            return PdaActionResultVo.fail("核销数量必须大于0且最多保留4位小数");
        }
        BigDecimal actual = line.getActualQty() == null ? BigDecimal.ZERO : line.getActualQty();
        BigDecimal plan = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        if (plan.signum() <= 0 || actual.signum() < 0) {
            return PdaActionResultVo.fail("计划数量或累计数量异常，请先更正单据");
        }
        if (actual.add(bo.getQty()).compareTo(plan) > 0) {
            return PdaActionResultVo.fail("累计实交超过计划数量(" + plan + ")");
        }
        line.setActualQty(actual.add(bo.getQty()));
        if (StringUtils.isNotBlank(bo.getBatchCode())) {
            line.setBatchCode(bo.getBatchCode());
        }
        lineMapper.updateById(line);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_SCAN_MATERIAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdaActionResultVo sign(Long handoverId) {
        WmMaterialHandover head = headForUpdate(handoverId);
        if (WmMaterialHandover.STATUS_SIGNED.equals(head.getStatus())) {
            return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED); // 幂等
        }
        if (!WmMaterialHandover.STATUS_DRAFT.equals(head.getStatus())) {
            return PdaActionResultVo.fail("单据当前状态不允许签收");
        }
        List<WmMaterialHandoverLine> lines = linesOf(handoverId);
        for (WmMaterialHandoverLine current : lines) {
            BigDecimal planned = current.getQuantity();
            BigDecimal actual = current.getActualQty();
            if (planned == null || planned.signum() <= 0
                    || (actual != null && (actual.signum() < 0 || actual.compareTo(planned) > 0))) {
                return PdaActionResultVo.fail("明细数量异常，请先更正单据");
            }
        }
        boolean anyActual = lines.stream()
            .anyMatch(l -> l.getActualQty() != null && l.getActualQty().compareTo(BigDecimal.ZERO) > 0);
        if (!anyActual) {
            return PdaActionResultVo.fail("无实交记录，请先扫码核销");
        }

        if (head.getWarehouseId() == null) return PdaActionResultVo.fail("交接单缺少来源仓库");
        Date now = new Date();
        for (WmMaterialHandoverLine line : lines) {
            BigDecimal qty = line.getActualQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 未核销行留痕不阻塞签收(部分交接)
            }
            if (serialInventory != null && serialInventory.postSource("WM_MATERIAL_HANDOVER", handoverId, line.getLineId())) {
                continue;
            }
            // 出库保留真实交接来源，并限定原单头的来源仓库。
            WmMiscIssueLine out = new WmMiscIssueLine();
            out.setPostingSourceType("WM_MATERIAL_HANDOVER");
            out.setWarehouseId(head.getWarehouseId());
            out.setIssueId(head.getHandoverId());
            out.setLineId(line.getLineId());
            out.setMaterialId(line.getMaterialId());
            out.setMaterialCode(line.getMaterialCode());
            out.setMaterialName(line.getMaterialName());
            out.setMaterialSpec(line.getMaterialSpec());
            out.setBaseUomId(line.getBaseUomId());
            out.setBaseUomName(line.getBaseUomName());
            out.setBatchId(line.getBatchId());
            out.setBatchCode(line.getBatchCode());
            out.setLocationId(line.getFromLocationId());
            out.setLocationCode(line.getFromLocationCode());
            out.setLocationName(line.getFromLocationName());
            out.setQuantity(qty);
            stockOperationService.postMiscIssue(out, now);

            // 入: 线边库位 (杂项入库通道, 线边库位所在仓库作为头包装)
            WmStorageLocation toLoc = line.getToLocationId() != null
                ? locationMapper.selectById(line.getToLocationId())
                : locationMapper.selectOne(Wrappers.<WmStorageLocation>lambdaQuery()
                    .eq(WmStorageLocation::getLocationCode, line.getToLocationCode()));
            if (toLoc == null) {
                throw new ServiceException("线边库位不存在: " + line.getToLocationCode());
            }
            WmWarehouse toWarehouse = warehouseMapper.selectById(toLoc.getWarehouseId());
            WmMiscRecpt wrapper = new WmMiscRecpt();
            wrapper.setRecptId(head.getHandoverId());
            wrapper.setWarehouseId(toLoc.getWarehouseId());
            wrapper.setAreaId(toLoc.getAreaId());
            wrapper.setWarehouseCode(toWarehouse != null ? toWarehouse.getWarehouseCode() : null);
            wrapper.setWarehouseName(toWarehouse != null ? toWarehouse.getWarehouseName() : null);

            WmMiscRecptLine in = new WmMiscRecptLine();
            in.setPostingSourceType("WM_MATERIAL_HANDOVER");
            in.setRecptId(head.getHandoverId());
            in.setLineId(line.getLineId());
            in.setMaterialId(line.getMaterialId());
            in.setMaterialCode(line.getMaterialCode());
            in.setMaterialName(line.getMaterialName());
            in.setMaterialSpec(line.getMaterialSpec());
            in.setBaseUomId(line.getBaseUomId());
            in.setBaseUomName(line.getBaseUomName());
            in.setBatchId(line.getBatchId());
            in.setBatchCode(line.getBatchCode());
            in.setLocationId(toLoc.getLocationId());
            in.setLocationCode(toLoc.getLocationCode());
            in.setLocationName(toLoc.getLocationName());
            in.setQuantity(qty);
            stockOperationService.postMiscRecpt(in, wrapper, now);
        }

        head.setStatus(WmMaterialHandover.STATUS_SIGNED);
        head.setSignBy(LoginHelper.getUserId());
        head.setSignName(LoginHelper.getUsername());
        head.setSignTime(now);
        handoverMapper.updateById(head);
        return PdaActionResultVo.ok(PdaActionResultVo.NEXT_FINISHED);
    }

    /** 核销与完成共用单据行锁，跨用户、跨幂等键也只能过账一次。 */
    private WmMaterialHandover headForUpdate(Long handoverId) {
        WmMaterialHandover head = handoverMapper.selectOne(Wrappers.<WmMaterialHandover>lambdaQuery()
            .eq(WmMaterialHandover::getHandoverId, handoverId).last("FOR UPDATE"));
        if (head == null) {
            throw new ServiceException("交接单不存在");
        }
        return head;
    }

    private WmMaterialHandover headOrThrow(Long handoverId) {
        WmMaterialHandover head = handoverMapper.selectById(handoverId);
        if (head == null) {
            throw new ServiceException("交接单不存在");
        }
        return head;
    }

    private List<WmMaterialHandoverLine> linesOf(Long handoverId) {
        return lineMapper.selectList(
            Wrappers.<WmMaterialHandoverLine>lambdaQuery().eq(WmMaterialHandoverLine::getHandoverId, handoverId));
    }
}

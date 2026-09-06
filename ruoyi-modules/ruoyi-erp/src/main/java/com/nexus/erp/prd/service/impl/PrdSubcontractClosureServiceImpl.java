package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.SubcontractSnGenerateMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.domain.PrdSubcontractBomLine;
import com.nexus.erp.prd.domain.PrdSubcontractDoc;
import com.nexus.erp.prd.domain.PrdSubcontractQuote;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSubcontractDocBo;
import com.nexus.erp.prd.domain.bo.PrdSubcontractQuoteBo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractBomLineVo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractDocVo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractQuoteVo;
import com.nexus.erp.prd.mapper.PrdSubcontractBomLineMapper;
import com.nexus.erp.prd.mapper.PrdSubcontractDocMapper;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.prd.mapper.PrdSubcontractQuoteMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdSubcontractClosureService;
import com.nexus.erp.prd.service.IPrdSubcontractService;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外订单(PCBA)业务闭环 Service 实现
 * <p>
 * 数据源: ERP 业务表走 {@code @DS("erp")}。快照/报价/工程文件/发料推进/收货闭环都在此编排,
 * 库存过账由 wm 模块策略(WmSubcontractIssue/ReceiveService -> StockOperationStrategyFactory)完成。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdSubcontractClosureServiceImpl implements IPrdSubcontractClosureService {

    private final PrdSubcontractMapper subcontractMapper;
    private final PrdSubcontractBomLineMapper bomLineMapper;
    private final PrdSubcontractQuoteMapper quoteMapper;
    private final PrdSubcontractDocMapper docMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final BdBomMapper bomMapper;
    private final BdBomDetailMapper bomDetailMapper;
    private final BdRoutingStepMapper routingStepMapper;
    private final BdMaterialMapper materialMapper;
    private final BdUnitOfMeasureMapper uomMapper;
    private final WmWarehouseMapper warehouseMapper;
    private final ReliableMessageProducer messageProducer;

    @Override
    public Map<String, Object> getMaterialInfo(Long materialId) {
        BdMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new ServiceException("物料不存在: " + materialId);
        }
        Map<String, Object> result = new LinkedHashMap<>(8);
        result.put("material", material);

        // 当前生效 BOM(isCurrent=1) + 行(含供应方标注)
        BdBom bom = bomMapper.selectOne(Wrappers.<BdBom>lambdaQuery()
            .eq(BdBom::getParentMaterialId, materialId)
            .eq(BdBom::getIsCurrent, 1L)
            .orderByDesc(BdBom::getBomId)
            .last("LIMIT 1"));
        result.put("bom", bom);
        if (bom != null) {
            result.put("bomLines", bomDetailMapper.selectList(Wrappers.<BdBomDetail>lambdaQuery()
                .eq(BdBomDetail::getBomId, bom.getBomId())
                .orderByAsc(BdBomDetail::getSortOrder)));
        } else {
            result.put("bomLines", List.of());
        }

        // 工艺路线(取 BOM 关联路线)的委外工序
        if (bom != null && bom.getRoutingId() != null) {
            result.put("routingSteps", routingStepMapper.selectList(Wrappers.<BdRoutingStep>lambdaQuery()
                .eq(BdRoutingStep::getRoutingId, bom.getRoutingId())
                .eq(BdRoutingStep::getIsOutsource, 1)
                .orderByAsc(BdRoutingStep::getSequenceNum)));
            result.put("routingId", bom.getRoutingId());
        } else {
            result.put("routingSteps", List.of());
            result.put("routingId", null);
        }

        // 物料级工程文件库(未挂订单的)
        result.put("docs", docMapper.selectList(Wrappers.<PrdSubcontractDoc>lambdaQuery()
            .eq(PrdSubcontractDoc::getMaterialId, materialId)
            .isNull(PrdSubcontractDoc::getSubcontractId)));
        return result;
    }

    @Override
    public List<PrdSubcontractBomLineVo> getBomLines(Long subcontractId) {
        return bomLineMapper.selectVoList(Wrappers.<PrdSubcontractBomLine>lambdaQuery()
            .eq(PrdSubcontractBomLine::getSubcontractId, subcontractId)
            .orderByAsc(PrdSubcontractBomLine::getLineId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateBomSnapshot(Long subcontractId) {
        PrdSubcontract subcontract = subcontractMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (!IPrdSubcontractService.STATUS_DRAFT.equals(subcontract.getStatus())) {
            throw new ServiceException("仅草稿状态可重建BOM快照, 当前状态: " + subcontract.getStatus());
        }
        if (subcontract.getBomId() == null) {
            throw new ServiceException("订单未关联BOM, 无法生成快照");
        }
        BdBom bom = bomMapper.selectById(subcontract.getBomId());
        if (bom == null) {
            throw new ServiceException("关联BOM不存在: " + subcontract.getBomId());
        }
        if (subcontract.getRoutingId() == null && bom.getRoutingId() != null) {
            // BOM 携带工艺路线时回填订单快照冗余
            subcontract.setRoutingId(bom.getRoutingId());
            subcontractMapper.updateById(subcontract);
        }
        List<BdBomDetail> details = bomDetailMapper.selectList(Wrappers.<BdBomDetail>lambdaQuery()
            .eq(BdBomDetail::getBomId, bom.getBomId())
            .orderByAsc(BdBomDetail::getSortOrder));
        if (details.isEmpty()) {
            throw new ServiceException("BOM[" + bom.getBomName() + "]无明细行, 无法生成快照");
        }

        // 重建: 删旧快照(逻辑删)后按当前 BOM 全量快照
        bomLineMapper.delete(Wrappers.<PrdSubcontractBomLine>lambdaQuery()
            .eq(PrdSubcontractBomLine::getSubcontractId, subcontractId));

        BigDecimal plannedQty = subcontract.getPlannedQty() == null ? BigDecimal.ZERO : subcontract.getPlannedQty();
        List<PrdSubcontractBomLine> lines = new ArrayList<>(details.size());
        for (BdBomDetail d : details) {
            PrdSubcontractBomLine line = new PrdSubcontractBomLine();
            line.setSubcontractId(subcontractId);
            line.setBomDetailId(d.getDetailId());
            line.setMaterialId(d.getChildMaterialId());
            fillMaterialInfo(line);
            BigDecimal qtyPer = d.getQuantityPerParent() == null ? BigDecimal.ZERO : d.getQuantityPerParent();
            BigDecimal lossRate = d.getLossRate() == null ? BigDecimal.ZERO : d.getLossRate();
            line.setQuantityPer(qtyPer);
            line.setLossRate(lossRate);
            // 需求量 = 计划数 × 单耗 × (1 + 损耗率%)
            line.setDemandQty(plannedQty.multiply(qtyPer)
                .multiply(BigDecimal.ONE.add(lossRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP));
            line.setIssuedQty(BigDecimal.ZERO);
            line.setSupplyType(StringUtils.isBlank(d.getSupplyType()) ? SUPPLY_SELF : d.getSupplyType());
            line.setRemark(d.getRemark());
            lines.add(line);
        }
        lines.forEach(bomLineMapper::insert);
        log.info("委外订单[{}]BOM快照生成完成, 行数={}", subcontract.getSubcontractNo(), lines.size());
        return lines.size();
    }

    private void fillMaterialInfo(PrdSubcontractBomLine line) {
        BdMaterial m = materialMapper.selectById(line.getMaterialId());
        if (m == null) {
            throw new ServiceException("BOM子件物料不存在: " + line.getMaterialId());
        }
        line.setMaterialCode(m.getMaterialCode());
        line.setMaterialName(m.getMaterialName());
        line.setMaterialSpec(m.getMaterialSpec());
        if (m.getBaseUomId() != null) {
            BdUnitOfMeasure uom = uomMapper.selectById(m.getBaseUomId());
            line.setBaseUomName(uom != null ? uom.getUomName() : null);
        }
    }

    @Override
    public List<PrdSubcontractQuoteVo> listQuotes(Long subcontractId) {
        return quoteMapper.selectVoList(Wrappers.<PrdSubcontractQuote>lambdaQuery()
            .eq(PrdSubcontractQuote::getSubcontractId, subcontractId)
            .orderByDesc(PrdSubcontractQuote::getQuoteTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveQuote(PrdSubcontractQuoteBo bo) {
        PrdSubcontractQuote quote = MapstructUtils.convert(bo, PrdSubcontractQuote.class);
        if (quote.getSubcontractId() == null) {
            throw new ServiceException("报价必须挂靠委外订单");
        }
        if (quote.getQuoteId() == null) {
            quote.setQuoteStatus(StringUtils.isBlank(quote.getQuoteStatus())
                ? "PENDING" : quote.getQuoteStatus());
            quote.setQuoteTime(new Date());
            quoteMapper.insert(quote);
        } else {
            PrdSubcontractQuote existing = quoteMapper.selectById(quote.getQuoteId());
            if (existing == null) {
                throw new ServiceException("报价记录不存在");
            }
            if ("SELECTED".equals(existing.getQuoteStatus())) {
                throw new ServiceException("已选定报价不可编辑");
            }
            quoteMapper.updateById(quote);
        }
        return quote.getQuoteId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteQuotes(Long[] quoteIds) {
        for (Long id : quoteIds) {
            PrdSubcontractQuote q = quoteMapper.selectById(id);
            if (q != null && "SELECTED".equals(q.getQuoteStatus())) {
                throw new ServiceException("已选定报价不可删除: " + q.getVendorName());
            }
        }
        return quoteMapper.deleteByIds(List.of(quoteIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void selectQuote(Long quoteId) {
        PrdSubcontractQuote quote = quoteMapper.selectById(quoteId);
        if (quote == null) {
            throw new ServiceException("报价记录不存在");
        }
        PrdSubcontract subcontract = subcontractMapper.selectById(quote.getSubcontractId());
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (IPrdSubcontractService.STATUS_RELEASED.equals(subcontract.getStatus())
            || IPrdSubcontractService.STATUS_IN_PROGRESS.equals(subcontract.getStatus())
            || IPrdSubcontractService.STATUS_COMPLETED.equals(subcontract.getStatus())
            || IPrdSubcontractService.STATUS_CLOSED.equals(subcontract.getStatus())) {
            throw new ServiceException("订单已下达/完工, 不可重新选定供应商");
        }
        // 本单其余报价置未选中
        quoteMapper.update(null, Wrappers.<PrdSubcontractQuote>lambdaUpdate()
            .set(PrdSubcontractQuote::getQuoteStatus, "NOT_SELECTED")
            .eq(PrdSubcontractQuote::getSubcontractId, quote.getSubcontractId())
            .eq(PrdSubcontractQuote::getQuoteStatus, "PENDING")
            .ne(PrdSubcontractQuote::getQuoteId, quoteId));
        quote.setQuoteStatus("SELECTED");
        quoteMapper.updateById(quote);
        // 回写订单供应商与价格
        subcontract.setVendorId(quote.getVendorId());
        subcontract.setVendorCode(quote.getVendorCode());
        subcontract.setVendorName(quote.getVendorName());
        subcontract.setUnitPrice(quote.getUnitPrice());
        if (subcontract.getPlannedQty() != null && quote.getUnitPrice() != null) {
            subcontract.setTotalAmount(subcontract.getPlannedQty().multiply(quote.getUnitPrice()));
        }
        subcontractMapper.updateById(subcontract);
    }

    @Override
    public List<PrdSubcontractDocVo> listDocs(Long materialId, Long subcontractId) {
        return docMapper.selectVoList(Wrappers.<PrdSubcontractDoc>lambdaQuery()
            .eq(materialId != null, PrdSubcontractDoc::getMaterialId, materialId)
            .eq(subcontractId != null, PrdSubcontractDoc::getSubcontractId, subcontractId)
            .isNull(subcontractId == null, PrdSubcontractDoc::getSubcontractId)
            .orderByDesc(PrdSubcontractDoc::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDoc(PrdSubcontractDocBo bo) {
        if (bo.getFileOssId() == null) {
            throw new ServiceException("工程文件必须先上传 OSS 取得文件ID");
        }
        PrdSubcontractDoc doc = MapstructUtils.convert(bo, PrdSubcontractDoc.class);
        if (doc.getMaterialId() == null) {
            throw new ServiceException("工程文件必须关联物料");
        }
        if (doc.getDocId() == null) {
            docMapper.insert(doc);
        } else {
            docMapper.updateById(doc);
        }
        return doc.getDocId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocs(Long[] docIds) {
        return docMapper.deleteByIds(List.of(docIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceToReleased(Long subcontractId) {
        PrdSubcontract subcontract = subcontractMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        // 已下达/进行中/完工 直接返回(幂等, 部分发料单可能多次触发)
        if (!IPrdSubcontractService.STATUS_DRAFT.equals(subcontract.getStatus())
            && !IPrdSubcontractService.STATUS_SUBMITTED.equals(subcontract.getStatus())
            && !IPrdSubcontractService.STATUS_APPROVED.equals(subcontract.getStatus())) {
            log.info("委外订单[{}]状态[{}]已推进, 跳过下达推进", subcontract.getSubcontractNo(), subcontract.getStatus());
            return;
        }
        subcontract.setStatus(IPrdSubcontractService.STATUS_RELEASED);
        subcontract.setActualStartDate(new Date());
        subcontractMapper.updateById(subcontract);

        // 自动创建关联生产工单(与 release() 的工单语义一致)
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoNo("WO-SC-" + subcontract.getSubcontractNo());
        wo.setMaterialId(subcontract.getMaterialId());
        wo.setMaterialCode(subcontract.getMaterialCode());
        wo.setMaterialName(subcontract.getMaterialName());
        wo.setMaterialSpec(subcontract.getMaterialSpec());
        wo.setPlannedQty(subcontract.getPlannedQty());
        wo.setUnitName(subcontract.getBaseUomName());
        wo.setPlanStartDate(subcontract.getPlanStartDate());
        wo.setPlanEndDate(subcontract.getPlanEndDate());
        wo.setWoStatus("RELEASED");
        wo.setSourceType("SUBCONTRACT");
        wo.setRemark("委外订单发料完成自动生成");
        workOrderMapper.insert(wo);
        subcontract.setWoId(wo.getWoId());
        subcontract.setWoNo(wo.getWoNo());
        subcontractMapper.updateById(subcontract);

        // 通知 MES 批量生成 PCBA SN(数量=计划量-已生成量)
        BigDecimal planned = subcontract.getPlannedQty() == null ? BigDecimal.ZERO : subcontract.getPlannedQty();
        int generated = subcontract.getSnGeneratedQty() == null ? 0 : subcontract.getSnGeneratedQty();
        int toGenerate = planned.intValue() - generated;
        if (toGenerate > 0) {
            SubcontractSnGenerateMessage msg = new SubcontractSnGenerateMessage();
            msg.setSubcontractId(subcontractId);
            msg.setSubcontractNo(subcontract.getSubcontractNo());
            msg.setMaterialId(subcontract.getMaterialId());
            msg.setMaterialCode(subcontract.getMaterialCode());
            msg.setMaterialName(subcontract.getMaterialName());
            msg.setQuantity(BigDecimal.valueOf(toGenerate));
            msg.setSourceModule("erp");
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.MES_SUBCONTRACT_SN, msg);
        }
        log.info("委外订单[{}]发料完成推进: RELEASED, 工单[{}], SN待生成={}",
            subcontract.getSubcontractNo(), wo.getWoNo(), toGenerate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accumulateActualQty(Long subcontractId, BigDecimal receivedQty) {
        if (receivedQty == null || receivedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("收货数量必须大于0");
        }
        PrdSubcontract subcontract = subcontractMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        // 原子累加实际数量
        subcontractMapper.update(null, Wrappers.<PrdSubcontract>lambdaUpdate()
            .setSql("actual_qty = IFNULL(actual_qty, 0) + " + receivedQty.toPlainString())
            .eq(PrdSubcontract::getSubcontractId, subcontractId));
        // 重读判断是否达量自动完工
        PrdSubcontract latest = subcontractMapper.selectById(subcontractId);
        BigDecimal planned = latest.getPlannedQty() == null ? BigDecimal.ZERO : latest.getPlannedQty();
        BigDecimal actual = latest.getActualQty() == null ? BigDecimal.ZERO : latest.getActualQty();
        if (actual.compareTo(planned) >= 0
            && !IPrdSubcontractService.STATUS_COMPLETED.equals(latest.getStatus())
            && !IPrdSubcontractService.STATUS_CLOSED.equals(latest.getStatus())) {
            subcontractMapper.update(null, Wrappers.<PrdSubcontract>lambdaUpdate()
                .set(PrdSubcontract::getStatus, IPrdSubcontractService.STATUS_COMPLETED)
                .set(PrdSubcontract::getActualEndDate, new Date())
                .eq(PrdSubcontract::getSubcontractId, subcontractId)
                .in(PrdSubcontract::getStatus, IPrdSubcontractService.STATUS_RELEASED,
                    IPrdSubcontractService.STATUS_IN_PROGRESS));
            log.info("委外订单[{}]收货量[{}]达计划量[{}], 自动完工",
                latest.getSubcontractNo(), actual.stripTrailingZeros().toPlainString(),
                planned.stripTrailingZeros().toPlainString());
        }
    }

    /** 供 wm 收货服务校验目标仓默认值使用(半成品仓路由) */
    public Long resolveDefaultTargetWarehouse(Long materialId) {
        BdMaterial material = materialMapper.selectById(materialId);
        if (material != null && material.getMaterialType() != null && material.getMaterialType() == 2L) {
            List<WmWarehouse> warehouses = warehouseMapper.selectList(Wrappers.<WmWarehouse>lambdaQuery()
                .orderByAsc(WmWarehouse::getWarehouseId)
                .last("LIMIT 5"));
            // 半成品物料优先取名称含"半成品"的仓库(一期命名约定路由, 二期 warehouse_type 字段)
            return warehouses.stream()
                .filter(w -> w.getWarehouseName() != null && w.getWarehouseName().contains("半成品"))
                .map(WmWarehouse::getWarehouseId)
                .findFirst()
                .orElse(warehouses.isEmpty() ? null : warehouses.get(0).getWarehouseId());
        }
        return null;
    }
}

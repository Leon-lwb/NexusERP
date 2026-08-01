package com.nexus.erp.pu.invoice.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.vo.BdVendorVo;
import com.nexus.erp.bd.service.IBdVendorService;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.vo.PuPurchaseOrderVo;
import com.nexus.erp.pu.invoice.domain.PuInvoice;
import com.nexus.erp.pu.invoice.domain.PuInvoiceLine;
import com.nexus.erp.pu.invoice.domain.bo.PuInvoiceBo;
import com.nexus.erp.pu.invoice.domain.vo.MatchResultVo;
import com.nexus.erp.pu.invoice.domain.vo.PuInvoiceVo;
import com.nexus.erp.pu.invoice.mapper.PuInvoiceLineMapper;
import com.nexus.erp.pu.invoice.mapper.PuInvoiceMapper;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.service.IPuPurchaseOrderService;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.invoice.event.PuInvoiceIssuedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购进项发票Service业务层处理
 * <p>镜像 {@code SoInvoiceServiceImpl},状态机含进项认证子状态机(cert_status)。
 * 进项发票收票登记无需内部审批流转,故无 Flowable 集成;DRAFT->ISSUED 为"收票确认"。</p>
 *
 * @author nexus
 * @date 2026-07-16
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puInvoiceService")
public class PuInvoiceServiceImpl implements IPuInvoiceService {

    private final PuInvoiceMapper baseMapper;
    private final PuInvoiceLineMapper lineMapper;
    private final IPuPurchaseOrderService puPurchaseOrderService;
    private final IBdVendorService bdVendorService;
    private final PuPurchaseOrderLineMapper puPurchaseOrderLineMapper;
    private final WmMaterialReceiveLineMapper wmMaterialReceiveLineMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 分页查询进项发票列表。
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 进项发票分页列表
     */
    @Override
    public TableDataInfo<PuInvoiceVo> selectPageList(PuInvoiceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuInvoice> lqw = buildQueryWrapper(bo);
        Page<PuInvoiceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询进项发票列表。
     *
     * @param bo 查询参数
     * @return 进项发票列表
     */
    @Override
    public List<PuInvoiceVo> selectList(PuInvoiceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询进项发票详情（含明细行）。
     *
     * @param id 发票ID
     * @return 进项发票详情
     */
    @Override
    public PuInvoiceVo selectById(Long id) {
        PuInvoiceVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<PuInvoiceLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(PuInvoiceLine::getInvoiceId, vo.getInvoiceId());
            lqw.orderByAsc(PuInvoiceLine::getSortOrder);
            vo.setPuInvoiceLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增进项发票(默认草稿/未认证),含明细行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuInvoiceBo bo) {
        PuInvoice entity = MapstructUtils.convert(bo, PuInvoice.class);
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (StringUtils.isBlank(entity.getCertStatus())) {
            entity.setCertStatus(CERT_UNCERTIFIED);
        }
        if (StringUtils.isBlank(entity.getIssueChannel())) {
            entity.setIssueChannel("MANUAL");
        }
        if (StringUtils.isBlank(entity.getRedFlag())) {
            entity.setRedFlag("0");
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        if (entity.getVerifiedAmount() == null) {
            entity.setVerifiedAmount(BigDecimal.ZERO);
        }
        if (entity.getDeductibleTaxAmount() == null) {
            entity.setDeductibleTaxAmount(BigDecimal.ZERO);
        }
        int rows = baseMapper.insert(entity);
        if (bo.getPuInvoiceLineList() != null && !bo.getPuInvoiceLineList().isEmpty()) {
            for (PuInvoiceLine line : bo.getPuInvoiceLineList()) {
                line.setInvoiceId(entity.getInvoiceId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改进项发票:头乐观锁 + 明细行差量更新,仅草稿可编辑。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuInvoiceBo bo) {
        PuInvoice existing = baseMapper.selectById(bo.getInvoiceId());
        if (existing == null) {
            throw new ServiceException("发票不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改发票,仅草稿可编辑", existing.getStatus());
        }
        // 显式拷贝可编辑字段(不覆盖 version/verified_amount/cert_*/original_invoice_id/red_flag/电票/status)
        existing.setInvoiceNo(bo.getInvoiceNo());
        existing.setInvoiceCode(bo.getInvoiceCode());
        existing.setInvoiceType(bo.getInvoiceType());
        existing.setVendorId(bo.getVendorId());
        existing.setVendorName(bo.getVendorName());
        existing.setSellerTaxId(bo.getSellerTaxId());
        existing.setSellerTitle(bo.getSellerTitle());
        existing.setSellerBankName(bo.getSellerBankName());
        existing.setSellerBankAccount(bo.getSellerBankAccount());
        existing.setSellerAddressPhone(bo.getSellerAddressPhone());
        existing.setPoId(bo.getPoId());
        existing.setContractId(bo.getContractId());
        existing.setInvoiceDate(bo.getInvoiceDate());
        existing.setCurrency(bo.getCurrency());
        existing.setExchangeRate(bo.getExchangeRate());
        existing.setAmount(bo.getAmount());
        existing.setTaxAmount(bo.getTaxAmount());
        existing.setTotalAmount(bo.getTotalAmount());
        if (StringUtils.isNotBlank(bo.getIssueChannel())) {
            existing.setIssueChannel(bo.getIssueChannel());
        }
        existing.setRemark(bo.getRemark());
        existing.setAttr1(bo.getAttr1());
        existing.setAttr2(bo.getAttr2());
        existing.setAttr3(bo.getAttr3());
        existing.setAttr4(bo.getAttr4());
        int rows = baseMapper.updateById(existing);
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,修改失败,请重试");
        }
        if (bo.getPuInvoiceLineList() != null) {
            diffUpdateLines(existing.getInvoiceId(), bo.getPuInvoiceLineList());
        }
        return rows;
    }

    /**
     * 明细行差量更新:按 lineId 区分新增/更新/删除,取代全删重插。
     */
    private void diffUpdateLines(Long invoiceId, List<PuInvoiceLine> newLines) {
        List<PuInvoiceLine> existingLines = lineMapper.selectList(
            Wrappers.<PuInvoiceLine>lambdaQuery().eq(PuInvoiceLine::getInvoiceId, invoiceId));
        Map<Long, PuInvoiceLine> existingMap = new HashMap<>();
        for (PuInvoiceLine l : existingLines) {
            if (l.getLineId() != null) {
                existingMap.put(l.getLineId(), l);
            }
        }
        Set<Long> keepIds = new HashSet<>();
        for (PuInvoiceLine line : newLines) {
            if (line.getLineId() == null) {
                line.setInvoiceId(invoiceId);
                lineMapper.insert(line);
            } else {
                line.setInvoiceId(invoiceId);
                lineMapper.updateById(line);
                keepIds.add(line.getLineId());
            }
        }
        for (PuInvoiceLine l : existingLines) {
            if (l.getLineId() != null && !keepIds.contains(l.getLineId())) {
                lineMapper.deleteById(l.getLineId());
            }
        }
    }

    /**
     * 批量删除进项发票（仅草稿可删，非草稿抛异常并回退已开票数量）。
     *
     * @param ids 发票ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            PuInvoice existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("发票【{}】当前状态不允许删除", existing.getInvoiceNo());
            }
            if (existing != null) {
                rollbackInvoicedQty(existing);
            }
            lineMapper.delete(Wrappers.lambdaQuery(PuInvoiceLine.class)
                .eq(PuInvoiceLine::getInvoiceId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据采购订单生成发票草稿(按"已收货未开票"自动汇总)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateDraftFromPo(Long poId) {
        PuPurchaseOrderVo poHeader = puPurchaseOrderService.selectById(poId);
        if (poHeader == null) {
            log.warn("生成进项发票草稿失败:采购订单不存在, poId={}", poId);
            return null;
        }
        List<PuPurchaseOrderLine> poLines = poHeader.getLineList();
        if (poLines == null || poLines.isEmpty()) {
            log.warn("生成进项发票草稿跳过:采购订单无行明细, poId={}", poId);
            return null;
        }

        BdVendorVo vendor = poHeader.getVendorId() != null ? bdVendorService.selectById(poHeader.getVendorId()) : null;

        List<PuInvoiceLine> invoiceLines = new ArrayList<>();
        int sortOrder = 0;
        for (PuPurchaseOrderLine poLine : poLines) {
            BigDecimal received = poLine.getReceivedQuantity() != null ? poLine.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal invoiced = poLine.getInvoicedQty() != null ? poLine.getInvoicedQty() : BigDecimal.ZERO;
            BigDecimal remaining = received.subtract(invoiced);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 不含税单价:优先取 PO 的 unitPriceExcludingTax,缺失则回退 unitPrice(视为不含税)
            BigDecimal unitPriceExcl = poLine.getUnitPriceExcludingTax() != null
                ? poLine.getUnitPriceExcludingTax() : poLine.getUnitPrice();
            if (unitPriceExcl == null) {
                unitPriceExcl = BigDecimal.ZERO;
            }
            BigDecimal taxRatePct = normalizeTaxRateToPercent(poLine.getTaxRate());
            BigDecimal lineAmount = remaining.multiply(unitPriceExcl).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineAmount.multiply(taxRatePct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineAmount.add(lineTax);

            PuInvoiceLine line = new PuInvoiceLine();
            line.setLineNo(String.valueOf(sortOrder + 1));
            line.setMaterialId(poLine.getMaterialId());
            line.setMaterialCode(poLine.getMaterialCode());
            line.setMaterialName(poLine.getMaterialName());
            line.setMaterialSpec(poLine.getMaterialSpec());
            line.setQuantity(remaining);
            line.setUnit(poLine.getUnit());
            line.setUnitPrice(unitPriceExcl);
            line.setLineAmount(lineAmount);
            line.setTaxRate(taxRatePct);
            line.setTaxAmount(lineTax);
            line.setTotalAmount(lineTotal);
            line.setPoLineId(poLine.getLineId());
            line.setSortOrder(sortOrder++);
            invoiceLines.add(line);
        }
        if (invoiceLines.isEmpty()) {
            log.info("生成进项发票草稿跳过:采购订单无可开票行(已全部开票), poId={}", poId);
            return null;
        }

        PuInvoice invoice = new PuInvoice();
        invoice.setInvoiceNo("FP" + IdWorker.getId());
        invoice.setInvoiceType(vendor != null && StringUtils.isNotBlank(vendor.getDefaultInvoiceType())
            ? vendor.getDefaultInvoiceType() : "VAT_SPECIAL");
        invoice.setVendorId(poHeader.getVendorId());
        invoice.setVendorName(poHeader.getVendorName());
        invoice.setPoId(poHeader.getOrderId());
        invoice.setInvoiceDate(new Date());
        invoice.setCurrency(StringUtils.isNotBlank(poHeader.getCurrency()) ? poHeader.getCurrency() : "CNY");
        invoice.setExchangeRate(poHeader.getExchangeRate() != null ? poHeader.getExchangeRate() : BigDecimal.ONE);
        invoice.setStatus(STATUS_DRAFT);
        invoice.setCertStatus(CERT_UNCERTIFIED);
        invoice.setIssueChannel("MANUAL");
        invoice.setRedFlag("0");
        invoice.setVersion(0);
        invoice.setVerifiedAmount(BigDecimal.ZERO);
        invoice.setDeductibleTaxAmount(BigDecimal.ZERO);
        if (vendor != null) {
            invoice.setSellerTaxId(vendor.getTaxId());
            invoice.setSellerTitle(StringUtils.isNotBlank(vendor.getInvoiceTitle())
                ? vendor.getInvoiceTitle() : poHeader.getVendorName());
            invoice.setSellerBankName(vendor.getInvoiceBankName());
            invoice.setSellerBankAccount(vendor.getBankAccount());
            invoice.setSellerAddressPhone(vendor.getInvoiceAddressPhone());
        }
        invoice.setAmount(invoiceLines.stream().map(PuInvoiceLine::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setTaxAmount(invoiceLines.stream().map(PuInvoiceLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setTotalAmount(invoiceLines.stream().map(PuInvoiceLine::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        baseMapper.insert(invoice);
        for (PuInvoiceLine line : invoiceLines) {
            line.setInvoiceId(invoice.getInvoiceId());
            lineMapper.insert(line);
            // 原子累加已开票数量(防重复开票);数量内部计算,无注入风险
            // 条件更新门禁: invoiced_qty + ? <= received_quantity, 影响行数为 0 说明并发已开票,回滚本次草稿
            int affected = puPurchaseOrderLineMapper.update(null,
                Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
                    .eq(PuPurchaseOrderLine::getLineId, line.getPoLineId())
                    .apply("invoiced_qty + {0} <= received_quantity", line.getQuantity())
                    .setSql("invoiced_qty = invoiced_qty + " + line.getQuantity().toPlainString()));
            if (affected == 0) {
                throw new ServiceException("采购订单行已开票数量变化,请刷新后重试: lineId=" + line.getPoLineId());
            }
        }
        log.info("采购订单自动生成进项发票草稿, poId={}, invoiceId={}, invoiceNo={}, lineCount={}",
            poId, invoice.getInvoiceId(), invoice.getInvoiceNo(), invoiceLines.size());
        return invoice.getInvoiceId();
    }

    /**
     * 收票确认:DRAFT -> ISSUED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveInvoice(Long invoiceId) {
        PuInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (!STATUS_DRAFT.equals(invoice.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许收票确认,仅草稿可确认", invoice.getStatus());
        }
        int rows = baseMapper.update(null,
            Wrappers.<PuInvoice>lambdaUpdate()
                .eq(PuInvoice::getInvoiceId, invoiceId)
                .eq(PuInvoice::getVersion, invoice.getVersion())
                .set(PuInvoice::getStatus, STATUS_ISSUED)
                .set(PuInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,收票确认失败,请重试");
        }
        applicationEventPublisher.publishEvent(
            new PuInvoiceIssuedEvent(this, invoiceId, TenantHelper.getTenantId()));
    }

    /**
     * 三单匹配(PO + 收货 + 发票):物料/数量(发票≤收货)/单价/税率一致性校验。
     */
    @Override
    public MatchResultVo matchThreeWay(Long invoiceId) {
        PuInvoiceVo invoice = selectById(invoiceId);
        MatchResultVo result = new MatchResultVo();
        if (invoice == null) {
            result.addMismatch("发票不存在");
            return result;
        }
        List<PuInvoiceLine> lines = invoice.getPuInvoiceLineList();
        if (lines == null || lines.isEmpty()) {
            result.addMismatch("发票无明细行");
            return result;
        }

        // 收集 poLineId / grLineId, 批量加载 PO 行与收货单行
        Set<Long> poLineIds = new HashSet<>();
        Set<Long> grLineIds = new HashSet<>();
        boolean hasLink = false;
        for (PuInvoiceLine line : lines) {
            if (line.getPoLineId() != null) {
                poLineIds.add(line.getPoLineId());
                hasLink = true;
            }
            if (line.getGrLineId() != null) {
                grLineIds.add(line.getGrLineId());
                hasLink = true;
            }
        }
        if (!hasLink) {
            // 纯手工发票(无 PO/GR 关联),跳过三单匹配
            result.setPassed(true);
            result.setSummary("发票未关联采购订单/收货单,跳过三单匹配");
            return result;
        }

        Map<Long, PuPurchaseOrderLine> poLineMap = new HashMap<>();
        if (!poLineIds.isEmpty()) {
            for (PuPurchaseOrderLine pl : puPurchaseOrderLineMapper.selectBatchIds(poLineIds)) {
                poLineMap.put(pl.getLineId(), pl);
            }
        }
        Map<Long, WmMaterialReceiveLine> grLineMap = new HashMap<>();
        if (!grLineIds.isEmpty()) {
            for (WmMaterialReceiveLine gl : wmMaterialReceiveLineMapper.selectBatchIds(grLineIds)) {
                grLineMap.put(gl.getLineId(), gl);
            }
        }

        int checked = 0;
        for (PuInvoiceLine line : lines) {
            String lineTag = "行[" + (line.getMaterialCode() != null ? line.getMaterialCode() : line.getLineNo()) + "]";
            if (line.getPoLineId() != null) {
                checked++;
                PuPurchaseOrderLine poLine = poLineMap.get(line.getPoLineId());
                if (poLine == null) {
                    result.addMismatch(lineTag + "关联采购订单行不存在");
                    continue;
                }
                if (line.getMaterialId() == null || !line.getMaterialId().equals(poLine.getMaterialId())) {
                    result.addMismatch(lineTag + "物料与采购订单行不一致");
                }
                BigDecimal received = poLine.getReceivedQuantity() != null ? poLine.getReceivedQuantity() : BigDecimal.ZERO;
                if (line.getQuantity() != null && line.getQuantity().compareTo(received) > 0) {
                    result.addMismatch(lineTag + "开票数量(" + line.getQuantity() + ")超过收货数量(" + received + ")");
                }
                // 单价(不含税):PO 优先 unitPriceExcludingTax, 缺失用 unitPrice
                BigDecimal poPrice = poLine.getUnitPriceExcludingTax() != null
                    ? poLine.getUnitPriceExcludingTax() : poLine.getUnitPrice();
                if (poPrice != null && line.getUnitPrice() != null
                    && line.getUnitPrice().subtract(poPrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                    result.addMismatch(lineTag + "单价(" + line.getUnitPrice() + ")与采购订单(" + poPrice + ")不一致");
                }
                // 税率:归一化后比较
                BigDecimal poTaxPct = normalizeTaxRateToPercent(poLine.getTaxRate());
                if (line.getTaxRate() != null
                    && line.getTaxRate().subtract(poTaxPct).abs().compareTo(new BigDecimal("0.01")) > 0) {
                    result.addMismatch(lineTag + "税率(" + line.getTaxRate() + ")与采购订单(" + poTaxPct + ")不一致");
                }
            }
            if (line.getGrLineId() != null) {
                WmMaterialReceiveLine grLine = grLineMap.get(line.getGrLineId());
                if (grLine == null) {
                    result.addMismatch(lineTag + "关联收货单行不存在");
                    continue;
                }
                if (line.getMaterialId() == null || !line.getMaterialId().equals(grLine.getMaterialId())) {
                    result.addMismatch(lineTag + "物料与收货单行不一致");
                }
                BigDecimal grQty = grLine.getQuantityReceived() != null ? grLine.getQuantityReceived() : BigDecimal.ZERO;
                if (line.getQuantity() != null && line.getQuantity().compareTo(grQty) > 0) {
                    result.addMismatch(lineTag + "开票数量(" + line.getQuantity() + ")超过收货单数量(" + grQty + ")");
                }
            }
        }
        result.setSummary("已校验" + checked + "个关联行,差异" + result.getMismatchCount() + "处");
        return result;
    }

    /**
     * 核销发票:前置三单匹配(带 PO 关联行时),原子累加 verified_amount,达额转 VERIFIED。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyInvoice(Long invoiceId, BigDecimal verifyAmount, Long receiptId) {
        if (verifyAmount == null || verifyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("核销金额必须大于0");
        }
        PuInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (!STATUS_ISSUED.equals(invoice.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许核销,仅已收发票可核销", invoice.getStatus());
        }
        // 前置三单匹配:带 PO 关联行且匹配失败则阻止核销
        MatchResultVo match = matchThreeWay(invoiceId);
        if (!match.isPassed()) {
            throw new ServiceException("三单匹配未通过,禁止核销:{}", String.join("; ", match.getMismatches()));
        }
        BigDecimal newVerified = invoice.getVerifiedAmount().add(verifyAmount);
        String newStatus = newVerified.compareTo(invoice.getTotalAmount()) >= 0 ? STATUS_VERIFIED : STATUS_ISSUED;
        int rows = baseMapper.update(null,
            Wrappers.<PuInvoice>lambdaUpdate()
                .eq(PuInvoice::getInvoiceId, invoiceId)
                .eq(PuInvoice::getVersion, invoice.getVersion())
                .setSql("verified_amount = verified_amount + " + verifyAmount.toPlainString())
                .set(PuInvoice::getStatus, newStatus)
                .set(PuInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,核销失败,请重试");
        }
        log.info("进项发票核销, invoiceId={}, verifyAmount={}, status={}->{}", invoiceId, verifyAmount, invoice.getStatus(), newStatus);
    }

    /**
     * 进项认证:UNCERTIFIED -> CERTIFIED,记录认证日期与可抵扣税额(默认全额=税额)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void certifyInvoice(Long invoiceId) {
        PuInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (!CERT_UNCERTIFIED.equals(invoice.getCertStatus())) {
            throw new ServiceException("当前认证状态【{}】不允许认证,仅未认证可认证", invoice.getCertStatus());
        }
        if (STATUS_DRAFT.equals(invoice.getStatus()) || STATUS_CANCELLED.equals(invoice.getStatus())) {
            throw new ServiceException("当前发票状态【{}】不允许认证,仅已收/已核销可认证", invoice.getStatus());
        }
        BigDecimal deductible = invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO;
        int rows = baseMapper.update(null,
            Wrappers.<PuInvoice>lambdaUpdate()
                .eq(PuInvoice::getInvoiceId, invoiceId)
                .eq(PuInvoice::getVersion, invoice.getVersion())
                .set(PuInvoice::getCertStatus, CERT_CERTIFIED)
                .set(PuInvoice::getCertDate, new Date())
                .set(PuInvoice::getDeductibleTaxAmount, deductible)
                .set(PuInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,认证失败,请重试");
        }
        log.info("进项发票认证, invoiceId={}, deductibleTax={}", invoiceId, deductible);
    }

    /**
     * 进项抵扣:CERTIFIED -> DEDUCTED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductInvoice(Long invoiceId) {
        PuInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (!CERT_CERTIFIED.equals(invoice.getCertStatus())) {
            throw new ServiceException("当前认证状态【{}】不允许抵扣,仅已认证可抵扣", invoice.getCertStatus());
        }
        if (STATUS_DRAFT.equals(invoice.getStatus()) || STATUS_CANCELLED.equals(invoice.getStatus())) {
            throw new ServiceException("当前发票状态【{}】不允许抵扣,仅已收/已核销可抵扣", invoice.getStatus());
        }
        int rows = baseMapper.update(null,
            Wrappers.<PuInvoice>lambdaUpdate()
                .eq(PuInvoice::getInvoiceId, invoiceId)
                .eq(PuInvoice::getVersion, invoice.getVersion())
                .set(PuInvoice::getCertStatus, CERT_DEDUCTED)
                .set(PuInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,抵扣失败,请重试");
        }
        log.info("进项发票抵扣, invoiceId={}", invoiceId);
    }

    /**
     * 作废发票(仅 ISSUED/DRAFT 可作废);回退采购订单行 invoiced_qty。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInvoice(Long invoiceId, String reason) {
        PuInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (!STATUS_ISSUED.equals(invoice.getStatus()) && !STATUS_DRAFT.equals(invoice.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许作废", invoice.getStatus());
        }
        rollbackInvoicedQty(invoice);
        int rows = baseMapper.update(null,
            Wrappers.<PuInvoice>lambdaUpdate()
                .eq(PuInvoice::getInvoiceId, invoiceId)
                .eq(PuInvoice::getVersion, invoice.getVersion())
                .set(PuInvoice::getStatus, STATUS_CANCELLED)
                .set(PuInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,作废失败,请重试");
        }
        log.info("进项发票作废, invoiceId={}, invoiceNo={}, reason={}", invoiceId, invoice.getInvoiceNo(),
            StringUtils.isBlank(reason) ? "作废" : reason);
    }

    /**
     * 红冲发票(全量):复制原行取负生成红字发票,冲减原发票 verified_amount 与采购订单行 invoiced_qty;
     * 原发票若已抵扣则 cert_status -> TRANSFERRED。仅 ISSUED/VERIFIED 的蓝字发票可红冲。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long redFlush(Long originalInvoiceId, String reason) {
        PuInvoice original = baseMapper.selectById(originalInvoiceId);
        if (original == null) {
            throw new ServiceException("原发票不存在");
        }
        if (!STATUS_ISSUED.equals(original.getStatus()) && !STATUS_VERIFIED.equals(original.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许红冲,仅已收/已核销的蓝字发票可红冲", original.getStatus());
        }
        if ("1".equals(original.getRedFlag())) {
            throw new ServiceException("红字发票不允许再次红冲");
        }
        List<PuInvoiceLine> origLines = lineMapper.selectList(
            Wrappers.<PuInvoiceLine>lambdaQuery().eq(PuInvoiceLine::getInvoiceId, originalInvoiceId));
        if (origLines.isEmpty()) {
            throw new ServiceException("原发票无明细行,无法红冲");
        }

        // 复制原行取负
        List<PuInvoiceLine> redLines = new ArrayList<>();
        for (PuInvoiceLine orig : origLines) {
            redLines.add(negateLine(orig));
        }
        Long redId = buildAndInsertRedInvoice(original, redLines, reason);
        BigDecimal redTotal = sumTotal(redLines);
        // 冲减原发票已核销金额;若已抵扣则进项转出
        offsetOriginal(original, redTotal.abs());
        log.info("进项发票红冲(全量), originalInvoiceId={}, redInvoiceId={}, redTotal={}", originalInvoiceId, redId, redTotal);
        applicationEventPublisher.publishEvent(
            new PuInvoiceIssuedEvent(this, redId, TenantHelper.getTenantId()));
        return redId;
    }

    /**
     * 退货进项转出(部分红冲):按退货数量生成红字发票,原发票 cert_status -> TRANSFERRED,
     * 冲减原发票 verified_amount 与采购订单行 invoiced_qty。由 PuPurchaseReturnServiceImpl.execute 调用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long redFlushForReturn(Long originalInvoiceId, Map<Long, BigDecimal> poLineReturnQty) {
        PuInvoice original = baseMapper.selectById(originalInvoiceId);
        if (original == null) {
            log.warn("退货进项转出跳过:原进项发票不存在, invoiceId={}", originalInvoiceId);
            return null;
        }
        if ("1".equals(original.getRedFlag())) {
            log.warn("退货进项转出跳过:原发票为红字发票, invoiceId={}", originalInvoiceId);
            return null;
        }
        if (poLineReturnQty == null || poLineReturnQty.isEmpty()) {
            return null;
        }
        List<PuInvoiceLine> origLines = lineMapper.selectList(
            Wrappers.<PuInvoiceLine>lambdaQuery().eq(PuInvoiceLine::getInvoiceId, originalInvoiceId));

        // 仅红冲退货覆盖的行,按退货数量取负
        List<PuInvoiceLine> redLines = new ArrayList<>();
        for (PuInvoiceLine orig : origLines) {
            if (orig.getPoLineId() == null) {
                continue;
            }
            BigDecimal returnQty = poLineReturnQty.get(orig.getPoLineId());
            if (returnQty == null || returnQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 退货数量不超过该发票行已开票数量(防超额红冲;多发票覆盖同 PO 行的按发票拆分见 TODO)
            BigDecimal origQty = orig.getQuantity() != null ? orig.getQuantity() : BigDecimal.ZERO;
            if (returnQty.compareTo(origQty) > 0) {
                log.warn("退货进项转出:退货数量({})超过该发票行已开票数量({}),按已开票数量红冲, poLineId={}",
                    returnQty, origQty, orig.getPoLineId());
                returnQty = origQty;
            }
            redLines.add(negateLine(orig, returnQty));
        }
        if (redLines.isEmpty()) {
            log.info("退货进项转出跳过:原发票无匹配退货行, invoiceId={}", originalInvoiceId);
            return null;
        }

        Long redId = buildAndInsertRedInvoice(original, redLines, "退货进项转出");
        BigDecimal redTotal = sumTotal(redLines);
        offsetOriginal(original, redTotal.abs());
        log.info("退货进项转出(部分红冲), originalInvoiceId={}, redInvoiceId={}, redTotal={}",
            originalInvoiceId, redId, redTotal);
        applicationEventPublisher.publishEvent(
            new PuInvoiceIssuedEvent(this, redId, TenantHelper.getTenantId()));
        return redId;
    }

    /**
     * 退货进项转出(按 PO 行查找覆盖的已抵扣蓝字进项发票并部分红冲)。
     * 仅 cert_status=DEDUCTED 的蓝字发票触发;未抵扣不转出。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int redFlushForReturnByPoLines(Map<Long, BigDecimal> poLineReturnQty) {
        if (poLineReturnQty == null || poLineReturnQty.isEmpty()) {
            return 0;
        }
        // 查覆盖这些 PO 行的发票行
        List<PuInvoiceLine> coverLines = lineMapper.selectList(
            Wrappers.<PuInvoiceLine>lambdaQuery().in(PuInvoiceLine::getPoLineId, poLineReturnQty.keySet()));
        if (coverLines.isEmpty()) {
            return 0;
        }
        // 按 PO 行汇总各蓝字发票行已开票数量，用于把退货数量按"先消先冲"逐发票分配，
        // 防止多发票覆盖同一 PO 行时每张都按全额退货红冲导致进项转出超冲（invoiced_qty 冲成负数）
        Map<Long, Map<Long, BigDecimal>> invoiceLineQtyByPoLine = new HashMap<>();
        for (PuInvoiceLine l : coverLines) {
            invoiceLineQtyByPoLine
                .computeIfAbsent(l.getPoLineId(), k -> new HashMap<>())
                .put(l.getInvoiceId(), l.getQuantity() != null ? l.getQuantity() : BigDecimal.ZERO);
        }
        // 剩余可冲退货量（初始为退货总量，逐发票扣减）
        Map<Long, BigDecimal> remainingReturnQty = new HashMap<>(poLineReturnQty);
        Set<Long> invoiceIds = new HashSet<>();
        for (PuInvoiceLine l : coverLines) {
            invoiceIds.add(l.getInvoiceId());
        }
        // 蓝字、非作废(已收/已核销)、已抵扣的发票才需进项转出
        List<PuInvoice> covering = baseMapper.selectList(
            Wrappers.<PuInvoice>lambdaQuery()
                .in(PuInvoice::getInvoiceId, invoiceIds)
                .eq(PuInvoice::getRedFlag, "0")
                .eq(PuInvoice::getCertStatus, CERT_DEDUCTED)
                .in(PuInvoice::getStatus, List.of(STATUS_ISSUED, STATUS_VERIFIED)));
        int count = 0;
        for (PuInvoice inv : covering) {
            // 构建"仅本发票可红冲量"的退货映射：每个 PO 行取 min(退货剩余, 本发票行已开票量)
            Map<Long, BigDecimal> perInvoiceReturnQty = new HashMap<>();
            for (Map.Entry<Long, BigDecimal> e : remainingReturnQty.entrySet()) {
                Long poLineId = e.getKey();
                BigDecimal remaining = e.getValue();
                if (remaining == null || remaining.signum() <= 0) {
                    continue;
                }
                BigDecimal invoiceQty = invoiceLineQtyByPoLine.getOrDefault(poLineId, Map.of())
                    .getOrDefault(inv.getInvoiceId(), BigDecimal.ZERO);
                BigDecimal canFlush = remaining.min(invoiceQty);
                if (canFlush.signum() > 0) {
                    perInvoiceReturnQty.put(poLineId, canFlush);
                }
            }
            if (perInvoiceReturnQty.isEmpty()) {
                continue;
            }
            Long redId = redFlushForReturn(inv.getInvoiceId(), perInvoiceReturnQty);
            if (redId != null) {
                count++;
                // 扣减剩余退货量
                for (Map.Entry<Long, BigDecimal> e : perInvoiceReturnQty.entrySet()) {
                    remainingReturnQty.merge(e.getKey(), e.getValue(), (a, b) -> a.subtract(b));
                }
            }
        }
        return count;
    }

    /**
     * 复制原行取负(全量红冲:数量/金额全部取负)。
     */
    private PuInvoiceLine negateLine(PuInvoiceLine orig) {
        return negateLine(orig, orig.getQuantity() != null ? orig.getQuantity() : BigDecimal.ZERO);
    }

    /**
     * 复制原行,按指定数量取负(部分红冲:数量取负,单价/税率沿用原行,金额按负数量重算)。
     */
    private PuInvoiceLine negateLine(PuInvoiceLine orig, BigDecimal qty) {
        PuInvoiceLine rl = new PuInvoiceLine();
        rl.setLineNo(orig.getLineNo());
        rl.setMaterialId(orig.getMaterialId());
        rl.setMaterialCode(orig.getMaterialCode());
        rl.setMaterialName(orig.getMaterialName());
        rl.setMaterialSpec(orig.getMaterialSpec());
        rl.setQuantity(qty.negate());
        rl.setUnit(orig.getUnit());
        rl.setUnitPrice(orig.getUnitPrice());
        BigDecimal unitPrice = orig.getUnitPrice() != null ? orig.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal lineAmount = qty.negate().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        rl.setLineAmount(lineAmount);
        rl.setTaxRate(orig.getTaxRate());
        BigDecimal taxRate = orig.getTaxRate() != null ? orig.getTaxRate() : BigDecimal.ZERO;
        BigDecimal taxAmount = lineAmount.multiply(taxRate)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        rl.setTaxAmount(taxAmount);
        rl.setTotalAmount(lineAmount.add(taxAmount));
        rl.setPoLineId(orig.getPoLineId());
        rl.setGrLineId(orig.getGrLineId());
        rl.setSortOrder(orig.getSortOrder());
        return rl;
    }

    /**
     * 构建并插入红字发票头+行(行已取负),并冲减采购订单行 invoiced_qty。返回红字发票ID。
     */
    private Long buildAndInsertRedInvoice(PuInvoice original, List<PuInvoiceLine> redLines, String reason) {
        PuInvoice red = new PuInvoice();
        red.setInvoiceNo("FP" + IdWorker.getId());
        red.setInvoiceType(original.getInvoiceType());
        red.setVendorId(original.getVendorId());
        red.setVendorName(original.getVendorName());
        red.setSellerTaxId(original.getSellerTaxId());
        red.setSellerTitle(original.getSellerTitle());
        red.setSellerBankName(original.getSellerBankName());
        red.setSellerBankAccount(original.getSellerBankAccount());
        red.setSellerAddressPhone(original.getSellerAddressPhone());
        red.setPoId(original.getPoId());
        red.setContractId(original.getContractId());
        red.setOriginalInvoiceId(original.getInvoiceId());
        red.setRedFlag("1");
        red.setInvoiceDate(new Date());
        red.setCurrency(original.getCurrency());
        red.setExchangeRate(original.getExchangeRate());
        red.setStatus(STATUS_ISSUED);
        red.setCertStatus(CERT_TRANSFERRED);
        red.setIssueChannel("MANUAL");
        red.setVersion(0);
        red.setVerifiedAmount(BigDecimal.ZERO);
        red.setDeductibleTaxAmount(BigDecimal.ZERO);
        red.setRemark(StringUtils.isBlank(reason) ? "红冲" : reason);

        red.setAmount(redLines.stream().map(l -> l.getLineAmount() != null ? l.getLineAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
        red.setTaxAmount(redLines.stream().map(l -> l.getTaxAmount() != null ? l.getTaxAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
        red.setTotalAmount(redLines.stream().map(l -> l.getTotalAmount() != null ? l.getTotalAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
        baseMapper.insert(red);
        for (PuInvoiceLine rl : redLines) {
            rl.setInvoiceId(red.getInvoiceId());
            lineMapper.insert(rl);
            // 红字数量为负,原子加即冲减订单行已开票数量
            if (rl.getPoLineId() != null && rl.getQuantity() != null) {
                puPurchaseOrderLineMapper.update(null,
                    Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
                        .eq(PuPurchaseOrderLine::getLineId, rl.getPoLineId())
                        .setSql("invoiced_qty = invoiced_qty + " + rl.getQuantity().toPlainString()));
            }
        }
        return red.getInvoiceId();
    }

    /**
     * 红字行价税合计求和(负数)。
     */
    private BigDecimal sumTotal(List<PuInvoiceLine> redLines) {
        return redLines.stream()
            .map(l -> l.getTotalAmount() != null ? l.getTotalAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 冲减原发票:verified_amount -= offset;若已抵扣则 cert_status -> TRANSFERRED;
     * verified 归零且原为 VERIFIED 则回退至 ISSUED。带乐观锁。
     */
    private void offsetOriginal(PuInvoice original, BigDecimal offset) {
        BigDecimal origVerified = original.getVerifiedAmount() != null ? original.getVerifiedAmount() : BigDecimal.ZERO;
        BigDecimal newVerified = origVerified.subtract(offset);
        if (newVerified.compareTo(BigDecimal.ZERO) < 0) {
            newVerified = BigDecimal.ZERO;
        }
        boolean backToIssued = newVerified.compareTo(BigDecimal.ZERO) == 0 && STATUS_VERIFIED.equals(original.getStatus());
        boolean transferOut = CERT_DEDUCTED.equals(original.getCertStatus());
        int rows = baseMapper.update(null,
            Wrappers.<PuInvoice>lambdaUpdate()
                .eq(PuInvoice::getInvoiceId, original.getInvoiceId())
                .eq(PuInvoice::getVersion, original.getVersion())
                .set(PuInvoice::getVerifiedAmount, newVerified)
                .set(PuInvoice::getStatus, backToIssued ? STATUS_ISSUED : original.getStatus())
                .set(PuInvoice::getCertStatus, transferOut ? CERT_TRANSFERRED : original.getCertStatus())
                .set(PuInvoice::getVersion, original.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("原发票数据已变更,红冲失败,请重试");
        }
    }

    /**
     * 回退发票对应采购订单行的已开票数量(作废/删除草稿时调用)。
     */
    private void rollbackInvoicedQty(PuInvoice invoice) {
        List<PuInvoiceLine> lines = lineMapper.selectList(
            Wrappers.<PuInvoiceLine>lambdaQuery().eq(PuInvoiceLine::getInvoiceId, invoice.getInvoiceId()));
        for (PuInvoiceLine line : lines) {
            if (line.getPoLineId() == null || line.getQuantity() == null) {
                continue;
            }
            puPurchaseOrderLineMapper.update(null,
                Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
                    .eq(PuPurchaseOrderLine::getLineId, line.getPoLineId())
                    .setSql("invoiced_qty = invoiced_qty - " + line.getQuantity().toPlainString()));
        }
    }

    /**
     * 税率归一化为百分比:PO 行 taxRate 注释为"0.13 表示 13%"(小数),发票行 taxRate 用百分比(13)。
     * 0 < rate < 1 视为小数 ×100,否则视为百分比原值。
     */
    private BigDecimal normalizeTaxRateToPercent(BigDecimal taxRate) {
        if (taxRate == null) {
            return BigDecimal.ZERO;
        }
        if (taxRate.compareTo(BigDecimal.ZERO) > 0 && taxRate.compareTo(BigDecimal.ONE) < 0) {
            return taxRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        return taxRate.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<PuInvoice> buildQueryWrapper(PuInvoiceBo bo) {
        LambdaQueryWrapper<PuInvoice> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getInvoiceNo()), PuInvoice::getInvoiceNo, bo.getInvoiceNo());
        lqw.eq(bo.getVendorId() != null, PuInvoice::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuInvoice::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getInvoiceType()), PuInvoice::getInvoiceType, bo.getInvoiceType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuInvoice::getStatus, bo.getStatus());
        lqw.eq(bo.getPoId() != null, PuInvoice::getPoId, bo.getPoId());
        lqw.orderByDesc(PuInvoice::getCreateTime);
        return lqw;
    }
}

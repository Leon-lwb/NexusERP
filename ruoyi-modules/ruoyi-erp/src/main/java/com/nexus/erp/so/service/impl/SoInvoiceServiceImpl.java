package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.vo.BdClientVo;
import com.nexus.erp.bd.service.IBdClientService;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceLineVo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.mapper.SoInvoiceLineMapper;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoInvoiceService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.so.event.SoInvoiceIssuedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 销售发票Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soInvoiceService")
public class SoInvoiceServiceImpl implements ISoInvoiceService {

    private final SoInvoiceMapper baseMapper;
    private final SoInvoiceLineMapper lineMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;
    private final ISoSalesOrderHeaderService soSalesOrderHeaderService;
    private final IBdClientService bdClientService;
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 分页查询销售发票列表
     */
    @Override
    public TableDataInfo<SoInvoiceVo> selectPageList(SoInvoiceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoInvoice> lqw = buildQueryWrapper(bo);
        Page<SoInvoiceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售发票列表（不分页）
     */
    @Override
    public List<SoInvoiceVo> selectList(SoInvoiceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售发票（含明细行）
     */
    @Override
    public SoInvoiceVo selectById(Long id) {
        SoInvoiceVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<SoInvoiceLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoInvoiceLine::getInvoiceId, vo.getInvoiceId());
            lqw.orderByAsc(SoInvoiceLine::getSortOrder);
            // 转 VO 返回，避免把 tenantId/delFlag/createBy 等审计字段暴露给前端
            vo.setSoInvoiceLineList(MapstructUtils.convert(lineMapper.selectList(lqw), SoInvoiceLineVo.class));
        }
        return vo;
    }

    /**
     * 新增销售发票（含明细行）。
     * <p>状态与红冲相关字段一律服务端权威：</p>
     * <ul>
     *   <li><b>status 强制 DRAFT</b>：发票状态只能经审批流（{@code afterFlowHandle}）/ 核销 / 作废流转。
     *       原实现仅在为空时兜底，客户端可直接建出 ISSUED 发票 —— 而 {@code insertByBo} 不发布
     *       {@code SoInvoiceIssuedEvent}（只有 {@code afterFlowHandle} 与 {@code redFlush} 发布），
     *       于是发票标记「已开」却从未过账到总账。</li>
     *   <li><b>redFlag/originalInvoiceId/verifiedAmount 强制复位</b>：红字发票只能由 {@code redFlush} 生成。
     *       否则客户端可伪造 {@code redFlag=1} 建「红票」，配合负数量明细走
     *       {@code occupyInvoicedQty} 的释放分支，绕过红冲流程释放已开票量（重新打开超开缺口）。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoInvoiceBo bo) {
        SoInvoice entity = MapstructUtils.convert(bo, SoInvoice.class);
        entity.setStatus(STATUS_DRAFT);
        entity.setRedFlag("0");
        entity.setOriginalInvoiceId(null);
        entity.setVerifiedAmount(BigDecimal.ZERO);
        if (StringUtils.isBlank(entity.getInvoiceNo())) {
            // 手工建票未填号时兜底生成，与 generateDraftFromOrder 口径一致
            entity.setInvoiceNo("FP" + IdWorker.getId());
        }
        if (StringUtils.isBlank(entity.getIssueChannel())) {
            entity.setIssueChannel("MANUAL");
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        // 手工建票：表头金额按明细汇总，不采信客户端（与 generateDraftFromOrder 口径一致）
        if (bo.getSoInvoiceLineList() != null) {
            recalcHeaderAmounts(entity, bo.getSoInvoiceLineList());
        } else {
            entity.setAmount(BigDecimal.ZERO);
            entity.setTaxAmount(BigDecimal.ZERO);
            entity.setTotalAmount(BigDecimal.ZERO);
        }
        int rows = baseMapper.insert(entity);
        if (bo.getSoInvoiceLineList() != null && !bo.getSoInvoiceLineList().isEmpty()) {
            for (SoInvoiceLine line : bo.getSoInvoiceLineList()) {
                // 负数量明细只应来自红冲（redFlush 复制原行取负）；手工建票传负数会走
                // occupyInvoicedQty 的释放分支，等于绕过红冲释放已开票量
                if (line.getQuantity() != null && line.getQuantity().signum() < 0) {
                    throw new ServiceException("发票明细数量不能为负数，红字发票请通过红冲生成");
                }
                assertLineTaxRate(line);
                line.setInvoiceId(entity.getInvoiceId());
                lineMapper.insert(line);
                // 手工建票同样占用可开票量，否则可绕过防超开门禁重复开票
                occupyInvoicedQty(line.getSoLineId(), line.getQuantity());
            }
        }
        return rows;
    }

    /**
     * 根据销售订单生成发票草稿（按"已发货未开票"自动汇总）。
     * <p>由 {@code SalesOrderFulfilledEventListener} 在订单履约后异步触发。
     * 幂等：按行 {@code invoiced_qty = delivered_qty - invoiced_qty} 计算可开票量，
     * 全部 ≤0 则不生成；生成后原子累加 {@code so_sales_order_line.invoiced_qty} 防重复开票。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateDraftFromOrder(Long soId) {
        SoSalesOrderHeaderVo soHeader = soSalesOrderHeaderService.selectById(soId);
        if (soHeader == null) {
            log.warn("生成发票草稿失败:销售订单不存在, soId={}", soId);
            return null;
        }
        List<SoSalesOrderLineVo> soLines = soHeader.getSoSalesOrderLineList();
        if (soLines == null || soLines.isEmpty()) {
            log.warn("生成发票草稿跳过:销售订单无行明细, soId={}", soId);
            return null;
        }

        // 购买方开票四要素(从客户档案取)
        BdClientVo client = soHeader.getClientId() != null ? bdClientService.selectById(soHeader.getClientId()) : null;

        // 按行计算可开票量 = 已发货 - 已开票,过滤 >0
        List<SoInvoiceLine> invoiceLines = new ArrayList<>();
        int sortOrder = 0;
        for (SoSalesOrderLineVo soLine : soLines) {
            BigDecimal delivered = soLine.getDeliveredQty() != null ? soLine.getDeliveredQty() : BigDecimal.ZERO;
            BigDecimal invoiced = soLine.getInvoicedQty() != null ? soLine.getInvoicedQty() : BigDecimal.ZERO;
            BigDecimal remaining = delivered.subtract(invoiced);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal unitPrice = soLine.getUnitPrice() != null ? soLine.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal taxRate = soLine.getTaxRate() != null ? soLine.getTaxRate() : BigDecimal.ZERO;
            // 不含税行金额 = 数量 × 单价(按 SO 单价为不含税单价计算;草稿可编辑)
            BigDecimal lineAmount = remaining.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineAmount.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineAmount.add(lineTax);

            SoInvoiceLine line = new SoInvoiceLine();
            line.setLineNo(soLine.getLineNo());
            line.setMaterialId(soLine.getMaterialId());
            line.setMaterialCode(soLine.getMaterialCode());
            line.setMaterialName(soLine.getMaterialName());
            line.setMaterialSpec(soLine.getMaterialSpec());
            line.setQuantity(remaining);
            line.setUnit(soLine.getUnit());
            line.setUnitPrice(unitPrice);
            line.setLineAmount(lineAmount);
            line.setTaxRate(taxRate);
            line.setTaxAmount(lineTax);
            line.setTotalAmount(lineTotal);
            line.setSoLineId(soLine.getLineId());
            line.setSortOrder(sortOrder++);
            invoiceLines.add(line);
        }
        if (invoiceLines.isEmpty()) {
            log.info("生成发票草稿跳过:销售订单无可开票行(已全部开票), soId={}", soId);
            return null;
        }

        // 构建发票头
        SoInvoice invoice = new SoInvoice();
        invoice.setInvoiceNo("FP" + IdWorker.getId());
        invoice.setInvoiceType(client != null && StringUtils.isNotBlank(client.getDefaultInvoiceType())
            ? client.getDefaultInvoiceType() : "VAT_NORMAL");
        invoice.setClientId(soHeader.getClientId());
        invoice.setClientName(soHeader.getClientName());
        invoice.setSoId(soHeader.getSoId());
        invoice.setInvoiceDate(new Date());
        invoice.setCurrency(StringUtils.isNotBlank(soHeader.getCurrency()) ? soHeader.getCurrency() : "CNY");
        invoice.setExchangeRate(soHeader.getExchangeRate() != null ? soHeader.getExchangeRate() : BigDecimal.ONE);
        invoice.setStatus(STATUS_DRAFT);
        invoice.setIssueChannel("MANUAL");
        invoice.setRedFlag("0");
        invoice.setVersion(0);
        invoice.setVerifiedAmount(BigDecimal.ZERO);
        if (client != null) {
            invoice.setBuyerTaxId(client.getTaxId());
            invoice.setBuyerTitle(StringUtils.isNotBlank(client.getInvoiceTitle())
                ? client.getInvoiceTitle() : soHeader.getClientName());
            invoice.setBuyerBankName(client.getInvoiceBankName());
            invoice.setBuyerBankAccount(client.getInvoiceBankAccount());
            invoice.setBuyerAddressPhone(client.getInvoiceAddressPhone());
        }
        // 头金额 = 各行之和
        invoice.setAmount(invoiceLines.stream().map(SoInvoiceLine::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setTaxAmount(invoiceLines.stream().map(SoInvoiceLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setTotalAmount(invoiceLines.stream().map(SoInvoiceLine::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        baseMapper.insert(invoice);
        for (SoInvoiceLine line : invoiceLines) {
            line.setInvoiceId(invoice.getInvoiceId());
            lineMapper.insert(line);
            // 占用可开票量（统一门禁入口，影响行数为 0 说明并发已开票，抛异常回滚本次草稿）
            occupyInvoicedQty(line.getSoLineId(), line.getQuantity());
        }
        log.info("销售订单自动生成发票草稿, soId={}, invoiceId={}, invoiceNo={}, lineCount={}",
            soId, invoice.getInvoiceId(), invoice.getInvoiceNo(), invoiceLines.size());
        return invoice.getInvoiceId();
    }

    /**
     * 修改销售发票（含明细行），仅草稿和已驳回状态可编辑。
     * <p>头更新带 @Version 乐观锁(并发不丢更新);明细行按 lineId 差量更新(新增/更新/删除),
     * 取代原先全删重插(原实现并发丢行且无锁)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoInvoiceBo bo) {
        SoInvoice existing = baseMapper.selectById(bo.getInvoiceId());
        if (existing == null) {
            throw new ServiceException("发票不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改发票", existing.getStatus());
        }
        // 显式拷贝可编辑字段(不覆盖 version/verified_amount/original_invoice_id/red_flag/电票字段/status)
        existing.setInvoiceNo(bo.getInvoiceNo());
        existing.setInvoiceCode(bo.getInvoiceCode());
        existing.setInvoiceType(bo.getInvoiceType());
        existing.setClientId(bo.getClientId());
        existing.setClientName(bo.getClientName());
        existing.setBuyerTaxId(bo.getBuyerTaxId());
        existing.setBuyerTitle(bo.getBuyerTitle());
        existing.setBuyerBankName(bo.getBuyerBankName());
        existing.setBuyerBankAccount(bo.getBuyerBankAccount());
        existing.setBuyerAddressPhone(bo.getBuyerAddressPhone());
        existing.setSoId(bo.getSoId());
        existing.setContractId(bo.getContractId());
        existing.setInvoiceDate(bo.getInvoiceDate());
        existing.setCurrency(bo.getCurrency());
        existing.setExchangeRate(bo.getExchangeRate());
        // 表头金额不直接采信客户端：提交了明细就按明细汇总，未提交则保持库中原值（existing 即库中值，不赋值）。
        // 原因：verifyInvoice 的核销上限就是 totalAmount —— 客户端把 totalAmount 改大，
        // 就能用一个远小于真实金额的收款把发票置为「已核销」。
        if (bo.getSoInvoiceLineList() != null) {
            recalcHeaderAmounts(existing, bo.getSoInvoiceLineList());
        }
        if (StringUtils.isNotBlank(bo.getIssueChannel())) {
            existing.setIssueChannel(bo.getIssueChannel());
        }
        existing.setRemark(bo.getRemark());
        existing.setAttr1(bo.getAttr1());
        existing.setAttr2(bo.getAttr2());
        existing.setAttr3(bo.getAttr3());
        existing.setAttr4(bo.getAttr4());
        // 头更新:existing.version 来自 DB,@Version 自动 WHERE version=? 并 SET version+1
        int rows = baseMapper.updateById(existing);
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,修改失败,请重试");
        }
        // 明细行差量更新(按 lineId 新增/更新/删除)
        if (bo.getSoInvoiceLineList() != null) {
            diffUpdateLines(existing.getInvoiceId(), bo.getSoInvoiceLineList());
        }
        return rows;
    }

    /**
     * 发票明细行差量更新:按 lineId 区分新增/更新/删除,取代全删重插。
     */
    private void diffUpdateLines(Long invoiceId, List<SoInvoiceLine> newLines) {
        List<SoInvoiceLine> existingLines = lineMapper.selectList(
            Wrappers.<SoInvoiceLine>lambdaQuery().eq(SoInvoiceLine::getInvoiceId, invoiceId));
        Map<Long, SoInvoiceLine> existingMap = new HashMap<>();
        for (SoInvoiceLine l : existingLines) {
            if (l.getLineId() != null) {
                existingMap.put(l.getLineId(), l);
            }
        }
        Set<Long> keepIds = new HashSet<>();
        for (SoInvoiceLine line : newLines) {
            assertLineTaxRate(line);
            line.setInvoiceId(invoiceId);
            if (line.getLineId() == null) {
                lineMapper.insert(line);
                // 新增明细：占用可开票量
                occupyInvoicedQty(line.getSoLineId(), line.getQuantity());
            } else {
                SoInvoiceLine before = existingMap.get(line.getLineId());
                lineMapper.updateById(line);
                keepIds.add(line.getLineId());
                // 已有明细：按「订单行归属 + 数量」的变化量调整占用
                adjustInvoicedQtyOnEdit(before, line);
            }
        }
        // 删除:现有行中不在保留集合内的
        for (SoInvoiceLine l : existingLines) {
            if (l.getLineId() != null && !keepIds.contains(l.getLineId())) {
                lineMapper.deleteById(l.getLineId());
                // 删除明细：释放其占用
                releaseInvoicedQty(l.getSoLineId(), l.getQuantity());
            }
        }
    }

    /**
     * 编辑发票明细时调整订单行已开票数量。
     * <p>换绑订单行则先释放旧行再占用新行；同一行只调差额（增加走占用门禁，减少走释放下界守卫）。</p>
     */
    private void adjustInvoicedQtyOnEdit(SoInvoiceLine before, SoInvoiceLine after) {
        if (before == null) {
            occupyInvoicedQty(after.getSoLineId(), after.getQuantity());
            return;
        }
        if (!Objects.equals(before.getSoLineId(), after.getSoLineId())) {
            releaseInvoicedQty(before.getSoLineId(), before.getQuantity());
            occupyInvoicedQty(after.getSoLineId(), after.getQuantity());
            return;
        }
        BigDecimal oldQty = before.getQuantity() == null ? BigDecimal.ZERO : before.getQuantity();
        BigDecimal newQty = after.getQuantity() == null ? BigDecimal.ZERO : after.getQuantity();
        BigDecimal delta = newQty.subtract(oldQty);
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            occupyInvoicedQty(after.getSoLineId(), delta);
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            releaseInvoicedQty(after.getSoLineId(), delta.negate());
        }
    }

    /**
     * 批量删除销售发票（含明细行），仅草稿状态可删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            SoInvoice existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("发票【{}】当前状态不允许删除", existing.getInvoiceNo());
            }
            // 删除草稿前回退订单行已开票数量(防重复开票计数残留)
            if (existing != null) {
                rollbackInvoicedQty(existing);
            }
            lineMapper.delete(Wrappers.lambdaQuery(SoInvoiceLine.class)
                .eq(SoInvoiceLine::getInvoiceId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 核销发票(原子累加 verified_amount,达价税合计则转 VERIFIED)。
     * <p>P1 仅实现回写原语;fin_settlement 记账由 P3 FinSettlementService.settle() 调用本方法。
     * 采用 setSql 原子累加 + @Version 乐观锁,防并发超开。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyInvoice(Long invoiceId, BigDecimal verifyAmount, Long receiptId) {
        if (verifyAmount == null || verifyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("核销金额必须大于0");
        }
        SoInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (!STATUS_ISSUED.equals(invoice.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许核销,仅已开票发票可核销", invoice.getStatus());
        }
        BigDecimal alreadyVerified = invoice.getVerifiedAmount() == null ? BigDecimal.ZERO : invoice.getVerifiedAmount();
        BigDecimal totalAmount = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
        BigDecimal newVerified = alreadyVerified.add(verifyAmount);
        // 核销上限：累计核销额不得超过价税合计（原实现无上限，可超额核销后仍置 VERIFIED）
        if (newVerified.compareTo(totalAmount) > 0) {
            throw new ServiceException("核销金额超过发票价税合计，剩余可核销："
                + totalAmount.subtract(alreadyVerified).toPlainString());
        }
        String newStatus = newVerified.compareTo(totalAmount) >= 0 ? STATUS_VERIFIED : STATUS_ISSUED;
        int rows = baseMapper.update(null,
            Wrappers.<SoInvoice>lambdaUpdate()
                .eq(SoInvoice::getInvoiceId, invoiceId)
                .eq(SoInvoice::getVersion, invoice.getVersion())
                .setSql("verified_amount = verified_amount + " + verifyAmount.toPlainString())
                .set(SoInvoice::getStatus, newStatus)
                .set(SoInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,核销失败,请重试");
        }
        log.info("发票核销, invoiceId={}, verifyAmount={}, status={}->{}", invoiceId, verifyAmount, invoice.getStatus(), newStatus);
    }

    /**
     * 作废发票（<b>仅 DRAFT 可作废</b>）；回退 so_sales_order_line.invoiced_qty 以释放可开票量。
     *
     * <p><b>为什么已开票（ISSUED）不允许作废</b>：作废只是把单据置为 CANCELLED，
     * 而总账过账只由 {@link SoInvoiceIssuedEvent} 触发 —— 作废不发布任何 GL 事件，
     * 因此已过账的凭证会**原样留在账上**，财务报表仍包含这张「已作废」发票的收入与应收，账实不符。
     * 已开票发票的正确冲销路径是**红冲**（{@link #redFlush}）：生成红字负数发票，
     * 由红票的 ISSUED 事件过账出红字凭证，借贷方向不变、金额取负，账务自然抵销。</p>
     *
     * <p>这与会计惯例一致：发票一经开具即产生纳税与账务义务，只能开具红字发票冲销，不能直接作废。
     * 若确需纠正未过账的发票，请先确认其处于草稿态。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInvoice(Long invoiceId, String reason) {
        SoInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("发票不存在");
        }
        if (STATUS_ISSUED.equals(invoice.getStatus()) || STATUS_VERIFIED.equals(invoice.getStatus())) {
            // 明确引导到红冲，而不是笼统的「状态不允许作废」
            throw new ServiceException("已开票/已核销的发票不允许作废，请使用【红冲】生成红字发票冲销（当前状态：{}）",
                invoice.getStatus());
        }
        if (!STATUS_DRAFT.equals(invoice.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许作废，仅草稿可作废", invoice.getStatus());
        }
        // 回退订单行已开票数量
        rollbackInvoicedQty(invoice);
        int rows = baseMapper.update(null,
            Wrappers.<SoInvoice>lambdaUpdate()
                .eq(SoInvoice::getInvoiceId, invoiceId)
                .eq(SoInvoice::getVersion, invoice.getVersion())
                .set(SoInvoice::getStatus, STATUS_CANCELLED)
                .set(SoInvoice::getVersion, invoice.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("发票数据已变更,作废失败,请重试");
        }
        log.info("发票作废, invoiceId={}, invoiceNo={}, reason={}", invoiceId, invoice.getInvoiceNo(),
            StringUtils.isBlank(reason) ? "作废" : reason);
    }

    /**
     * 红冲发票:复制原行取负生成红字发票(red_flag=1),冲减原发票 verified_amount,
     * 冲减订单行 invoiced_qty。仅 ISSUED/VERIFIED 的蓝字发票可红冲。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long redFlush(Long originalInvoiceId, String reason) {
        SoInvoice original = baseMapper.selectById(originalInvoiceId);
        if (original == null) {
            throw new ServiceException("原发票不存在");
        }
        if (!STATUS_ISSUED.equals(original.getStatus()) && !STATUS_VERIFIED.equals(original.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许红冲,仅已开票/已核销的蓝字发票可红冲", original.getStatus());
        }
        if ("1".equals(original.getRedFlag())) {
            throw new ServiceException("红字发票不允许再次红冲");
        }
        // 防重复红冲：同一张蓝字发票只能红冲一次。
        // 原实现只校验原票 redFlag（原票状态与标记在红冲后都不变），因此同一张蓝票可被无限红冲，
        // 每次都冲减 invoiced_qty 与原票 verified_amount。这里按 original_invoice_id 判重，无需新增 DDL。
        Long redCount = baseMapper.selectCount(Wrappers.<SoInvoice>lambdaQuery()
            .eq(SoInvoice::getOriginalInvoiceId, originalInvoiceId));
        if (redCount != null && redCount > 0) {
            throw new ServiceException("该发票已红冲，不允许重复红冲");
        }
        List<SoInvoiceLine> origLines = lineMapper.selectList(
            Wrappers.<SoInvoiceLine>lambdaQuery().eq(SoInvoiceLine::getInvoiceId, originalInvoiceId));
        if (origLines.isEmpty()) {
            throw new ServiceException("原发票无明细行,无法红冲");
        }

        // 复制原行取负生成红字发票头
        SoInvoice red = new SoInvoice();
        red.setInvoiceNo("FP" + IdWorker.getId());
        red.setInvoiceType(original.getInvoiceType());
        red.setClientId(original.getClientId());
        red.setClientName(original.getClientName());
        red.setSoId(original.getSoId());
        red.setContractId(original.getContractId());
        red.setOriginalInvoiceId(originalInvoiceId);
        red.setRedFlag("1");
        red.setInvoiceDate(new Date());
        red.setCurrency(original.getCurrency());
        red.setExchangeRate(original.getExchangeRate());
        red.setBuyerTaxId(original.getBuyerTaxId());
        red.setBuyerTitle(original.getBuyerTitle());
        red.setBuyerBankName(original.getBuyerBankName());
        red.setBuyerBankAccount(original.getBuyerBankAccount());
        red.setBuyerAddressPhone(original.getBuyerAddressPhone());
        red.setStatus(STATUS_ISSUED);
        red.setIssueChannel("MANUAL");
        red.setVersion(0);
        red.setVerifiedAmount(BigDecimal.ZERO);
        red.setRemark(StringUtils.isBlank(reason) ? "红冲" : reason);

        List<SoInvoiceLine> redLines = new ArrayList<>();
        BigDecimal redAmount = BigDecimal.ZERO;
        BigDecimal redTax = BigDecimal.ZERO;
        BigDecimal redTotal = BigDecimal.ZERO;
        for (SoInvoiceLine orig : origLines) {
            SoInvoiceLine rl = new SoInvoiceLine();
            rl.setLineNo(orig.getLineNo());
            rl.setMaterialId(orig.getMaterialId());
            rl.setMaterialCode(orig.getMaterialCode());
            rl.setMaterialName(orig.getMaterialName());
            rl.setMaterialSpec(orig.getMaterialSpec());
            rl.setQuantity(orig.getQuantity() != null ? orig.getQuantity().negate() : BigDecimal.ZERO);
            rl.setUnit(orig.getUnit());
            rl.setUnitPrice(orig.getUnitPrice());
            rl.setLineAmount(orig.getLineAmount() != null ? orig.getLineAmount().negate() : BigDecimal.ZERO);
            rl.setTaxRate(orig.getTaxRate());
            rl.setTaxAmount(orig.getTaxAmount() != null ? orig.getTaxAmount().negate() : BigDecimal.ZERO);
            rl.setTotalAmount(orig.getTotalAmount() != null ? orig.getTotalAmount().negate() : BigDecimal.ZERO);
            rl.setSoLineId(orig.getSoLineId());
            rl.setSortOrder(orig.getSortOrder());
            redLines.add(rl);
            redAmount = redAmount.add(rl.getLineAmount());
            redTax = redTax.add(rl.getTaxAmount());
            redTotal = redTotal.add(rl.getTotalAmount());
        }
        red.setAmount(redAmount);
        red.setTaxAmount(redTax);
        red.setTotalAmount(redTotal);
        baseMapper.insert(red);
        for (SoInvoiceLine rl : redLines) {
            rl.setInvoiceId(red.getInvoiceId());
            lineMapper.insert(rl);
            // 红字数量为负 -> 冲减订单行已开票数量（宽容模式：历史票未占用过时不阻断红冲）
            releaseInvoicedQty(rl.getSoLineId(), rl.getQuantity() == null ? null : rl.getQuantity().negate());
        }

        // 冲减原发票已核销金额(取红字总额绝对值);若归零且原为 VERIFIED 则回退至 ISSUED
        BigDecimal offset = redTotal.abs();
        BigDecimal origVerified = original.getVerifiedAmount() != null ? original.getVerifiedAmount() : BigDecimal.ZERO;
        BigDecimal newVerified = origVerified.subtract(offset);
        if (newVerified.compareTo(BigDecimal.ZERO) < 0) {
            newVerified = BigDecimal.ZERO;
        }
        boolean backToIssued = newVerified.compareTo(BigDecimal.ZERO) == 0 && STATUS_VERIFIED.equals(original.getStatus());
        int rows = baseMapper.update(null,
            Wrappers.<SoInvoice>lambdaUpdate()
                .eq(SoInvoice::getInvoiceId, originalInvoiceId)
                .eq(SoInvoice::getVersion, original.getVersion())
                .set(SoInvoice::getVerifiedAmount, newVerified)
                .set(SoInvoice::getStatus, backToIssued ? STATUS_ISSUED : original.getStatus())
                .set(SoInvoice::getVersion, original.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("原发票数据已变更,红冲失败,请重试");
        }
        log.info("发票红冲, originalInvoiceId={}, redInvoiceId={}, redTotal={}", originalInvoiceId, red.getInvoiceId(), redTotal);
        applicationEventPublisher.publishEvent(
            new SoInvoiceIssuedEvent(this, red.getInvoiceId(), TenantHelper.getTenantId()));
        return red.getInvoiceId();
    }

    /**
     * 校验明细税率区间（决策 Q3：税率为**百分数**，`13` 表示 13%）。
     *
     * <p>与合同（{@code SoContractServiceImpl} 的 0~100 校验）、报价
     * （{@code SoQuotationServiceImpl}）、订单（{@code SoSalesOrderLineBo} 的
     * {@code @DecimalMin/@DecimalMax}）口径保持一致 —— 发票此前是唯一没有区间校验的单据，
     * 可落库 `0.13`（被当成 0.13%）或 `1300` 这类值，导致行税额算错而无人察觉。</p>
     */
    private void assertLineTaxRate(SoInvoiceLine line) {
        BigDecimal taxRate = line.getTaxRate();
        if (taxRate != null
            && (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ServiceException("发票明细税率必须在 0~100 之间（百分数：13 表示 13%）");
        }
    }

    /**
     * 由明细行汇总发票表头金额（金额 = Σ行金额，税额 = Σ行税额，价税合计 = 金额 + 税额）。
     *
     * <p>与 {@code generateDraftFromOrder} 的汇总口径一致。**只汇总表头，不改写行金额** ——
     * 行金额的税额口径在前端存在分叉（发票页按「含税金额 × 税率」，订单页按「净额 × 税率」），
     * 统一口径属待决事项，不在本次范围内擅自改动。</p>
     *
     * @param invoice 发票头（原地覆盖 amount/taxAmount/totalAmount）
     * @param lines   客户端提交的明细行
     */
    private void recalcHeaderAmounts(SoInvoice invoice, List<SoInvoiceLine> lines) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (SoInvoiceLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getLineAmount() != null) {
                amount = amount.add(line.getLineAmount());
            }
            if (line.getTaxAmount() != null) {
                tax = tax.add(line.getTaxAmount());
            }
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        invoice.setAmount(amount);
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(amount.add(tax));
    }

    /**
     * 回退发票对应订单行的已开票数量(作废/删除草稿/红冲时调用)。
     * <p>宽容模式：历史手工票或本次改造前生成的草稿可能从未占用过 invoiced_qty，
     * 此时回退会命中下界守卫 —— 静默跳过而不是阻断作废/删除，避免旧数据无法清理。</p>
     */
    private void rollbackInvoicedQty(SoInvoice invoice) {
        List<SoInvoiceLine> lines = lineMapper.selectList(
            Wrappers.<SoInvoiceLine>lambdaQuery().eq(SoInvoiceLine::getInvoiceId, invoice.getInvoiceId()));
        for (SoInvoiceLine line : lines) {
            releaseInvoicedQty(line.getSoLineId(), line.getQuantity());
        }
    }

    // ==================== 可开票量占用/释放（统一入口）====================

    /**
     * 占用订单行可开票量（严格模式）：<b>所有</b>发票明细写入口都必须经过这里，
     * 否则手工建票/改票可以绕过「invoiced_qty + 本次 <= delivered_qty」的防超开门禁。
     *
     * @param soLineId 销售订单行ID，为空表示该明细未关联订单行（纯手工票），直接跳过
     * @param qty      本次占用数量（正数）
     */
    private void occupyInvoicedQty(Long soLineId, BigDecimal qty) {
        adjustInvoicedQty(soLineId, qty, true);
    }

    /**
     * 释放订单行可开票量（宽容模式）：下界守卫失败时静默跳过，不阻断作废/删除/红冲。
     *
     * @param soLineId 销售订单行ID，为空则跳过
     * @param qty      本次释放数量（正数）
     */
    private void releaseInvoicedQty(Long soLineId, BigDecimal qty) {
        if (qty == null) {
            return;
        }
        adjustInvoicedQty(soLineId, qty.negate(), false);
    }

    /**
     * 调整订单行已开票数量（原子 setSql，正数占用、负数释放）。
     * <p>两侧守卫：</p>
     * <ul>
     *   <li>占用：{@code COALESCE(invoiced_qty,0) + delta <= COALESCE(delivered_qty,0)}，防超开；</li>
     *   <li>释放：{@code COALESCE(invoiced_qty,0) + delta >= 0}，防已开票数量变负
     *       （变负会反向放大可开票量，进而导致重复开票）。</li>
     * </ul>
     * <p>用 COALESCE 兜住历史行的 NULL 值 —— 否则 {@code NULL + x} 恒为 NULL，条件恒假。</p>
     *
     * @param soLineId 销售订单行ID
     * @param delta    变化量（正=占用，负=释放）
     * @param strict   true=守卫失败抛异常；false=守卫失败静默跳过
     * @return 影响行数
     */
    private int adjustInvoicedQty(Long soLineId, BigDecimal delta, boolean strict) {
        if (soLineId == null || delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        // delta 为 BigDecimal，拼接无注入风险
        LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, soLineId)
            .setSql("invoiced_qty = COALESCE(invoiced_qty, 0) + " + delta.toPlainString());
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            wrapper.apply("COALESCE(invoiced_qty, 0) + {0} <= COALESCE(delivered_qty, 0)", delta);
        } else {
            wrapper.apply("COALESCE(invoiced_qty, 0) + {0} >= 0", delta);
        }
        int affected = soSalesOrderLineMapper.update(null, wrapper);
        if (affected == 0) {
            if (strict) {
                throw new ServiceException("销售订单行已开票数量变化（可能已超开或已被回退），请刷新后重试: lineId=" + soLineId);
            }
            log.warn("回退已开票数量未命中（可能未占用过或已为0），跳过: soLineId={}, delta={}", soLineId, delta.toPlainString());
        }
        return affected;
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 发起审批流程（Flowable 工作流回调）：校验发票存在并启动对应审批流，注入流程变量。
     *
     * @param invoiceIdStr 发票ID（字符串）
     * @param selectFlowId 流程定义ID
     * @return 启动成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String invoiceIdStr, String selectFlowId) {
        Long invoiceId = Long.parseLong(invoiceIdStr);
        SoInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("销售发票不存在");
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (invoice.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(invoice.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", invoiceIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("invoiceId", invoice.getInvoiceId());
        variables.put("invoiceNo", invoice.getInvoiceNo());
        variables.put("clientName", invoice.getClientName());
        variables.put("totalAmount", invoice.getTotalAmount());
        R<Void> result = processService.startProcessByDataId(invoiceIdStr, selectFlowId, "soInvoiceService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    /**
     * 获取发票对应的流程实例ID（运行时优先，其次历史）。
     *
     * @param invoiceIdStr 发票ID（字符串）
     * @return 流程实例ID，无则返回 null
     */
    @Override
    public String getProcInsId(String invoiceIdStr) {
        return procInsIdHelper.resolve(invoiceIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(invoiceIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 审批流节点处理完成后的回调：根据审批动作更新发票状态。
     *
     * <p><b>条件更新 + 事件与状态变更绑定</b>：只在发票仍处于「流程在途」状态
     * （DRAFT/APPROVING/REJECTED）时才改写，且**仅当状态真的变为 ISSUED 时才发布
     * {@link SoInvoiceIssuedEvent}**。原实现是无条件 {@code updateById} 且只要
     * {@code actStatus=pass} 就发事件，存在重复过账风险：</p>
     * <ul>
     *   <li>发票已 ISSUED（已过账总账），随后流程被撤销/撤回 → 回调把状态改回 DRAFT，
     *       界面显示未开具而总账凭证仍在；用户再次提交审批 → 再次 ISSUED →
     *       <b>再次发布 SoInvoiceIssuedEvent，产生重复总账凭证</b>；</li>
     *   <li>已核销（VERIFIED）或已作废（CANCELLED）的发票同样会被回调覆盖。</li>
     * </ul>
     *
     * @param business 工作流业务对象（含审批状态与数据ID）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long invoiceId = Long.parseLong(business.getDataId());
        SoInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            return;
        }
        String actStatus = business.getActStatus();
        String newStatus;
        if (ActStatus.doing.equals(actStatus)) {
            newStatus = STATUS_APPROVING;
        } else if (ActStatus.pass.equals(actStatus)) {
            newStatus = STATUS_ISSUED;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            newStatus = STATUS_REJECTED;
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            newStatus = STATUS_DRAFT;
        } else {
            return;
        }
        int updated = baseMapper.update(null,
            Wrappers.<SoInvoice>lambdaUpdate()
                .set(SoInvoice::getStatus, newStatus)
                .eq(SoInvoice::getInvoiceId, invoiceId)
                .in(SoInvoice::getStatus, STATUS_DRAFT, STATUS_APPROVING, STATUS_REJECTED));
        if (updated == 0) {
            log.warn("发票[{}]当前状态[{}]不允许流程回调改写为[{}]，忽略本次变更（不发布过账事件）",
                invoiceId, invoice.getStatus(), newStatus);
            return;
        }
        // 只有本次真的流转到 ISSUED 才过账，避免重复回调导致重复凭证
        if (STATUS_ISSUED.equals(newStatus)) {
            applicationEventPublisher.publishEvent(
                new SoInvoiceIssuedEvent(this, invoiceId, TenantHelper.getTenantId()));
        }
    }

    /**
     * 根据数据ID返回发票详情（供审批详情展示）。
     *
     * @param dataId 业务数据ID
     * @return 发票业务对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取审批任务所需的表单变量（当前实现无需额外变量，返回 null）。
     *
     * @param taskNameId 任务节点ID
     * @param values     已有流程变量
     * @return 任务表单变量，未实现返回 null
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取审批任务的候选处理人（当前实现未指定，返回 null）。
     *
     * @param taskNameId 任务节点ID
     * @param values     流程变量
     * @return 候选用户名列表，未实现返回 null
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoInvoice> buildQueryWrapper(SoInvoiceBo bo) {
        LambdaQueryWrapper<SoInvoice> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getInvoiceNo()), SoInvoice::getInvoiceNo, bo.getInvoiceNo());
        lqw.eq(bo.getClientId() != null, SoInvoice::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoInvoice::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getInvoiceType()), SoInvoice::getInvoiceType, bo.getInvoiceType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoInvoice::getStatus, bo.getStatus());
        lqw.eq(bo.getSoId() != null, SoInvoice::getSoId, bo.getSoId());
        lqw.eq(bo.getContractId() != null, SoInvoice::getContractId, bo.getContractId());
        lqw.orderByDesc(SoInvoice::getCreateTime);
        return lqw;
    }
}

package com.nexus.erp.fin.arap.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.domain.FinSettlement;
import com.nexus.erp.fin.arap.domain.bo.FinSettlementBo;
import com.nexus.erp.fin.arap.domain.vo.FinSettlementVo;
import com.nexus.erp.fin.arap.domain.vo.OpenItemVo;
import com.nexus.erp.fin.arap.mapper.FinReceiptMapper;
import com.nexus.erp.fin.arap.mapper.FinSettlementMapper;
import com.nexus.erp.fin.arap.service.IFinSettlementService;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherLineMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherMapper;
import com.nexus.erp.pu.invoice.domain.PuInvoice;
import com.nexus.erp.pu.invoice.domain.bo.PuInvoiceBo;
import com.nexus.erp.pu.invoice.domain.vo.PuInvoiceVo;
import com.nexus.erp.pu.invoice.mapper.PuInvoiceMapper;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.service.ISoInvoiceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 应收应付核销 Service 实现
 * <p>复用 so/pu 的 verifyInvoice 原语(原子累加+乐观锁+状态翻转);全额核销时清账 gl_voucher_line。
 * 不改 F0 凭证引擎与余额服务(凭证由 Poster 经 createAndPost 生成,清账只更新未清项标志位)。</p>
 * <p>反核销({@link #unsettle(Long)}) 是 settle 的逆原语:因 so/pu 侧未暴露「反核销」方法且该包不在本次改动范围，
 * 这里直接经 {@code SoInvoiceMapper}/{@code PuInvoiceMapper} 做等价的条件更新(同一张表、同样的
 * {@code version} 乐观锁与 {@code >=} 金额守卫口径)。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FinSettlementServiceImpl implements IFinSettlementService {

    private final FinSettlementMapper baseMapper;
    private final FinReceiptMapper receiptMapper;
    private final ISoInvoiceService soInvoiceService;
    private final IPuInvoiceService puInvoiceService;
    private final SoInvoiceMapper soInvoiceMapper;
    private final PuInvoiceMapper puInvoiceMapper;
    private final GlVoucherMapper voucherMapper;
    private final GlVoucherLineMapper voucherLineMapper;
    private final GlAccountMapper accountMapper;

    /** 核销:写 fin_settlement + 调发票 verifyInvoice + 累加收付款单 settled_amount + 满额置 SETTLED + 清 gl_voucher_line 未清项 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(FinSettlementBo bo) {
        Long invoiceId = bo.getInvoiceId();
        Long receiptId = bo.getReceiptId();
        BigDecimal settleAmount = bo.getSettleAmount();
        String direction = bo.getDirection();
        if (settleAmount == null || settleAmount.signum() <= 0) {
            throw new ServiceException("核销金额必须大于0");
        }
        // 精度对齐：收付款单/核销金额为 decimal(18,4)，凭证与余额表为 decimal(18,2)，
        // 统一按 2 位小数入账，防止明细与余额表对不平（如 10.125 → 凭证 10.13）
        settleAmount = settleAmount.setScale(2, RoundingMode.HALF_UP);
        if (invoiceId == null || receiptId == null || StringUtils.isBlank(direction)) {
            throw new ServiceException("发票/收付款单/方向不能为空");
        }

        // 校验收款单
        FinReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new ServiceException("收付款单不存在");
        }
        if (!ArapConstants.RECEIPT_STATUS_CONFIRMED.equals(receipt.getStatus())
            && !ArapConstants.RECEIPT_STATUS_SETTLED.equals(receipt.getStatus())) {
            throw new ServiceException("收付款单状态【{}】不可核销", receipt.getStatus());
        }
        BigDecimal receiptOpen = nz(receipt.getAmount()).subtract(nz(receipt.getSettledAmount()));
        if (receiptOpen.compareTo(settleAmount) < 0) {
            throw new ServiceException("收付款单未核销余额【{}】不足,本次核销【{}】", receiptOpen, settleAmount);
        }

        // 校验发票 + 取冗余字段
        String invoiceNo;
        String invoiceType;
        BigDecimal invoiceTotal;
        BigDecimal invoiceVerifiedBefore;
        String invoiceSource;
        if (ArapConstants.DIRECTION_AR.equals(direction)) {
            SoInvoiceVo inv = soInvoiceService.selectById(invoiceId);
            if (inv == null) {
                throw new ServiceException("销售发票不存在");
            }
            invoiceNo = inv.getInvoiceNo();
            invoiceType = inv.getInvoiceType();
            invoiceTotal = nz(inv.getTotalAmount());
            invoiceVerifiedBefore = nz(inv.getVerifiedAmount());
            invoiceSource = GlConstants.SOURCE_SO_INVOICE;
        } else {
            PuInvoiceVo inv = puInvoiceService.selectById(invoiceId);
            if (inv == null) {
                throw new ServiceException("采购发票不存在");
            }
            invoiceNo = inv.getInvoiceNo();
            invoiceType = inv.getInvoiceType();
            invoiceTotal = nz(inv.getTotalAmount());
            invoiceVerifiedBefore = nz(inv.getVerifiedAmount());
            invoiceSource = GlConstants.SOURCE_PU_INVOICE;
        }
        BigDecimal invoiceOpen = invoiceTotal.subtract(invoiceVerifiedBefore);
        if (invoiceOpen.compareTo(settleAmount) < 0) {
            throw new ServiceException("发票未核销余额【{}】不足,本次核销【{}】", invoiceOpen, settleAmount);
        }

        // 1. 写 fin_settlement
        FinSettlement s = new FinSettlement();
        s.setSettlementNo("HX" + IdWorker.getId());
        s.setDirection(direction);
        s.setInvoiceId(invoiceId);
        s.setInvoiceNo(invoiceNo);
        s.setInvoiceType(invoiceType);
        s.setReceiptId(receiptId);
        s.setSettleAmount(settleAmount);
        s.setSettleDate(bo.getSettleDate() != null ? bo.getSettleDate() : new Date());
        s.setOperator(bo.getOperator() != null ? bo.getOperator() : String.valueOf(LoginHelper.getUserId()));
        s.setRemark(bo.getRemark());
        baseMapper.insert(s);

        // 2. 调发票核销原语(原子累加 verified_amount + 状态翻转)
        if (ArapConstants.DIRECTION_AR.equals(direction)) {
            soInvoiceService.verifyInvoice(invoiceId, settleAmount, receiptId);
        } else {
            puInvoiceService.verifyInvoice(invoiceId, settleAmount, receiptId);
        }

        // 3. 累加 fin_receipt.settled_amount + 状态(全额转 SETTLED)
        BigDecimal newSettled = nz(receipt.getSettledAmount()).add(settleAmount);
        boolean receiptFully = newSettled.compareTo(nz(receipt.getAmount())) >= 0;
        String newReceiptStatus = receiptFully ? ArapConstants.RECEIPT_STATUS_SETTLED : ArapConstants.RECEIPT_STATUS_CONFIRMED;
        int rows = receiptMapper.update(null, Wrappers.<FinReceipt>lambdaUpdate()
            .eq(FinReceipt::getReceiptId, receiptId)
            .eq(FinReceipt::getVersion, receipt.getVersion())
            .setSql("settled_amount = settled_amount + " + settleAmount.toPlainString())
            .set(FinReceipt::getStatus, newReceiptStatus)
            .set(FinReceipt::getVersion, receipt.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("收付款单数据已变更,核销失败,请重试");
        }

        // 4. 全额核销时清账 gl_voucher_line(openItem=1 -> cleared=1, clearedBy=settlement_id)
        boolean invoiceFully = invoiceVerifiedBefore.add(settleAmount).compareTo(invoiceTotal) >= 0;
        Long reconAccountId = getAccountId(ArapConstants.DIRECTION_AR.equals(direction)
            ? ArapConstants.ACCT_AR : ArapConstants.ACCT_AP);
        if (invoiceFully) {
            clearReconLine(invoiceSource, invoiceId, reconAccountId, s.getSettlementId());
        }
        if (receiptFully) {
            clearReconLine(GlConstants.SOURCE_RECEIPT, receiptId, reconAccountId, s.getSettlementId());
        }
        log.info("核销完成: direction={}, invoiceId={}, receiptId={}, settleAmount={}, settlementId={}",
            direction, invoiceId, receiptId, settleAmount, s.getSettlementId());
    }

    /**
     * 反核销（FIN-ARAP-02）:{@link #settle(FinSettlementBo)} 的逆原语。
     * <p>全部走条件更新 + 乐观锁 + 金额守卫，任一步影响 0 行即抛异常由事务整体回滚：</p>
     * <ol>
     *   <li>发票 {@code verified_amount -= settle_amount}，状态回退（不再满额则回 ISSUED）；</li>
     *   <li>{@code fin_receipt.settled_amount -= settle_amount}，状态回退（不再满额则回 CONFIRMED）；</li>
     *   <li>{@code gl_voucher_line} 中 {@code cleared_by = settlementId} 的未清项复位（cleared=0, cleared_by=NULL）；</li>
     *   <li>逻辑删除 {@code fin_settlement}（重复调用查不到记录即拒绝，不会二次回滚金额）。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unsettle(Long settlementId) {
        FinSettlement s = baseMapper.selectById(settlementId);
        if (s == null) {
            throw new ServiceException("核销记录不存在或已反核销");
        }
        BigDecimal settleAmount = nz(s.getSettleAmount());
        if (settleAmount.signum() <= 0) {
            throw new ServiceException("核销金额异常,无法反核销");
        }
        // 1. 回退发票已核销额与状态
        reverseInvoiceVerify(s, settleAmount);

        // 2. 回退收付款单已结算额与状态(>= 守卫 + 乐观锁)
        FinReceipt receipt = receiptMapper.selectById(s.getReceiptId());
        if (receipt == null) {
            throw new ServiceException("收付款单不存在,无法反核销");
        }
        BigDecimal newSettled = nz(receipt.getSettledAmount()).subtract(settleAmount);
        if (newSettled.signum() < 0) {
            throw new ServiceException("收付款单已核销金额【{}】不足回退【{}】", receipt.getSettledAmount(), settleAmount);
        }
        String newStatus = newSettled.compareTo(nz(receipt.getAmount())) >= 0
            ? ArapConstants.RECEIPT_STATUS_SETTLED : ArapConstants.RECEIPT_STATUS_CONFIRMED;
        int rows = receiptMapper.update(null, Wrappers.<FinReceipt>lambdaUpdate()
            .eq(FinReceipt::getReceiptId, receipt.getReceiptId())
            .eq(FinReceipt::getVersion, receipt.getVersion())
            .ge(FinReceipt::getSettledAmount, settleAmount)
            .setSql("settled_amount = settled_amount - " + settleAmount.toPlainString())
            .set(FinReceipt::getStatus, newStatus)
            .set(FinReceipt::getVersion, receipt.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("收付款单数据已变更,反核销失败,请重试");
        }

        // 3. 复位本次核销清掉的未清项(gl_voucher_line)
        int clearedRows = voucherLineMapper.update(null, Wrappers.<GlVoucherLine>lambdaUpdate()
            .eq(GlVoucherLine::getClearedBy, settlementId)
            .set(GlVoucherLine::getCleared, GlConstants.NO)
            .setSql("cleared_by = NULL"));

        // 4. 逻辑删除核销记录
        baseMapper.deleteById(settlementId);
        log.info("反核销完成: settlementId={}, direction={}, invoiceId={}, receiptId={}, settleAmount={}, 复位未清项={}行",
            settlementId, s.getDirection(), s.getInvoiceId(), s.getReceiptId(), settleAmount, clearedRows);
    }

    /** 反向冲减发票已核销额(与 so/pu 的 verifyInvoice 对称:条件更新 + >= 守卫 + version 乐观锁 + 状态回退) */
    private void reverseInvoiceVerify(FinSettlement s, BigDecimal settleAmount) {
        if (ArapConstants.DIRECTION_AR.equals(s.getDirection())) {
            SoInvoice inv = soInvoiceMapper.selectById(s.getInvoiceId());
            if (inv == null) {
                throw new ServiceException("销售发票不存在,无法反核销");
            }
            if (!ISoInvoiceService.STATUS_ISSUED.equals(inv.getStatus())
                && !ISoInvoiceService.STATUS_VERIFIED.equals(inv.getStatus())) {
                throw new ServiceException("销售发票状态【{}】不允许反核销", inv.getStatus());
            }
            BigDecimal newVerified = nz(inv.getVerifiedAmount()).subtract(settleAmount);
            if (newVerified.signum() < 0) {
                throw new ServiceException("销售发票已核销金额【{}】不足回退【{}】", inv.getVerifiedAmount(), settleAmount);
            }
            String newStatus = newVerified.compareTo(nz(inv.getTotalAmount())) >= 0
                ? ISoInvoiceService.STATUS_VERIFIED : ISoInvoiceService.STATUS_ISSUED;
            int rows = soInvoiceMapper.update(null, Wrappers.<SoInvoice>lambdaUpdate()
                .eq(SoInvoice::getInvoiceId, inv.getInvoiceId())
                .eq(SoInvoice::getVersion, inv.getVersion())
                .ge(SoInvoice::getVerifiedAmount, settleAmount)
                .setSql("verified_amount = verified_amount - " + settleAmount.toPlainString())
                .set(SoInvoice::getStatus, newStatus)
                .set(SoInvoice::getVersion, inv.getVersion() + 1));
            if (rows == 0) {
                throw new ServiceException("销售发票数据已变更,反核销失败,请重试");
            }
            return;
        }
        PuInvoice inv = puInvoiceMapper.selectById(s.getInvoiceId());
        if (inv == null) {
            throw new ServiceException("采购发票不存在,无法反核销");
        }
        if (!IPuInvoiceService.STATUS_ISSUED.equals(inv.getStatus())
            && !IPuInvoiceService.STATUS_VERIFIED.equals(inv.getStatus())) {
            throw new ServiceException("采购发票状态【{}】不允许反核销", inv.getStatus());
        }
        BigDecimal newVerified = nz(inv.getVerifiedAmount()).subtract(settleAmount);
        if (newVerified.signum() < 0) {
            throw new ServiceException("采购发票已核销金额【{}】不足回退【{}】", inv.getVerifiedAmount(), settleAmount);
        }
        String newStatus = newVerified.compareTo(nz(inv.getTotalAmount())) >= 0
            ? IPuInvoiceService.STATUS_VERIFIED : IPuInvoiceService.STATUS_ISSUED;
        int rows = puInvoiceMapper.update(null, Wrappers.<PuInvoice>lambdaUpdate()
            .eq(PuInvoice::getInvoiceId, inv.getInvoiceId())
            .eq(PuInvoice::getVersion, inv.getVersion())
            .ge(PuInvoice::getVerifiedAmount, settleAmount)
            .setSql("verified_amount = verified_amount - " + settleAmount.toPlainString())
            .set(PuInvoice::getStatus, newStatus)
            .set(PuInvoice::getVersion, inv.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("采购发票数据已变更,反核销失败,请重试");
        }
    }

    /** 分页查询核销记录 */
    @Override
    public TableDataInfo<FinSettlementVo> queryPageList(FinSettlementBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FinSettlement> lqw = buildQueryWrapper(bo);
        Page<FinSettlementVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /** 查询核销记录列表 */
    @Override
    public List<FinSettlementVo> queryList(FinSettlementBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /** 查未清项:so/pu 发票未清 + fin_receipt 未清,合并返回 */
    @Override
    public List<OpenItemVo> queryOpenItems(String direction, Long partnerId) {
        List<OpenItemVo> result = new ArrayList<>();
        if (ArapConstants.DIRECTION_AR.equals(direction)) {
            SoInvoiceBo soBo = new SoInvoiceBo();
            if (partnerId != null) {
                soBo.setClientId(partnerId);
            }
            for (SoInvoiceVo inv : soInvoiceService.selectList(soBo)) {
                addIfOpen(result, ArapConstants.ITEM_INVOICE, inv.getInvoiceId(), inv.getInvoiceNo(),
                    direction, inv.getClientId(), nz(inv.getTotalAmount()), nz(inv.getVerifiedAmount()), inv.getInvoiceDate());
            }
        } else {
            PuInvoiceBo puBo = new PuInvoiceBo();
            if (partnerId != null) {
                puBo.setVendorId(partnerId);
            }
            for (PuInvoiceVo inv : puInvoiceService.selectList(puBo)) {
                addIfOpen(result, ArapConstants.ITEM_INVOICE, inv.getInvoiceId(), inv.getInvoiceNo(),
                    direction, inv.getVendorId(), nz(inv.getTotalAmount()), nz(inv.getVerifiedAmount()), inv.getInvoiceDate());
            }
        }
        appendOpenReceipts(direction, partnerId, result);
        return result;
    }

    /** 追加未清收付款单到未清项列表(已核销<金额的 CONFIRMED/SETTLED 单据) */
    private void appendOpenReceipts(String direction, Long partnerId, List<OpenItemVo> result) {
        LambdaQueryWrapper<FinReceipt> lqw = Wrappers.lambdaQuery();
        lqw.eq(FinReceipt::getDirection, direction);
        lqw.in(FinReceipt::getStatus, ArapConstants.RECEIPT_STATUS_CONFIRMED, ArapConstants.RECEIPT_STATUS_SETTLED);
        if (ArapConstants.DIRECTION_AR.equals(direction)) {
            lqw.eq(partnerId != null, FinReceipt::getClientId, partnerId);
        } else {
            lqw.eq(partnerId != null, FinReceipt::getVendorId, partnerId);
        }
        for (FinReceipt r : receiptMapper.selectList(lqw)) {
            Long pid = ArapConstants.DIRECTION_AR.equals(direction) ? r.getClientId() : r.getVendorId();
            addIfOpen(result, ArapConstants.ITEM_RECEIPT, r.getReceiptId(), r.getReceiptNo(),
                direction, pid, nz(r.getAmount()), nz(r.getSettledAmount()), r.getReceiptDate());
        }
    }

    /** 未清余额>0 则追加到未清项列表 */
    private void addIfOpen(List<OpenItemVo> result, String itemType, Long itemId, String itemNo,
                           String direction, Long partnerId, BigDecimal total, BigDecimal settled, Date itemDate) {
        BigDecimal open = total.subtract(settled);
        if (open.signum() <= 0) {
            return;
        }
        OpenItemVo item = new OpenItemVo();
        item.setItemType(itemType);
        item.setItemId(itemId);
        item.setItemNo(itemNo);
        item.setDirection(direction);
        item.setPartnerId(partnerId);
        item.setTotalAmount(total);
        item.setSettledAmount(settled);
        item.setOpenAmount(open);
        item.setItemDate(itemDate);
        result.add(item);
    }

    /** 清账:按来源+来源单据定位统驭科目未清项凭证行,置 cleared=1 */
    private void clearReconLine(String voucherSource, Long sourceId, Long reconAccountId, Long settlementId) {
        GlVoucher voucher = voucherMapper.selectOne(Wrappers.<GlVoucher>lambdaQuery()
            .eq(GlVoucher::getVoucherSource, voucherSource)
            .eq(GlVoucher::getSourceId, sourceId)
            .last("limit 1"));
        if (voucher == null) {
            // 凭证未生成：当前过账链路为同步制单，正常不会走到这里；
            // 若走到说明制单失败，抛异常回滚核销，避免"已核销但未清项永久残留"的账实不符
            throw new ServiceException("清账失败:凭证未生成(source={}, sourceId={})，核销已回滚", voucherSource, sourceId);
        }
        int n = voucherLineMapper.update(null, Wrappers.<GlVoucherLine>lambdaUpdate()
            .eq(GlVoucherLine::getVoucherId, voucher.getVoucherId())
            .eq(GlVoucherLine::getAccountId, reconAccountId)
            .eq(GlVoucherLine::getOpenItem, GlConstants.YES)
            .eq(GlVoucherLine::getCleared, GlConstants.NO)
            .set(GlVoucherLine::getCleared, GlConstants.YES)
            .set(GlVoucherLine::getClearedBy, settlementId));
        log.info("清账完成: source={}, sourceId={}, settlementId={}, 清账行数={}", voucherSource, sourceId, settlementId, n);
    }

    /** 按科目编码查科目ID,不存在抛异常 */
    private Long getAccountId(String code) {
        GlAccount acc = accountMapper.selectOne(Wrappers.<GlAccount>lambdaQuery()
            .eq(GlAccount::getAccountCode, code));
        if (acc == null) {
            throw new ServiceException("科目不存在:{}", code);
        }
        return acc.getAccountId();
    }

    /** 构建核销记录查询条件,按创建时间倒序 */
    private LambdaQueryWrapper<FinSettlement> buildQueryWrapper(FinSettlementBo bo) {
        LambdaQueryWrapper<FinSettlement> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(StringUtils.isNotBlank(bo.getDirection()), FinSettlement::getDirection, bo.getDirection());
            lqw.like(StringUtils.isNotBlank(bo.getInvoiceNo()), FinSettlement::getInvoiceNo, bo.getInvoiceNo());
            lqw.eq(bo.getInvoiceId() != null, FinSettlement::getInvoiceId, bo.getInvoiceId());
            lqw.eq(bo.getReceiptId() != null, FinSettlement::getReceiptId, bo.getReceiptId());
        }
        lqw.orderByDesc(FinSettlement::getCreateTime);
        return lqw;
    }

    /** null->BigDecimal.ZERO,防空指针 */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

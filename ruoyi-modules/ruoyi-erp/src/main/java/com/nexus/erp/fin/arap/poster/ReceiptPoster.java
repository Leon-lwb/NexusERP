package com.nexus.erp.fin.arap.poster;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.mapper.FinReceiptMapper;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.poster.AccountingPoster;
import com.nexus.erp.fin.gl.poster.PostingContext;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 收付款单过账 Poster:收付款单 CONFIRMED -> 银行存款 <-> 应收/应付账款。
 * <p>AR 收款:借 银行存款 1002 / 贷 应收账款 1122(统驭 AR,未清项,往来=客户)</p>
 * <p>AP 付款:借 应付账款 2202(统驭 AP,未清项,往来=供应商) / 贷 银行存款 1002</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Component
public class ReceiptPoster implements AccountingPoster {

    private final FinReceiptMapper receiptMapper;
    private final IGlVoucherService voucherService;
    private final GlAccountMapper accountMapper;

    /** 返回 SOURCE_RECEIPT,由 GlPostingConsumer 按 sourceType 路由 */
    @Override
    public String sourceType() {
        return GlConstants.SOURCE_RECEIPT;
    }

    /**
     * 收付款单过账:CONFIRMED 时构建凭证并过账。
     * AR 收款:借 银行存款 1002/贷 应收账款 1122(统驭,未清项,往来=客户);
     * AP 付款:借 应付账款 2202(统驭,未清项,往来=供应商)/贷 银行存款 1002。
     */
    @Override
    public void post(PostingContext ctx) {
        FinReceipt receipt = receiptMapper.selectById(ctx.getSourceId());
        if (receipt == null) {
            throw new ServiceException("收付款单不存在: {}", ctx.getSourceId());
        }
        boolean isAR = ArapConstants.DIRECTION_AR.equals(receipt.getDirection());
        String summary = (isAR ? "应收收款 " : "应付付款 ") + receipt.getReceiptNo();
        Long bankId = getAccountId(ArapConstants.ACCT_BANK);
        Long reconId = getAccountId(isAR ? ArapConstants.ACCT_AR : ArapConstants.ACCT_AP);
        Long partnerId = isAR ? receipt.getClientId() : receipt.getVendorId();
        String partnerType = isAR ? ArapConstants.PARTNER_CLIENT : ArapConstants.PARTNER_VENDOR;
        BigDecimal amount = nz(receipt.getAmount());
        List<GlVoucherLine> lines = new ArrayList<>();
        if (isAR) {
            // 借:银行存款 / 贷:应收账款(统驭)
            lines.add(line(bankId, amount, null, summary));
            lines.add(reconLine(reconId, null, amount, partnerId, partnerType, summary));
        } else {
            // 借:应付账款(统驭) / 贷:银行存款
            lines.add(reconLine(reconId, amount, null, partnerId, partnerType, summary));
            lines.add(line(bankId, null, amount, summary));
        }
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(ctx.getVoucherDate() != null ? ctx.getVoucherDate() : receipt.getReceiptDate());
        req.setVoucherWord(isAR ? "R" : "P");
        req.setVoucherSource(GlConstants.SOURCE_RECEIPT);
        req.setSourceId(receipt.getReceiptId());
        req.setSourceNo(receipt.getReceiptNo());
        req.setSummary(summary);
        req.setLines(lines);
        voucherService.createAndPost(req);
        log.info("收付款单过账: receiptId={}, direction={}, amount={}", receipt.getReceiptId(), receipt.getDirection(), amount);
    }

    /** 构建普通凭证行(非统驭) */
    private GlVoucherLine line(Long accountId, BigDecimal debit, BigDecimal credit, String summary) {
        GlVoucherLine l = new GlVoucherLine();
        l.setAccountId(accountId);
        l.setDebit(debit);
        l.setCredit(credit);
        l.setSummary(summary);
        return l;
    }

    /** 构建统驭科目凭证行(未清项,记录往来单位) */
    private GlVoucherLine reconLine(Long accountId, BigDecimal debit, BigDecimal credit,
                                    Long partnerId, String partnerType, String summary) {
        GlVoucherLine l = line(accountId, debit, credit, summary);
        l.setPartnerId(partnerId);
        l.setPartnerType(partnerType);
        l.setOpenItem(GlConstants.YES);
        return l;
    }

    /** 按科目编码查询科目ID,不存在抛 ServiceException */
    private Long getAccountId(String code) {
        GlAccount acc = accountMapper.selectOne(Wrappers.<GlAccount>lambdaQuery()
            .eq(GlAccount::getAccountCode, code));
        if (acc == null) {
            throw new ServiceException("科目不存在: {}", code);
        }
        return acc.getAccountId();
    }

    /** null->BigDecimal.ZERO,防空指针 */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

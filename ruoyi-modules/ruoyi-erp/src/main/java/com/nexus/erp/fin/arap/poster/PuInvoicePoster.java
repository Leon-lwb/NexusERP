package com.nexus.erp.fin.arap.poster;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.poster.AccountingPoster;
import com.nexus.erp.fin.gl.poster.PostingContext;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import com.nexus.erp.pu.invoice.domain.vo.PuInvoiceVo;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购发票过账 Poster:发票 ISSUED(收票) -> 确认存货 + 进项税 + 应付账款。
 * <p>借:库存商品 1405 + 进项税额 2221.02 / 贷:应付账款 2202(统驭 AP,未清项,往来=供应商)</p>
 * <p>认证子状态机(CERTIFIED/DEDUCTED)独立管税务抵扣,不在 GL 重复过账。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Component
public class PuInvoicePoster implements AccountingPoster {

    private final IPuInvoiceService puInvoiceService;
    private final IGlVoucherService voucherService;
    private final GlAccountMapper accountMapper;

    /** 返回 SOURCE_PU_INVOICE,由 GlPostingConsumer 按 sourceType 路由 */
    @Override
    public String sourceType() {
        return GlConstants.SOURCE_PU_INVOICE;
    }

    /**
     * 采购发票过账:ISSUED 时构建凭证并过账。
     * 借:库存商品 1405+进项税额 2221.02/贷:应付账款 2202(统驭 AP,未清项,往来=供应商)。
     * 认证子状态机独立管理税务抵扣,不在此重复过账。
     */
    @Override
    public void post(PostingContext ctx) {
        PuInvoiceVo inv = puInvoiceService.selectById(ctx.getSourceId());
        if (inv == null) {
            throw new ServiceException("采购发票不存在: {}", ctx.getSourceId());
        }
        String summary = "采购发票 " + inv.getInvoiceNo();
        List<GlVoucherLine> lines = new ArrayList<>();
        // 借:库存商品(不含税)
        lines.add(line(getAccountId(ArapConstants.ACCT_INVENTORY), nz(inv.getAmount()), null, summary));
        // 借:进项税额
        BigDecimal tax = nz(inv.getTaxAmount());
        if (tax.signum() != 0) {
            lines.add(line(getAccountId(ArapConstants.ACCT_TAX_INPUT), tax, null, summary));
        }
        // 贷:应付账款(统驭 AP,未清项,往来=供应商)
        lines.add(reconLine(getAccountId(ArapConstants.ACCT_AP), null, nz(inv.getTotalAmount()),
            inv.getVendorId(), ArapConstants.PARTNER_VENDOR, summary));
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(ctx.getVoucherDate() != null ? ctx.getVoucherDate() : inv.getInvoiceDate());
        req.setVoucherWord("J");
        req.setVoucherSource(GlConstants.SOURCE_PU_INVOICE);
        req.setSourceId(inv.getInvoiceId());
        req.setSourceNo(inv.getInvoiceNo());
        req.setSummary(summary);
        req.setLines(lines);
        voucherService.createAndPost(req);
        log.info("采购发票过账: invoiceId={}, invoiceNo={}, total={}", inv.getInvoiceId(), inv.getInvoiceNo(), inv.getTotalAmount());
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

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
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.service.ISoInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售发票过账 Poster:发票 ISSUED -> 确认应收账款 + 销项税 + 收入。
 * <p>借:应收账款 1122(统驭 AR,未清项,往来=客户) / 贷:主营业务收入 6001 + 销项税额 2221.01</p>
 * <p>红字发票(总额为负)自动生成红字凭证,借贷方向不变、金额取负。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Component
public class SoInvoicePoster implements AccountingPoster {

    private final ISoInvoiceService soInvoiceService;
    private final IGlVoucherService voucherService;
    private final GlAccountMapper accountMapper;

    /** 返回 SOURCE_SO_INVOICE,由 GlPostingConsumer 按 sourceType 路由 */
    @Override
    public String sourceType() {
        return GlConstants.SOURCE_SO_INVOICE;
    }

    /**
     * 销售发票过账:ISSUED 时构建凭证并过账。
     * 借:应收账款 1122(统驭 AR,未清项,往来=客户)/贷:主营业务收入 6001+销项税额 2221.01。
     * 红字发票金额取负,借贷方向不变。
     */
    @Override
    public void post(PostingContext ctx) {
        SoInvoiceVo inv = soInvoiceService.selectById(ctx.getSourceId());
        if (inv == null) {
            throw new ServiceException("销售发票不存在: {}", ctx.getSourceId());
        }
        String summary = "销售发票 " + inv.getInvoiceNo();
        List<GlVoucherLine> lines = new ArrayList<>();
        // 借:应收账款(统驭 AR,未清项,往来=客户)
        lines.add(reconLine(getAccountId(ArapConstants.ACCT_AR), nz(inv.getTotalAmount()), null,
            inv.getClientId(), ArapConstants.PARTNER_CLIENT, summary));
        // 贷:主营业务收入(不含税)
        lines.add(line(getAccountId(ArapConstants.ACCT_REVENUE), null, nz(inv.getAmount()), summary));
        // 贷:销项税额
        BigDecimal tax = nz(inv.getTaxAmount());
        if (tax.signum() != 0) {
            lines.add(line(getAccountId(ArapConstants.ACCT_TAX_OUTPUT), null, tax, summary));
        }
        VoucherBuildRequest req = new VoucherBuildRequest();
        req.setVoucherDate(ctx.getVoucherDate() != null ? ctx.getVoucherDate() : inv.getInvoiceDate());
        req.setVoucherWord("J");
        req.setVoucherSource(GlConstants.SOURCE_SO_INVOICE);
        req.setSourceId(inv.getInvoiceId());
        req.setSourceNo(inv.getInvoiceNo());
        req.setSummary(summary);
        req.setLines(lines);
        voucherService.createAndPost(req);
        log.info("销售发票过账: invoiceId={}, invoiceNo={}, total={}", inv.getInvoiceId(), inv.getInvoiceNo(), inv.getTotalAmount());
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

package com.nexus.erp.fin.gl.poster;

/**
 * 业务自动制单扩展点(AccountingPoster)。
 * <p>F0 仅定义 SPI + Registry + GlPostingConsumer;F1+ 各业务实现此接口
 * (SoInvoicePoster/PuInvoicePoster/ReceiptPoster/DepreciationPoster/CostPoster),
 * 在 {@link #post(PostingContext)} 中构建分录行并调 {@code IGlVoucherService.createAndPost}。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface AccountingPoster {

    /** 来源类型,对应 gl_voucher.voucher_source */
    String sourceType();

    /**
     * 根据来源单据构建并过账凭证。
     *
     * @param ctx 过账上下文(来源单据信息)
     */
    void post(PostingContext ctx);
}

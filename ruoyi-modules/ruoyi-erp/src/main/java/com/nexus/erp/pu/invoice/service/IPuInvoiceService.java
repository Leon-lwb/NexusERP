package com.nexus.erp.pu.invoice.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.invoice.domain.bo.PuInvoiceBo;
import com.nexus.erp.pu.invoice.domain.vo.MatchResultVo;
import com.nexus.erp.pu.invoice.domain.vo.PuInvoiceVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 采购进项发票Service接口
 * <p>镜像 {@code ISoInvoiceService},状态机含进项认证子状态机(cert_status)。
 * 进项发票收票登记无需内部审批流转,故无 APPROVING/REJECTED 与 Flowable 回调。</p>
 *
 * @author nexus
 * @date 2026-07-16
 */
public interface IPuInvoiceService {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 已收(发票已收取) */
    String STATUS_ISSUED = "ISSUED";
    /** 已核销(已付款核销完毕) */
    String STATUS_VERIFIED = "VERIFIED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";

    /** 认证状态:未认证 */
    String CERT_UNCERTIFIED = "UNCERTIFIED";
    /** 认证状态:已认证 */
    String CERT_CERTIFIED = "CERTIFIED";
    /** 认证状态:已抵扣 */
    String CERT_DEDUCTED = "DEDUCTED";
    /** 认证状态:已转出(退货进项转出) */
    String CERT_TRANSFERRED = "TRANSFERRED";

    /**
     * 分页查询采购进项发票列表
     */
    TableDataInfo<PuInvoiceVo> selectPageList(PuInvoiceBo bo, PageQuery pageQuery);

    /**
     * 查询采购进项发票列表(不分页)
     */
    List<PuInvoiceVo> selectList(PuInvoiceBo bo);

    /**
     * 根据ID查询采购进项发票(含明细行)
     */
    PuInvoiceVo selectById(Long id);

    /**
     * 新增进项发票(默认草稿/未认证),含明细行
     */
    int insertByBo(PuInvoiceBo bo);

    /**
     * 修改进项发票(差量更新明细行 + 头乐观锁),仅草稿可编辑
     */
    int updateByBo(PuInvoiceBo bo);

    /**
     * 批量删除进项发票(仅草稿可删,删前回退采购订单行已开票数量)
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据采购订单生成发票草稿(按"已收货未开票"自动汇总)。
     * <p>镜像 so 的 generateDraftFromOrder:按行 {@code received_quantity - invoiced_qty >0}
     * 生成 DRAFT,销售方四要素从 BdVendor 取,原子累加 {@code pu_purchase_order_line.invoiced_qty} 防重复开票。</p>
     *
     * @param poId 采购订单ID
     * @return 新建的发票ID;订单不存在/无可开票行则返回 null
     */
    Long generateDraftFromPo(Long poId);

    /**
     * 收票确认:DRAFT -> ISSUED(发票已收取登记)
     */
    void receiveInvoice(Long invoiceId);

    /**
     * 三单匹配校验(PO + 收货 + 发票):物料/数量(发票≤收货)/单价/税率/税额/价税合计一致性。
     * <p>仅对带有 po_line_id 的发票行校验;无关联行的发票(纯手工)跳过匹配。</p>
     *
     * @param invoiceId 发票ID
     * @return 匹配结果(含差异明细)
     */
    MatchResultVo matchThreeWay(Long invoiceId);

    /**
     * 核销发票(原子累加 verified_amount,达价税合计则转 VERIFIED)。
     * <p>前置三单匹配校验(见 6.2):若发票带 po_line_id 关联行且匹配失败,抛异常阻止核销。</p>
     *
     * @param invoiceId    发票ID
     * @param verifyAmount 本次核销金额
     * @param receiptId    关联收付款单ID(P3 核销记录用,P2 可传 null)
     */
    void verifyInvoice(Long invoiceId, BigDecimal verifyAmount, Long receiptId);

    /**
     * 进项认证:UNCERTIFIED -> CERTIFIED,记录认证日期与可抵扣税额(默认全额=税额)
     */
    void certifyInvoice(Long invoiceId);

    /**
     * 进项抵扣:CERTIFIED -> DEDUCTED
     */
    void deductInvoice(Long invoiceId);

    /**
     * 作废发票(仅 ISSUED/DRAFT 可作废;回退采购订单行 invoiced_qty)
     *
     * @param invoiceId 发票ID
     * @param reason    作废原因
     */
    void cancelInvoice(Long invoiceId, String reason);

    /**
     * 红冲发票(全量红冲,镜像 so):复制原行取负生成红字发票(red_flag=1),
     * 冲减原发票 verified_amount 与采购订单行 invoiced_qty;原发票若已抵扣则 cert_status -> TRANSFERRED。
     *
     * @param originalInvoiceId 原蓝字发票ID
     * @param reason            红冲原因
     * @return 红字发票ID
     */
    Long redFlush(Long originalInvoiceId, String reason);

    /**
     * 退货进项转出(部分红冲):按退货数量生成红字发票,原发票 cert_status -> TRANSFERRED,
     * 冲减原发票 verified_amount 与采购订单行 invoiced_qty。由 {@code PuPurchaseReturnServiceImpl.execute} 调用。
     *
     * @param originalInvoiceId 原蓝字发票ID
     * @param poLineReturnQty   采购订单行ID -> 退货数量(仅红冲这些行,按退货数量取负)
     * @return 红字发票ID;原发票不存在/无可红冲行则返回 null
     */
    Long redFlushForReturn(Long originalInvoiceId, Map<Long, BigDecimal> poLineReturnQty);

    /**
     * 退货进项转出(按采购订单行查找覆盖的已抵扣蓝字进项发票并部分红冲)。
     * <p>由 {@code PuPurchaseReturnServiceImpl.execute} 调用;仅 cert_status=DEDUCTED 的蓝字发票触发转出,
     * 未抵扣的发票不转出(无进项可转)。封装 pu_invoice 查询,退货服务无需直连发票表。</p>
     *
     * @param poLineReturnQty 采购订单行ID -> 退货数量
     * @return 生成的红字发票数量
     */
    int redFlushForReturnByPoLines(Map<Long, BigDecimal> poLineReturnQty);
}

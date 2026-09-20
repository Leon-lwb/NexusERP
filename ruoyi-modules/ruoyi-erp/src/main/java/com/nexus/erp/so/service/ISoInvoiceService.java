package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售发票Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface ISoInvoiceService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已开票 */
    String STATUS_ISSUED = "ISSUED";
    /** 已核销 */
    String STATUS_VERIFIED = "VERIFIED";
    /** 已驳回 */
    String STATUS_REJECTED = "REJECTED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询销售发票列表
     */
    TableDataInfo<SoInvoiceVo> selectPageList(SoInvoiceBo bo, PageQuery pageQuery);

    /**
     * 查询销售发票列表（不分页）
     */
    List<SoInvoiceVo> selectList(SoInvoiceBo bo);

    /**
     * 根据ID查询销售发票
     */
    SoInvoiceVo selectById(Long id);

    /**
     * 新增销售发票
     */
    int insertByBo(SoInvoiceBo bo);

    /**
     * 修改销售发票
     */
    int updateByBo(SoInvoiceBo bo);

    /**
     * 批量删除销售发票
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据销售订单生成发票草稿（按"已发货未开票"自动汇总）。
     * <p>由 {@code SalesOrderFulfilledEventListener} 在订单履约后异步触发。
     * 幂等：按行 {@code invoiced_qty = delivered_qty - invoiced_qty} 计算可开票量，
     * 全部 ≤0 则不生成；生成后原子累加 {@code so_sales_order_line.invoiced_qty} 防重复开票。</p>
     *
     * @param soId 销售订单ID
     * @return 新建的发票ID；订单不存在/无可开票行则返回 null
     */
    Long generateDraftFromOrder(Long soId);

    /**
     * 核销发票（原子累加 verified_amount，达价税合计则转 VERIFIED）。
     * <p>P1 仅实现回写原语；fin_settlement 记账由 P3 FinSettlementService.settle() 调用本方法。</p>
     *
     * @param invoiceId    发票ID
     * @param verifyAmount 本次核销金额
     * @param receiptId    关联收付款单ID（P3 核销记录用，P1 可传 null）
     */
    void verifyInvoice(Long invoiceId, BigDecimal verifyAmount, Long receiptId);

    /**
     * 作废发票（<b>仅 DRAFT 可作废</b>；回退 so_sales_order_line.invoiced_qty）。
     *
     * <p>已开票（ISSUED）/已核销（VERIFIED）不允许作废：作废不冲销总账，会让已过账凭证留在账上。
     * 这类发票必须走 {@link #redFlush} 红冲，由红字发票的过账冲销原凭证。</p>
     *
     * @param invoiceId 发票ID
     * @param reason    作废原因
     */
    void cancelInvoice(Long invoiceId, String reason);

    /**
     * 红冲发票（生成红字负数发票，冲减原发票 verified_amount 与订单行 invoiced_qty）。
     *
     * @param originalInvoiceId 原蓝字发票ID
     * @param reason            红冲原因
     * @return 红字发票ID
     */
    Long redFlush(Long originalInvoiceId, String reason);

    /**
     * 提交流程审批（启动 Flowable 审批流）
     *
     * @param invoiceIdStr  发票ID字符串
     * @param selectFlowId  选择的审批流ID
     * @return 是否成功
     */
    boolean submit(String invoiceIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     *
     * @param invoiceIdStr 发票ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String invoiceIdStr);
}

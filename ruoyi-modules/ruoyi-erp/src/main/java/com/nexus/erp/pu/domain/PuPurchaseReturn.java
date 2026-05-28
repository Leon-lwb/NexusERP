package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购退货单实体对象 pu_purchase_return
 * <p>
 * 存储采购退货单的主表信息，记录因质量问题、规格不符等原因将已采购物料退回供应商的业务单据。
 * 退货单关联原始采购订单，经确认和执行后扣减对应物料库存并生成库存交易记录。
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_purchase_return")
public class PuPurchaseReturn extends TenantEntity {

    /** 退货单ID，主键 */
    @TableId(value = "return_id")
    private Long returnId;

    /** 退货单号，系统自动生成 */
    private String returnNo;

    /** 来源采购订单ID，关联pu_purchase_order表 */
    private Long orderId;

    /** 来源采购订单号 */
    private String orderNo;

    /** 供应商ID，关联bd_vendor表 */
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 退货日期 */
    private Date returnDate;

    /** 退货原因 */
    private String returnReason;

    /** 退货总金额 */
    private BigDecimal totalAmount;

    /**
     * 退货单状态
     * <ul>
     *   <li>DRAFT - 草稿</li>
     *   <li>CONFIRMED - 已确认</li>
     *   <li>EXECUTED - 已执行</li>
     *   <li>CANCELLED - 已作废</li>
     * </ul>
     */
    private String status;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 扩展字段1 */
    private String attr1;

    /** 扩展字段2 */
    private String attr2;

    /** 扩展字段3 */
    private Long attr3;

    /** 扩展字段4 */
    private Long attr4;

    /** 退货明细行列表，非数据库字段 */
    @TableField(exist = false)
    private List<PuPurchaseReturnLine> puPurchaseReturnLineList;

    /** 删除标志（0正常 1已删除） */
    @TableLogic
    private String delFlag;
}

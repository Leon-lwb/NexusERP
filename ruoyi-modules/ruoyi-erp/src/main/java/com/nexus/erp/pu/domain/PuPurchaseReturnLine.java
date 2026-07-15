package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 采购退货单明细实体对象 pu_purchase_return_line
 * <p>
 * 存储采购退货单的物料明细行，每一行记录一个退货物料的退货数量、单价、金额及退货原因。
 * 一个退货单可包含多条明细，分别对应原始采购订单中的不同物料行。
 * 执行退货时，系统会根据明细行扣减对应物料的库存数量。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_purchase_return_line")
public class PuPurchaseReturnLine extends TenantEntity {

    /** 明细行ID，主键 */
    @TableId(value = "line_id")
    private Long lineId;

    /** 所属退货单ID，关联pu_purchase_return表 */
    private Long returnId;

    /** 来源采购订单明细行ID，关联pu_purchase_order_line表 */
    private Long orderLineId;

    /** 物料ID，关联bd_material表 */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料规格型号 */
    private String materialSpec;

    /** 计量单位 */
    private String unit;

    /** 退货数量 */
    private BigDecimal returnQuantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 税率 */
    private BigDecimal taxRate;

    /** 金额 */
    private BigDecimal amount;

    /** 行税额 */
    private BigDecimal taxAmount;

    /** 不含税行金额 */
    private BigDecimal amountExcludingTax;

    /** 退货原因 */
    private String returnReason;

    /** 排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 扩展字段1 */
    private String attr1;

    /** 扩展字段2 */
    private String attr2;

    /** 扩展字段3 */
    private Long attr3;

    /** 扩展字段4 */
    private Long attr4;

    /** 删除标志（0正常 1已删除） */
    @TableLogic
    private String delFlag;
}

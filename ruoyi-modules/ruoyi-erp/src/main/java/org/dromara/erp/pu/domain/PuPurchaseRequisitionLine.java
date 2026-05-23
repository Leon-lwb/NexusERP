package org.dromara.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 采购申请明细实体对象 pu_purchase_requisition_line
 * <p>
 * 存储采购申请单的物料明细行，每一行记录一个物料的申请数量、已批准数量、短缺数量等信息。
 * 一个采购申请可包含多条明细，分别对应不同的采购物料。
 * 明细行经审批后可用于生成采购订单明细。
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_purchase_requisition_line")
public class PuPurchaseRequisitionLine extends TenantEntity {

    /** 明细行ID，主键 */
    @TableId(value = "line_id")
    private Long lineId;

    /** 所属采购申请ID，关联pu_purchase_requisition表 */
    private Long requisitionId;

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

    /** 申请数量 */
    private BigDecimal applyQuantity;

    /** 已批准数量 */
    private BigDecimal approvedQuantity;

    /** 短缺数量 */
    private BigDecimal shortageQuantity;

    /** 需求日期 */
    private Date requiredDate;

    /** 来源类型，如手工录入、MRP生成等 */
    private String sourceType;

    /** 来源单据ID，如MRP建议ID */
    private Long sourceId;

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

package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 销售退货行对象 so_return_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_return_line")
public class SoReturnLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 退货ID */
    private Long returnId;
    /** 销售订单行ID */
    private Long soLineId;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 物料规格 */
    private String materialSpec;
    /** 基本计量单位ID */
    private String baseUomId;
    /** 基本计量单位名称 */
    private String baseUomName;
    /** 退货数量 */
    private BigDecimal returnQuantity;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 金额 */
    private BigDecimal amount;
    /** 税率 */
    private BigDecimal taxRate;
    /** 税额 */
    private BigDecimal taxAmount;
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

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

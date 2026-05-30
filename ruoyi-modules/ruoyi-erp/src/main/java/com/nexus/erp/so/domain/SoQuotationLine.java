package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售报价行对象 so_quotation_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_quotation_line")
public class SoQuotationLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 报价ID */
    private Long quotationId;
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
    /** 数量 */
    private BigDecimal quantity;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 金额 */
    private BigDecimal amount;
    /** 税率 */
    private BigDecimal taxRate;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 交货日期 */
    private Date deliveryDate;
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

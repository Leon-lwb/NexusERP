package com.nexus.erp.pu.domain;

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
 * 采购合同行对象 pu_contract_line
 *
 * @author nexus
 * @date 2026-06-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@TableName("pu_contract_line")
public class PuContractLine extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 合同ID */
    private Long contractId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料规格 */
    private String materialSpec;

    /** 计量单位 */
    private String unit;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private BigDecimal quantity;

    /** 行金额 */
    private BigDecimal lineAmount;

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

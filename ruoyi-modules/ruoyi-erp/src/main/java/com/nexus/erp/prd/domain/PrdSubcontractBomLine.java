package com.nexus.erp.prd.domain;

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
 * 委外订单BOM快照行对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("prd_subcontract_bom_line")
public class PrdSubcontractBomLine extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 快照行ID */
    Long lineId;
    /** 委外订单ID */
    Long subcontractId;
    /** 来源BOM行ID(bd_bom_detail) */
    Long bomDetailId;
    /** 子件物料ID */
    Long materialId;
    /** 物料编码 */
    String materialCode;
    /** 物料名称 */
    String materialName;
    /** 规格型号 */
    String materialSpec;
    /** 单位名称 */
    String baseUomName;
    /** 单耗(每母件) */
    BigDecimal quantityPer;
    /** 损耗率(%) */
    BigDecimal lossRate;
    /** 需求量=计划数×单耗×(1+损耗) */
    BigDecimal demandQty;
    /** 已发料量 */
    BigDecimal issuedQty;
    /** 供应方: SELF_PROVIDED/VENDOR_PROVIDED/CUSTOMER_PROVIDED */
    String supplyType;
    /** 创建部门 */
    Long createDept;
    /** 创建者 */
    Long createBy;
    /** 创建时间 */
    Date createTime;
    /** 更新者 */
    Long updateBy;
    /** 更新时间 */
    Date updateTime;
    /** 备注 */
    String remark;
}

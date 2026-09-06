package com.nexus.erp.prd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdSubcontractBomLine;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外订单BOM快照行业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdSubcontractBomLine.class, reverseConvertGenerate = false)
public class PrdSubcontractBomLineBo extends BaseEntity {

    /** 快照行ID */
    private Long lineId;
    /** 委外订单ID */
    private Long subcontractId;
    /** 来源BOM行ID(bd_bom_detail) */
    private Long bomDetailId;
    /** 子件物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位名称 */
    private String baseUomName;
    /** 单耗(每母件) */
    private BigDecimal quantityPer;
    /** 损耗率(%) */
    private BigDecimal lossRate;
    /** 需求量=计划数×单耗×(1+损耗) */
    private BigDecimal demandQty;
    /** 已发料量 */
    private BigDecimal issuedQty;
    /** 供应方: SELF_PROVIDED/VENDOR_PROVIDED/CUSTOMER_PROVIDED */
    private String supplyType;
    /** 创建部门 */
    private Long createDept;
    /** 创建者 */
    private Long createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新者 */
    private Long updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 备注 */
    private String remark;
}

package com.nexus.erp.prd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdSubcontract;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外订单业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdSubcontract.class, reverseConvertGenerate = false)
public class PrdSubcontractBo extends BaseEntity {

    /** 委外单ID */
    private Long subcontractId;
    /** 委外单号 */
    private String subcontractNo;
    /** 供应商ID */
    private Long vendorId;
    /** 供应商编码 */
    private String vendorCode;
    /** 供应商名称 */
    private String vendorName;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位名称 */
    private String baseUomName;
    /** 计划数量 */
    private BigDecimal plannedQty;
    /** 实际数量 */
    private BigDecimal actualQty;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 计划开始日期 */
    private Date planStartDate;
    /** 计划结束日期 */
    private Date planEndDate;
    /** 实际开始日期 */
    private Date actualStartDate;
    /** 实际结束日期 */
    private Date actualEndDate;
    /** 关联工单ID */
    private Long woId;
    /** 关联工单编号 */
    private String woNo;
    /** 关联BOM ID(快照来源) */
    private Long bomId;
    /** 工艺路线ID */
    private Long routingId;
    /** 工艺要求(质量标准/特殊工艺/AOI/FCT等) */
    private String processRequirement;
    /** SN已生成数量 */
    private Integer snGeneratedQty;
    /** 收货目标仓库ID(默认半成品仓) */
    private Long targetWarehouseId;
    /** 收货目标仓库名称 */
    private String targetWarehouseName;
    /** 委外状态 */
    private String status;
    /** 备注 */
    private String remark;
    /** ID数组（用于批量操作） */
    private Long[] ids;
}

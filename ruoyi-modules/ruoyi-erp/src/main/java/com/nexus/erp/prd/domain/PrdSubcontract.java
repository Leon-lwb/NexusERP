package com.nexus.erp.prd.domain;

import cn.idev.excel.annotation.ExcelProperty;
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
 * 委外订单对象 prd_subcontract
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("prd_subcontract")
public class PrdSubcontract extends TenantEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "subcontract_id")
    private Long subcontractId;

    @ExcelProperty("委外单号")
    private String subcontractNo;

    @ExcelProperty("供应商ID")
    private Long vendorId;

    @ExcelProperty("供应商编码")
    private String vendorCode;

    @ExcelProperty("供应商名称")
    private String vendorName;

    @ExcelProperty("物料ID")
    private Long materialId;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("规格型号")
    private String materialSpec;

    @ExcelProperty("单位名称")
    private String baseUomName;

    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    @ExcelProperty("实际数量")
    private BigDecimal actualQty;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    @ExcelProperty("计划开始日期")
    private Date planStartDate;

    @ExcelProperty("计划结束日期")
    private Date planEndDate;

    @ExcelProperty("实际开始日期")
    private Date actualStartDate;

    @ExcelProperty("实际结束日期")
    private Date actualEndDate;

    @ExcelProperty("关联工单ID")
    private Long woId;

    @ExcelProperty("关联工单编号")
    private String woNo;

    @ExcelProperty("关联BOM ID")
    private Long bomId;

    @ExcelProperty("工艺路线ID")
    private Long routingId;

    @ExcelProperty("工艺要求")
    private String processRequirement;

    @ExcelProperty("SN已生成数量")
    private Integer snGeneratedQty;

    @ExcelProperty("收货目标仓库ID")
    private Long targetWarehouseId;

    @ExcelProperty("收货目标仓库名称")
    private String targetWarehouseName;

    @ExcelProperty("委外状态")
    private String status;

    @TableLogic
    private String delFlag;

    @ExcelProperty("备注")
    private String remark;
}

package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdSubcontract;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外订单视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdSubcontract.class)
public class PrdSubcontractVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 委外单ID */
    @ExcelProperty(value = "委外单ID")
    private Long subcontractId;

    /** 委外单号 */
    @ExcelProperty(value = "委外单号")
    private String subcontractNo;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商编码 */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /** 供应商名称 */
    @ExcelProperty(value = "供应商名字")
    private String vendorName;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /** 规格型号 */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /** 单位名称 */
    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    /** 计划数量 */
    @ExcelProperty(value = "计划数量")
    private BigDecimal plannedQty;

    /** 实际数量 */
    @ExcelProperty(value = "实际数量")
    private BigDecimal actualQty;

    /** 单价 */
    @ExcelProperty(value = "单价")
    private BigDecimal unitPrice;

    /** 总金额 */
    @ExcelProperty(value = "总金额")
    private BigDecimal totalAmount;

    /** 计划开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "计划开始日期")
    private Date planStartDate;

    /** 计划结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "计划结束日期")
    private Date planEndDate;

    /** 实际开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date actualStartDate;

    /** 实际结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date actualEndDate;

    /** 关联工单ID */
    private Long woId;

    /** 关联工单编号 */
    @ExcelProperty(value = "关联工单编号")
    private String woNo;

    /** 委外状态 */
    @ExcelProperty(value = "委外状态")
    private String status;

    /** 备注 */
    @ExcelProperty(value = "备注")
    private String remark;
}

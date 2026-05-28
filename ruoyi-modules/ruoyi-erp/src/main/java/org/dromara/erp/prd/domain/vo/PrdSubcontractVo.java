package org.dromara.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.prd.domain.PrdSubcontract;

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

    @ExcelProperty(value = "委外单ID")
    private Long subcontractId;

    @ExcelProperty(value = "委外单号")
    private String subcontractNo;

    private Long vendorId;

    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    @ExcelProperty(value = "供应商名字")
    private String vendorName;

    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    @ExcelProperty(value = "计划数量")
    private BigDecimal plannedQty;

    @ExcelProperty(value = "实际数量")
    private BigDecimal actualQty;

    @ExcelProperty(value = "单价")
    private BigDecimal unitPrice;

    @ExcelProperty(value = "总金额")
    private BigDecimal totalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "计划开始日期")
    private Date planStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "计划结束日期")
    private Date planEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date actualStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date actualEndDate;

    private Long woId;

    @ExcelProperty(value = "关联工单编号")
    private String woNo;

    @ExcelProperty(value = "委外状态")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;
}

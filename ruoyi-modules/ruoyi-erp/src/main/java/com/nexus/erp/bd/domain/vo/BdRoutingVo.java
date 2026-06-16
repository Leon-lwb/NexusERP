package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdRouting;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 工艺路线视图对象 bd_routing
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdRouting.class)
public class BdRoutingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "工艺路线ID")
    private Long routingId;

    @ExcelProperty(value = "工艺路线编码")
    private String routingCode;

    @ExcelProperty(value = "工艺路线名称")
    private String routingName;

    @ExcelProperty(value = "工艺路线描述")
    private String routingDesc;

    private Long materialId;

    @ExcelProperty(value = "适用物料编码")
    private String materialCode;

    @ExcelProperty(value = "适用物料名称")
    private String materialName;

    @ExcelProperty(value = "版本号")
    private String routingVersion;

    @ExcelProperty(value = "是否当前版本")
    private Long isCurrent;

    @ExcelProperty(value = "总工序数")
    private Integer totalSteps;

    @ExcelProperty(value = "总标准工时")
    private BigDecimal totalHours;

    @ExcelProperty(value = "生效日期")
    private Date effectiveDate;

    @ExcelProperty(value = "失效日期")
    private Date expiryDate;

    @ExcelProperty(value = "状态")
    private Long status;

    @ExcelProperty(value = "备注")
    private String remark;
}

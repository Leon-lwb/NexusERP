package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdRoutingStep;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线步骤视图对象 bd_routing_step
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdRoutingStep.class)
public class BdRoutingStepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "步骤ID")
    private Long stepId;

    @ExcelProperty(value = "工艺路线ID")
    private Long routingId;

    @ExcelProperty(value = "步骤序号")
    private Integer sequenceNum;

    @ExcelProperty(value = "工序ID")
    private Long processId;

    @ExcelProperty(value = "工序编码")
    private String processCode;

    @ExcelProperty(value = "工序名称")
    private String processName;

    @ExcelProperty(value = "步骤名称")
    private String stepName;

    @ExcelProperty(value = "工作中心ID")
    private Long workstationId;

    @ExcelProperty(value = "工作中心编码")
    private String workstationCode;

    @ExcelProperty(value = "工作中心名称")
    private String workstationName;

    @ExcelProperty(value = "标准工时")
    private BigDecimal standardHours;

    @ExcelProperty(value = "准备工时")
    private BigDecimal setupHours;

    @ExcelProperty(value = "标准人工数")
    private Integer laborCount;

    @ExcelProperty(value = "标准机台数")
    private Integer machineCount;

    @ExcelProperty(value = "人工费率")
    private BigDecimal laborRate;

    @ExcelProperty(value = "机器费率")
    private BigDecimal machineRate;

    @ExcelProperty(value = "是否关键工序")
    private Integer isKeyProcess;

    @ExcelProperty(value = "是否委外工序")
    private Integer isOutsource;

    @ExcelProperty(value = "是否需要质检")
    private Integer qualityRequired;

    @ExcelProperty(value = "等待时间")
    private BigDecimal waitTime;

    @ExcelProperty(value = "转移时间")
    private BigDecimal transferTime;

    private String description;

    private String remark;
}

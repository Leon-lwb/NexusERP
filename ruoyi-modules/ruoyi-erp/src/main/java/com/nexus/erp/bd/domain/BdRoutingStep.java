package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 工艺路线步骤对象 bd_routing_step
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_routing_step")
public class BdRoutingStep extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 步骤ID
     */
    @TableId(value = "step_id")
    private Long stepId;

    /**
     * 工艺路线ID
     */
    @ExcelProperty("工艺路线ID")
    private Long routingId;

    /**
     * 步骤序号
     */
    @ExcelProperty("步骤序号")
    private Integer sequenceNum;

    /**
     * 工序ID
     */
    @ExcelProperty("工序ID")
    private Long processId;

    /**
     * 工序编码
     */
    @ExcelProperty("工序编码")
    private String processCode;

    /**
     * 工序名称
     */
    @ExcelProperty("工序名称")
    private String processName;

    /**
     * 步骤名称
     */
    @ExcelProperty("步骤名称")
    private String stepName;

    /**
     * 工作中心ID
     */
    @ExcelProperty("工作中心ID")
    private Long workstationId;

    /**
     * 工作中心编码
     */
    @ExcelProperty("工作中心编码")
    private String workstationCode;

    /**
     * 工作中心名称
     */
    @ExcelProperty("工作中心名称")
    private String workstationName;

    /**
     * 标准工时(小时/件)
     */
    @ExcelProperty("标准工时")
    private BigDecimal standardHours;

    /**
     * 准备工时(小时)
     */
    @ExcelProperty("准备工时")
    private BigDecimal setupHours;

    /**
     * 标准人工数
     */
    @ExcelProperty("标准人工数")
    private Integer laborCount;

    /**
     * 标准机台数
     */
    @ExcelProperty("标准机台数")
    private Integer machineCount;

    /**
     * 人工费率(元/小时) - BOM成本滚算工序成本依赖
     */
    @ExcelProperty("人工费率")
    private BigDecimal laborRate;

    /**
     * 机器费率(元/小时) - BOM成本滚算工序成本依赖
     */
    @ExcelProperty("机器费率")
    private BigDecimal machineRate;

    /**
     * 是否关键工序
     */
    @ExcelProperty("是否关键工序")
    private Integer isKeyProcess;

    /**
     * 关联固件ID(固件刷写工艺步骤指定, 下发MES工单步骤后驱动刷写门禁)
     */
    @ExcelProperty("固件ID")
    private Long firmwareId;

    /**
     * 固件编码(冗余)
     */
    @ExcelProperty("固件编码")
    private String firmwareCode;

    /**
     * 固件名称(冗余)
     */
    @ExcelProperty("固件名称")
    private String firmwareName;

    /**
     * 是否委外工序
     */
    @ExcelProperty("是否委外工序")
    private Integer isOutsource;

    /**
     * 是否需要质检
     */
    @ExcelProperty("是否需要质检")
    private Integer qualityRequired;

    /**
     * 等待时间(小时)
     */
    @ExcelProperty("等待时间")
    private BigDecimal waitTime;

    /**
     * 转移时间(小时)
     */
    @ExcelProperty("转移时间")
    private BigDecimal transferTime;

    /**
     * 步骤说明/作业指导
     */
    private String description;

    /**
     * 备注
     */
    private String remark;
}

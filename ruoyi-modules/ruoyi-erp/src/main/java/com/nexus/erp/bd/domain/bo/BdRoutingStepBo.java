package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.nexus.erp.bd.domain.BdRoutingStep;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线步骤业务对象 bd_routing_step
 * 作为 BdRoutingBo.steps 的嵌套元素,随工艺路线一起保存(先删后插)
 *
 * @author nexus
 */
@Data
@AutoMapper(target = BdRoutingStep.class, reverseConvertGenerate = false)
public class BdRoutingStepBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stepId;

    private Long routingId;

    @NotNull(message = "步骤序号不能为空")
    private Integer sequenceNum;

    @NotNull(message = "工序ID不能为空")
    private Long processId;

    private String processCode;

    private String processName;

    private String stepName;

    private Long workstationId;

    private String workstationCode;

    private String workstationName;

    private BigDecimal standardHours;

    private BigDecimal setupHours;

    private Integer laborCount;

    private Integer machineCount;

    /** 人工费率(元/小时) - 成本滚算依赖 */
    private BigDecimal laborRate;

    /** 机器费率(元/小时) - 成本滚算依赖 */
    private BigDecimal machineRate;

    private Integer isKeyProcess;

    /** 关联固件ID(固件刷写工艺步骤指定, 下发MES后驱动刷写门禁) */
    private Long firmwareId;

    /** 固件编码(冗余) */
    private String firmwareCode;

    /** 固件名称(冗余) */
    private String firmwareName;

    private Integer isOutsource;

    private Integer qualityRequired;

    private BigDecimal waitTime;

    private BigDecimal transferTime;

    private String description;

    private String remark;
}

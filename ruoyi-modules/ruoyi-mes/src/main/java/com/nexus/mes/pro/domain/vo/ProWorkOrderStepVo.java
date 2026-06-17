package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProWorkOrderStep;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单工序步骤视图对象 pro_work_order_step
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProWorkOrderStep.class)
public class ProWorkOrderStepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工序步骤ID */
    private Long stepId;

    /** 所属MES工单ID */
    private Long mesWoId;

    /** 工序序号 */
    private Integer sequenceNum;

    /** ERP工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    private String workcenterCode;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 工序状态 */
    private String stepStatus;

    /** 是否关键工序 */
    private Integer isKeyProcess;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 备注 */
    private String remark;
}

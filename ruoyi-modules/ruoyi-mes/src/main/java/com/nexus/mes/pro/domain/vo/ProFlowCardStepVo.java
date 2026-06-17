package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProFlowCardStep;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES流转卡工序记录视图对象 pro_flow_card_step
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProFlowCardStep.class)
public class ProFlowCardStepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工序记录ID */
    private Long stepRecordId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号 */
    private String cardNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 关联工单工序步骤ID */
    private Long woStepId;

    /** 工序序号 */
    private Integer sequenceNum;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    private String workcenterCode;

    /** 是否关键工序 */
    private Integer isKeyProcess;

    /** 工序状态 */
    private String stepStatus;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 良品数量 */
    private BigDecimal goodQty;

    /** 不良品数量 */
    private BigDecimal defectQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 实际开始时间 */
    private Date actualStartTime;

    /** 实际结束时间 */
    private Date actualEndTime;

    /** 备注 */
    private String remark;
}

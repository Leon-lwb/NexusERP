package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES流转卡工序记录对象 pro_flow_card_step
 * <p>属于 MES 生产管理模块，表达 流转卡 的领域模型。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_flow_card_step")
public class ProFlowCardStep extends TenantEntity {

    /** 工序记录ID(主键) */
    @TableId(value = "step_record_id")
    private Long stepRecordId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号(冗余) */
    private String cardNo;

    /** 关联MES工单ID(冗余) */
    private Long mesWoId;

    /** 关联工单工序步骤ID */
    private Long woStepId;

    /** 工序序号 */
    private Integer sequenceNum;

    /** 工序ID(来自ERP) */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码(冗余) */
    private String workcenterCode;

    /** 是否关键工序(0否 1是) */
    private Integer isKeyProcess;

    /** 工序状态: PENDING/IN_PROGRESS/COMPLETED/SKIPPED */
    private String stepStatus;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名(冗余) */
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

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

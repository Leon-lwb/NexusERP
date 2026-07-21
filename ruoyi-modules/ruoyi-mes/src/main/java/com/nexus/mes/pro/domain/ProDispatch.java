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
 * MES派工单对象 pro_dispatch
 * <p>属于 MES 生产管理模块，表达 工序派工 的领域模型。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_dispatch")
public class ProDispatch extends TenantEntity {

    /** 派工单ID(主键) */
    @TableId(value = "dispatch_id")
    private Long dispatchId;

    /** 派工单编号 */
    private String dispatchNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号(冗余) */
    private String cardNo;

    /** 关联流转卡工序记录ID */
    private Long stepRecordId;

    /** 工序序号(冗余) */
    private Integer sequenceNum;

    /** 工序ID */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码(冗余) */
    private String workcenterCode;

    /** 操作员ID(SchWorker.workerId) */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 派工数量 */
    private BigDecimal plannedQty;

    /** 已完成数量 */
    private BigDecimal completedQty;

    /** 良品数量 */
    private BigDecimal goodQty;

    /** 不良品数量 */
    private BigDecimal defectQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 派工状态: PENDING/IN_PROGRESS/COMPLETED/CANCELLED */
    private String dispatchStatus;

    /** 计划开始时间 */
    private Date planStartTime;

    /** 计划结束时间 */
    private Date planEndTime;

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

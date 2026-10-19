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
 * <p>属于 MES 生产管理模块，作为 流转卡 的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProFlowCardStep.class)
public class ProFlowCardStepVo implements Serializable {

    /**
     * 当前实际工序的产品lot、原生产分配与已登记产出；证据不足时为空。
     */
    private java.util.Map<String, Object> productOutputContext;

    @Serial private static final long serialVersionUID = 1L;

    /**
     * 工序记录ID
     */
    private Long stepRecordId;

    /**
     * 关联流转卡ID
     */
    private Long cardId;

    /**
     * 流转卡编号
     */
    private String cardNo;

    /**
     * 关联MES工单ID
     */
    private Long mesWoId;

    /**
     * 关联工单工序步骤ID
     */
    private Long woStepId;

    /**
     * 工序序号
     */
    private Integer sequenceNum;

    /**
     * 工序ID
     */
    private Long processId;

    /**
     * 工序编码
     */
    private String processCode;

    /**
     * 工序名称
     */
    private String processName;

    /**
     * 工作中心ID
     */
    private Long workcenterId;

    /**
     * 工作中心编码
     */
    private String workcenterCode;

    /**
     * 是否关键工序
     */
    private Integer isKeyProcess;

    /**
     * 工序状态
     */
    private String stepStatus;

    /**
     * 操作员ID
     */
    private Long operatorId;

    /**
     * 操作员姓名
     */
    private String operatorName;

    /**
     * 计划数量
     */
    private BigDecimal plannedQty;

    /**
     * 良品数量
     */
    private BigDecimal goodQty;

    /**
     * 不良品数量
     */
    private BigDecimal defectQty;

    /**
     * 报废数量
     */
    private BigDecimal scrapQty;

    /**
     * 实际开始时间
     */
    private Date actualStartTime;

    /**
     * 实际结束时间
     */
    private Date actualEndTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 进站校验结果(PASS/FAIL)
     */
    private String entryCheckResult;

    /**
     * 进站校验备注(失败原因)
     */
    private String entryCheckRemark;

    /**
     * 出站校验结果(PASS/FAIL)
     */
    private String exitCheckResult;

    /**
     * 出站校验备注(失败原因)
     */
    private String exitCheckRemark;

    /**
     * 跳站原因
     */
    private String skipReason;
}

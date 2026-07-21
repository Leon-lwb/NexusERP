package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProDispatch;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES派工单视图对象 pro_dispatch
 * <p>属于 MES 生产管理模块，作为 工序派工 的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProDispatch.class)
public class ProDispatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 派工单ID */
    @ExcelProperty("派工单ID")
    private Long dispatchId;

    /** 派工单编号 */
    @ExcelProperty("派工单编号")
    private String dispatchNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号 */
    @ExcelProperty("流转卡编号")
    private String cardNo;

    /** 关联流转卡工序记录ID */
    private Long stepRecordId;

    /** 工序序号 */
    private Integer sequenceNum;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    @ExcelProperty("工序编码")
    private String processCode;

    /** 工序名称 */
    @ExcelProperty("工序名称")
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    @ExcelProperty("工作中心")
    private String workcenterCode;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    @ExcelProperty("操作员")
    private String operatorName;

    /** 派工数量 */
    @ExcelProperty("派工数量")
    private BigDecimal plannedQty;

    /** 已完成数量 */
    @ExcelProperty("已完成数量")
    private BigDecimal completedQty;

    /** 良品数量 */
    @ExcelProperty("良品数量")
    private BigDecimal goodQty;

    /** 不良品数量 */
    @ExcelProperty("不良品数量")
    private BigDecimal defectQty;

    /** 报废数量 */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /** 派工状态 */
    @ExcelProperty("状态")
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
}

package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.prd.domain.PrdWorkReport;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产报工视图对象 prd_work_report
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdWorkReport.class)
public class PrdWorkReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 报工记录ID
     */
    @ExcelProperty("报工记录ID")
    private Long reportId;

    /**
     * 报工单编号
     */
    @ExcelProperty("报工单编号")
    private String reportNo;

    /**
     * 报工类型
     */
    @ExcelProperty("报工类型")
    private String reportType;

    /**
     * 报工状态
     */
    @ExcelProperty("报工状态")
    private String reportStatus;

    /**
     * 工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 工单名称
     */
    private String woName;

    /**
     * 产品物料编码
     */
    private String materialCode;

    /**
     * 产品物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String specification;

    /**
     * 单位编码
     */
    private String unitCode;

    /**
     * 单位名称
     */
    private String unitName;

    /**
     * 工艺流程ID
     */
    private Long routeId;

    /**
     * 工艺流程编号
     */
    private String routeCode;

    /**
     * 工艺流程名称
     */
    private String routeName;

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
     * 工序步骤序号
     */
    private Long operationStep;

    /**
     * 当前工序报工次数
     */
    private Long operationSeq;

    /**
     * 生产任务ID
     */
    private Long taskId;

    /**
     * 生产任务编号
     */
    private String taskNo;

    /**
     * 工作站ID
     */
    private Long workstationId;

    /**
     * 工作站编码
     */
    private String workstationCode;

    /**
     * 工作站名称
     */
    private String workstationName;

    /**
     * 车间ID
     */
    private Long workshopId;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 设备ID
     */
    private Long equipmentId;

    /**
     * 设备编码
     */
    private String equipmentCode;

    /**
     * 设备名称
     */
    private String equipmentName;

    /**
     * 生产批号
     */
    private String lotNumber;

    /**
     * 批次编号
     */
    private String batchNo;

    /**
     * 过期日期
     */
    private Date expireDate;

    /**
     * 班次ID
     */
    private Long shiftId;

    /**
     * 班次名称
     */
    private String shiftName;

    /**
     * 计划数量
     */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /**
     * 本次报工数量
     */
    @ExcelProperty("本次报工数量")
    private BigDecimal reportedQty;

    /**
     * 合格数量
     */
    @ExcelProperty("合格数量")
    private BigDecimal goodQty;

    /**
     * 报废数量
     */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /**
     * 不良品数量
     */
    @ExcelProperty("不良品数量")
    private BigDecimal defectQty;

    /**
     * 工废数量
     */
    private BigDecimal laborScrapQty;

    /**
     * 料废数量
     */
    private BigDecimal materialScrapQty;

    /**
     * 其他原因报废数量
     */
    private BigDecimal otherScrapQty;

    /**
     * 待检测数量
     */
    private BigDecimal uncheckQty;

    /**
     * 累计报工数量
     */
    private BigDecimal cumulativeQty;

    /**
     * 剩余数量
     */
    private BigDecimal remainingQty;

    /**
     * 报工日期
     */
    @ExcelProperty("报工日期")
    private Date reportDate;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 人工工时
     */
    private BigDecimal laborHours;

    /**
     * 机器工时
     */
    private BigDecimal machineHours;

    /**
     * 准备工时
     */
    private BigDecimal setupHours;

    /**
     * 等待工时
     */
    private BigDecimal waitHours;

    /**
     * 暂停工时
     */
    private BigDecimal pauseHours;

    /**
     * 操作工ID
     */
    private Long workerId;

    /**
     * 操作工编码
     */
    private String workerCode;

    /**
     * 操作工姓名
     */
    private String workerName;

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 班组编码
     */
    private String teamCode;

    /**
     * 班组名称
     */
    private String teamName;

    /**
     * 辅助人员ID列表
     */
    private String helperIds;

    /**
     * 辅助人员姓名列表
     */
    private String helperNames;

    /**
     * 质量状态
     */
    private String qualityStatus;

    /**
     * 质检记录ID
     */
    private Long qualityCheckId;

    /**
     * 质检单号
     */
    private String qualityCheckNo;

    /**
     * 检验员ID
     */
    private Long inspectorId;

    /**
     * 检验员姓名
     */
    private String inspectorName;

    /**
     * 检验时间
     */
    private Date inspectTime;

    /**
     * 报工途径
     */
    private String reportChannel;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 来源单据ID
     */
    private Long sourceId;

    /**
     * 来源单据编号
     */
    private String sourceNo;

    /**
     * 条码信息
     */
    private String barcode;

    /**
     * 提交人
     */
    private String submitBy;

    /**
     * 提交时间
     */
    private Date submitTime;

    /**
     * 审核人
     */
    private String auditBy;

    /**
     * 审核时间
     */
    private Date auditTime;

    /**
     * 审核意见
     */
    private String auditRemark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;
}

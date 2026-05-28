package com.nexus.erp.prd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产报工对象 prd_work_report
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_work_report")
public class PrdWorkReport extends TenantEntity {

    /**
     * 报工记录ID
     */
    @TableId(value = "report_id")
    private Long reportId;

    /**
     * 报工单编号
     */
    @ExcelProperty("报工单编号")
    private String reportNo;

    /**
     * 报工类型：NORMAL-正常报工，REWORK-返工，REPAIR-返修，OVERTIME-加班
     */
    @ExcelProperty("报工类型")
    private String reportType;

    /**
     * 报工状态：DRAFT-草稿，SUBMITTED-已提交，APPROVED-已审核，REJECTED-已驳回
     */
    @ExcelProperty("报工状态")
    private String reportStatus;

    /**
     * 工单ID
     */
    private Long woId;

    /**
     * 工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 工单名称
     */
    @ExcelProperty("工单名称")
    private String woName;

    /**
     * 产品物料ID
     */
    private Long materialId;

    /**
     * 产品物料编码
     */
    @ExcelProperty("产品物料编码")
    private String materialCode;

    /**
     * 产品物料名称
     */
    @ExcelProperty("产品物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty("规格型号")
    private String specification;

    /**
     * 单位ID
     */
    private Long unitId;

    /**
     * 单位编码
     */
    @ExcelProperty("单位编码")
    private String unitCode;

    /**
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String unitName;

    /**
     * 工艺流程ID
     */
    @ExcelProperty("工艺流程ID")
    private Long routeId;

    /**
     * 工艺流程编号
     */
    @ExcelProperty("工艺流程编号")
    private String routeCode;

    /**
     * 工艺流程名称
     */
    @ExcelProperty("工艺流程名称")
    private String routeName;

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
     * 工序步骤序号
     */
    @ExcelProperty("工序步骤序号")
    private Long operationStep;

    /**
     * 当前工序报工次数
     */
    @ExcelProperty("当前工序报工次数")
    private Long operationSeq;

    /**
     * 生产任务ID
     */
    @ExcelProperty("生产任务ID")
    private Long taskId;

    /**
     * 生产任务编号
     */
    @ExcelProperty("生产任务编号")
    private String taskNo;

    /**
     * 工作站ID
     */
    @ExcelProperty("工作站ID")
    private Long workstationId;

    /**
     * 工作站编码
     */
    @ExcelProperty("工作站编码")
    private String workstationCode;

    /**
     * 工作站名称
     */
    @ExcelProperty("工作站名称")
    private String workstationName;

    /**
     * 车间ID
     */
    @ExcelProperty("车间ID")
    private Long workshopId;

    /**
     * 车间名称
     */
    @ExcelProperty("车间名称")
    private String workshopName;

    /**
     * 设备ID
     */
    @ExcelProperty("设备ID")
    private Long equipmentId;

    /**
     * 设备编码
     */
    @ExcelProperty("设备编码")
    private String equipmentCode;

    /**
     * 设备名称
     */
    @ExcelProperty("设备名称")
    private String equipmentName;

    /**
     * 生产批号/批次号
     */
    @ExcelProperty("生产批号")
    private String lotNumber;

    /**
     * 批次编号
     */
    @ExcelProperty("批次编号")
    private String batchNo;

    /**
     * 过期日期/有效期至
     */
    @ExcelProperty("过期日期")
    private Date expireDate;

    /**
     * 班次ID
     */
    @ExcelProperty("班次ID")
    private Long shiftId;

    /**
     * 班次名称
     */
    @ExcelProperty("班次名称")
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
     * 工废数量（人为原因报废）
     */
    @ExcelProperty("工废数量")
    private BigDecimal laborScrapQty;

    /**
     * 料废数量（材料原因报废）
     */
    @ExcelProperty("料废数量")
    private BigDecimal materialScrapQty;

    /**
     * 其他原因报废数量
     */
    @ExcelProperty("其他原因报废数量")
    private BigDecimal otherScrapQty;

    /**
     * 待检测数量
     */
    @ExcelProperty("待检测数量")
    private BigDecimal uncheckQty;

    /**
     * 累计报工数量
     */
    @ExcelProperty("累计报工数量")
    private BigDecimal cumulativeQty;

    /**
     * 剩余数量
     */
    @ExcelProperty("剩余数量")
    private BigDecimal remainingQty;

    /**
     * 报工日期
     */
    @ExcelProperty("报工日期")
    private Date reportDate;

    /**
     * 开始时间
     */
    @ExcelProperty("开始时间")
    private Date startTime;

    /**
     * 结束时间
     */
    @ExcelProperty("结束时间")
    private Date endTime;

    /**
     * 人工工时（小时）
     */
    @ExcelProperty("人工工时")
    private BigDecimal laborHours;

    /**
     * 机器工时（小时）
     */
    @ExcelProperty("机器工时")
    private BigDecimal machineHours;

    /**
     * 准备工时（小时）
     */
    @ExcelProperty("准备工时")
    private BigDecimal setupHours;

    /**
     * 等待工时（小时）
     */
    @ExcelProperty("等待工时")
    private BigDecimal waitHours;

    /**
     * 暂停工时（小时）
     */
    @ExcelProperty("暂停工时")
    private BigDecimal pauseHours;

    /**
     * 操作工ID
     */
    @ExcelProperty("操作工ID")
    private Long workerId;

    /**
     * 操作工编码
     */
    @ExcelProperty("操作工编码")
    private String workerCode;

    /**
     * 操作工姓名
     */
    @ExcelProperty("操作工姓名")
    private String workerName;

    /**
     * 班组ID
     */
    @ExcelProperty("班组ID")
    private Long teamId;

    /**
     * 班组编码
     */
    @ExcelProperty("班组编码")
    private String teamCode;

    /**
     * 班组名称
     */
    @ExcelProperty("班组名称")
    private String teamName;

    /**
     * 辅助人员ID列表（逗号分隔）
     */
    @ExcelProperty("辅助人员ID列表")
    private String helperIds;

    /**
     * 辅助人员姓名列表
     */
    @ExcelProperty("辅助人员姓名列表")
    private String helperNames;

    /**
     * 质量状态：PENDING-待检，QUALIFIED-合格，UNQUALIFIED-不合格，PARTIAL-部分合格
     */
    @ExcelProperty("质量状态")
    private String qualityStatus;

    /**
     * 质检记录ID
     */
    @ExcelProperty("质检记录ID")
    private Long qualityCheckId;

    /**
     * 质检单号
     */
    @ExcelProperty("质检单号")
    private String qualityCheckNo;

    /**
     * 检验员ID
     */
    @ExcelProperty("检验员ID")
    private Long inspectorId;

    /**
     * 检验员姓名
     */
    @ExcelProperty("检验员姓名")
    private String inspectorName;

    /**
     * 检验时间
     */
    @ExcelProperty("检验时间")
    private Date inspectTime;

    /**
     * 报工途径
     */
    @ExcelProperty("报工途径")
    private String reportChannel;

    /**
     * 来源类型
     */
    @ExcelProperty("来源类型")
    private String sourceType;

    /**
     * 来源单据ID
     */
    @ExcelProperty("来源单据ID")
    private Long sourceId;

    /**
     * 来源单据编号
     */
    @ExcelProperty("来源单据编号")
    private String sourceNo;

    /**
     * 条码信息
     */
    @ExcelProperty("条码信息")
    private String barcode;

    /**
     * 提交人
     */
    @ExcelProperty("提交人")
    private String submitBy;

    /**
     * 提交时间
     */
    @ExcelProperty("提交时间")
    private Date submitTime;

    /**
     * 审核人
     */
    @ExcelProperty("审核人")
    private String auditBy;

    /**
     * 审核时间
     */
    @ExcelProperty("审核时间")
    private Date auditTime;

    /**
     * 审核意见
     */
    @ExcelProperty("审核意见")
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

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

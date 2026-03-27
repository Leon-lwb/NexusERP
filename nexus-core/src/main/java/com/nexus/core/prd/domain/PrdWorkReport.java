package com.nexus.core.prd.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 生产报工对象 prd_work_report
 *
 * @author nexus
 * @date 2026-03-27
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.prd.mapper.PrdWorkReportMapper.PrdWorkReportResult")
public class PrdWorkReport extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 报工记录ID */
    @TableId(value = "report_id",type = IdType.AUTO)
    private Long reportId;
    /** 报工单编号 */
    @Excel(name = "报工单编号")
    private String reportNo;
    /** 报工类型：NORMAL-正常报工，REWORK-返工，REPAIR-返修，OVERTIME-加班 */
    @Excel(name = "报工类型：NORMAL-正常报工，REWORK-返工，REPAIR-返修，OVERTIME-加班")
    private String reportType;
    /** 报工状态：DRAFT-草稿，SUBMITTED-已提交，APPROVED-已审核，REJECTED-已驳回 */
    @Excel(name = "报工状态：DRAFT-草稿，SUBMITTED-已提交，APPROVED-已审核，REJECTED-已驳回")
    private String reportStatus;
    /** 工单ID */
    private Long woId;
    /** 工单编号 */
    @Excel(name = "工单编号")
    private String woNo;
    /** 工单名称 */
    @Excel(name = "工单名称")
    private String woName;
    /** 产品物料ID */
    private Long materialId;
    /** 产品物料编码 */
    @Excel(name = "产品物料编码")
    private String materialCode;
    /** 产品物料名称 */
    @Excel(name = "产品物料名称")
    private String materialName;
    /** 规格型号 */
    @Excel(name = "规格型号")
    private String specification;
    /** 单位ID */
    private Long unitId;
    /** 单位编码 */
    @Excel(name = "单位编码")
    private String unitCode;
    /** 单位名称 */
    @Excel(name = "单位名称")
    private String unitName;
    /** 工艺流程ID */
    @Excel(name = "工艺流程ID")
    private Long routeId;
    /** 工艺流程编号 */
    @Excel(name = "工艺流程编号")
    private String routeCode;
    /** 工艺流程名称 */
    @Excel(name = "工艺流程名称")
    private String routeName;
    /** 工序ID */
    @Excel(name = "工序ID")
    private Long processId;
    /** 工序编码 */
    @Excel(name = "工序编码")
    private String processCode;
    /** 工序名称 */
    @Excel(name = "工序名称")
    private String processName;
    /** 工序步骤序号 */
    @Excel(name = "工序步骤序号")
    private Long operationStep;
    /** 当前工序报工次数（用于多次报工） */
    @Excel(name = "当前工序报工次数", readConverterExp = "用=于多次报工")
    private Long operationSeq;
    /** 生产任务ID */
    @Excel(name = "生产任务ID")
    private Long taskId;
    /** 生产任务编号 */
    @Excel(name = "生产任务编号")
    private String taskNo;
    /** 工作站ID */
    @Excel(name = "工作站ID")
    private Long workstationId;
    /** 工作站编码 */
    @Excel(name = "工作站编码")
    private String workstationCode;
    /** 工作站名称 */
    @Excel(name = "工作站名称")
    private String workstationName;
    /** 车间ID */
    @Excel(name = "车间ID")
    private Long workshopId;
    /** 车间名称 */
    @Excel(name = "车间名称")
    private String workshopName;
    /** 设备ID */
    @Excel(name = "设备ID")
    private Long equipmentId;
    /** 设备编码 */
    @Excel(name = "设备编码")
    private String equipmentCode;
    /** 设备名称 */
    @Excel(name = "设备名称")
    private String equipmentName;
    /** 生产批号/批次号 */
    @Excel(name = "生产批号/批次号")
    private String lotNumber;
    /** 批次编号 */
    @Excel(name = "批次编号")
    private String batchNo;
    /** 过期日期/有效期至 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "过期日期/有效期至", width = 30, dateFormat = "yyyy-MM-dd")
    private Date expireDate;
    /** 班次ID */
    @Excel(name = "班次ID")
    private Long shiftId;
    /** 班次名称 */
    @Excel(name = "班次名称")
    private String shiftName;
    /** 计划数量（工序计划数量） */
    @Excel(name = "计划数量", readConverterExp = "工=序计划数量")
    private BigDecimal plannedQty;
    /** 本次报工数量 */
    @Excel(name = "本次报工数量")
    private BigDecimal reportedQty;
    /** 合格数量 */
    @Excel(name = "合格数量")
    private BigDecimal goodQty;
    /** 报废数量 */
    @Excel(name = "报废数量")
    private BigDecimal scrapQty;
    /** 不良品数量 */
    @Excel(name = "不良品数量")
    private BigDecimal defectQty;
    /** 工废数量（人为原因报废） */
    @Excel(name = "工废数量", readConverterExp = "人=为原因报废")
    private BigDecimal laborScrapQty;
    /** 料废数量（材料原因报废） */
    @Excel(name = "料废数量", readConverterExp = "材=料原因报废")
    private BigDecimal materialScrapQty;
    /** 其他原因报废数量 */
    @Excel(name = "其他原因报废数量")
    private BigDecimal otherScrapQty;
    /** 待检测数量 */
    @Excel(name = "待检测数量")
    private BigDecimal uncheckQty;
    /** 累计报工数量（到本次为止） */
    @Excel(name = "累计报工数量", readConverterExp = "到=本次为止")
    private BigDecimal cumulativeQty;
    /** 剩余数量 */
    @Excel(name = "剩余数量")
    private BigDecimal remainingQty;
    /** 报工日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "报工日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date reportDate;
    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "开始时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startTime;
    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date endTime;
    /** 人工工时（小时） */
    @Excel(name = "人工工时", readConverterExp = "小=时")
    private BigDecimal laborHours;
    /** 机器工时（小时） */
    @Excel(name = "机器工时", readConverterExp = "小=时")
    private BigDecimal machineHours;
    /** 准备工时（小时） */
    @Excel(name = "准备工时", readConverterExp = "小=时")
    private BigDecimal setupHours;
    /** 等待工时（小时） */
    @Excel(name = "等待工时", readConverterExp = "小=时")
    private BigDecimal waitHours;
    /** 暂停工时（小时） */
    @Excel(name = "暂停工时", readConverterExp = "小=时")
    private BigDecimal pauseHours;
    /** 操作工ID（主操作工） */
    @Excel(name = "操作工ID", readConverterExp = "主=操作工")
    private Long workerId;
    /** 操作工编码 */
    @Excel(name = "操作工编码")
    private String workerCode;
    /** 操作工姓名 */
    @Excel(name = "操作工姓名")
    private String workerName;
    /** 班组ID */
    @Excel(name = "班组ID")
    private Long teamId;
    /** 班组编码 */
    @Excel(name = "班组编码")
    private String teamCode;
    /** 班组名称 */
    @Excel(name = "班组名称")
    private String teamName;
    /** 辅助人员ID列表（逗号分隔） */
    @Excel(name = "辅助人员ID列表", readConverterExp = "逗=号分隔")
    private String helperIds;
    /** 辅助人员姓名列表 */
    @Excel(name = "辅助人员姓名列表")
    private String helperNames;
    /** 质量状态：PENDING-待检，QUALIFIED-合格，UNQUALIFIED-不合格，PARTIAL-部分合格 */
    @Excel(name = "质量状态：PENDING-待检，QUALIFIED-合格，UNQUALIFIED-不合格，PARTIAL-部分合格")
    private String qualityStatus;
    /** 质检记录ID */
    @Excel(name = "质检记录ID")
    private Long qualityCheckId;
    /** 质检单号 */
    @Excel(name = "质检单号")
    private String qualityCheckNo;
    /** 检验员ID */
    @Excel(name = "检验员ID")
    private Long inspectorId;
    /** 检验员姓名 */
    @Excel(name = "检验员姓名")
    private String inspectorName;
    /** 检验时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "检验时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date inspectTime;
    /** 报工途径：PC-电脑端，APP-移动端，PDA-手持设备，AUTO-自动采集 */
    @Excel(name = "报工途径：PC-电脑端，APP-移动端，PDA-手持设备，AUTO-自动采集")
    private String reportChannel;
    /** 来源类型：MANUAL-手工录入，SCAN-扫码，IMPORT-导入，INTERFACE-接口同步 */
    @Excel(name = "来源类型：MANUAL-手工录入，SCAN-扫码，IMPORT-导入，INTERFACE-接口同步")
    private String sourceType;
    /** 来源单据ID */
    @Excel(name = "来源单据ID")
    private Long sourceId;
    /** 来源单据编号 */
    @Excel(name = "来源单据编号")
    private String sourceNo;
    /** 条码信息 */
    @Excel(name = "条码信息")
    private String barcode;
    /** 提交人 */
    @Excel(name = "提交人")
    private String submitBy;
    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "提交时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date submitTime;
    /** 审核人 */
    @Excel(name = "审核人")
    private String auditBy;
    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "审核时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date auditTime;
    /** 审核意见 */
    @Excel(name = "审核意见")
    private String auditRemark;
    /** 预留字段1 */
    @Excel(name = "预留字段1")
    private String attr1;
    /** 预留字段2 */
    @Excel(name = "预留字段2")
    private String attr2;
    /** 预留字段3 */
    @Excel(name = "预留字段3")
    private Long attr3;
    /** 预留字段4 */
    @Excel(name = "预留字段4")
    private Long attr4;
    /** 删除标志（0-未删除，1-已删除） */
    private Integer deleted;
}
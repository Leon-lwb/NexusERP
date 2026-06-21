package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProWorkOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES工单视图对象 pro_work_order
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProWorkOrder.class)
public class ProWorkOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** MES工单ID */
    @ExcelProperty("MES工单ID")
    private Long mesWoId;

    /** 关联ERP工单ID */
    @ExcelProperty("ERP工单ID")
    private Long erpWoId;

    /** MES工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 产品物料ID */
    private Long materialId;

    /** 产品编码 */
    @ExcelProperty("产品编码")
    private String materialCode;

    /** 产品名称 */
    @ExcelProperty("产品名称")
    private String materialName;

    /** 计划数量 */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /** 已完成数量 */
    @ExcelProperty("已完成数量")
    private BigDecimal completedQty;

    /** 报废数量 */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /** 单位ID */
    private Long unitId;

    /** 单位名称 */
    @ExcelProperty("单位")
    private String unitName;

    /** 计划开工日期 */
    @ExcelProperty("计划开工")
    private Date planStartDate;

    /** 计划完工日期 */
    @ExcelProperty("计划完工")
    private Date planEndDate;

    /** 实际开工时间 */
    private Date actualStartDate;

    /** 实际完工时间 */
    private Date actualEndDate;

    /** 车间ID */
    private Long workshopId;

    /** 车间名称 */
    @ExcelProperty("车间")
    private String workshopName;

    /** BOM ID */
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;

    /** 工单状态 */
    @ExcelProperty("状态")
    private String woStatus;

    /** 优先级 */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 工序步骤列表(详情查询时填充) */
    private List<ProWorkOrderStepVo> steps;

    /** MBOM快照明细列表(详情查询时填充) */
    private List<ProWorkOrderBomLineVo> bomLines;

    /** 工艺路线编码(富集,不持久化) */
    private String routingCode;

    /** 工艺路线名称(富集,不持久化) */
    private String routingName;

    /** BOM版本号(富集,不持久化) */
    private String bomVersion;
}

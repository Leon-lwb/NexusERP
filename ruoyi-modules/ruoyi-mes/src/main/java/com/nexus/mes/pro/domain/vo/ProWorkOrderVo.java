package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;

import com.nexus.mes.pro.domain.ProWorkOrder;

import io.github.linpeilie.annotations.AutoMapper;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES工单视图对象 pro_work_order
 * <p>属于 MES 生产管理模块，作为工单查询/导出的返回结构，详情含工序步骤与 MBOM 快照明细及 enriched 字段。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProWorkOrder.class)
public class ProWorkOrderVo implements Serializable {

    /** 已核实的当前完工清单和取消修订状态；无清单与未知证据严格区分。 */
    private Map<String, Object> completionState;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * MES工单ID
     */
    @ExcelProperty("MES工单ID")
    private Long mesWoId;

    /**
     * 关联ERP工单ID
     */
    @ExcelProperty("ERP工单ID")
    private Long erpWoId;

    /**
     * MES工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 产品物料ID
     */
    private Long materialId;

    /**
     * 产品编码
     */
    @ExcelProperty("产品编码")
    private String materialCode;

    /**
     * 产品名称
     */
    @ExcelProperty("产品名称")
    private String materialName;

    /**
     * 计划数量
     */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /**
     * 已完成数量
     */
    @ExcelProperty("已完成数量")
    private BigDecimal completedQty;

    /**
     * 报废数量
     */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /**
     * 单位ID
     */
    private Long unitId;

    /**
     * 单位名称
     */
    @ExcelProperty("单位")
    private String unitName;

    /**
     * 计划开工日期
     */
    @ExcelProperty("计划开工")
    private Date planStartDate;

    /**
     * 计划完工日期
     */
    @ExcelProperty("计划完工")
    private Date planEndDate;

    /**
     * 实际开工时间
     */
    private Date actualStartDate;

    /**
     * 实际完工时间
     */
    private Date actualEndDate;

    /**
     * 车间ID
     */
    private Long workshopId;

    /**
     * 车间名称
     */
    @ExcelProperty("车间")
    private String workshopName;

    /**
     * BOM ID
     */
    private Long bomId;

    /**
     * 关联设备ID
     */
    private Long equipmentId;

    /**
     * 关联设备编码(查询时富集)
     */
    @ExcelProperty("关联设备")
    private String equipmentCode;

    /**
     * 关联设备名称(查询时富集)
     */
    private String equipmentName;

    /**
     * 工艺路线ID
     */
    private Long routingId;

    /**
     * 工单状态
     */
    @ExcelProperty("状态")
    private String woStatus;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 备注
     */
    private String remark;

    /**
     * 已选审批流程模板ID
     */
    private Long selectFlowId;

    /**
     * 工单来源(ERP/REWORK/MANUAL)
     */
    @ExcelProperty("工单来源")
    private String sourceType;

    /**
     * 发布包ID(工单按此包生成, 可追溯)
     */
    private Long releasePackageId;

    /**
     * 发布包编码(冗余)
     */
    @ExcelProperty("发布包编码")
    private String releasePackageCode;

    /**
     * 发布包版本(冗余快照)
     */
    @ExcelProperty("发布包版本")
    private String releasePackageVer;

    /**
     * 发布包配置项快照(JSON数组, 供工位/报表消费)
     */
    private String releaseConfig;

    /**
     * 工序步骤列表(详情查询时填充)
     */
    private List<ProWorkOrderStepVo> steps;

    /**
     * MBOM快照明细列表(详情查询时填充)
     */
    private List<ProWorkOrderBomLineVo> bomLines;

    /**
     * 工艺路线编码(富集,不持久化)
     */
    private String routingCode;

    /**
     * 工艺路线名称(富集,不持久化)
     */
    private String routingName;

    /**
     * BOM版本号(富集,不持久化)
     */
    private String bomVersion;
}

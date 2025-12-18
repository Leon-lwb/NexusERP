package com.nexus.core.prd.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;

/**
 * 生产工单对象 prd_work_order
 *
 * @author nexus
 * @date 2025-12-16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.prd.mapper.PrdWorkOrderMapper.PrdWorkOrderResult")
public class PrdWorkOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 工单ID
     */
    @TableId(value = "wo_id", type = IdType.AUTO)
    private Long woId;
    /**
     * 工单编号
     */
    @Excel(name = "工单编号")
    private String woNo;
    /**
     * 来源计划ID
     */
    private Long planId;
    /**
     * 计划名称
     */
    @Excel(name = "计划名称")
    private String planName;
    /**
     * 来源类型
     */
    @Excel(name = "来源类型")
    private String sourceType;
    /**
     * 来源单据ID
     */
    @Excel(name = "来源单据ID")
    private Long sourceId;
    /**
     * 销售订单编号
     */
    @Excel(name = "销售订单编号")
    private String sourceNo;
    /**
     * 来源单据行ID
     */
    @Excel(name = "来源单据行ID")
    private Long sourceLineId;
    /**
     * 产品物料ID
     */
    @Excel(name = "产品物料ID")
    private Long materialId;
    /**
     * 产品编码
     */
    @Excel(name = "产品编码")
    private String materialCode;
    /**
     * 产品名称
     */
    @Excel(name = "产品名称")
    private String materialName;
    /**
     * 产品规格
     */
    @Excel(name = "产品规格")
    private String materialSpec;
    /**
     * 计划数量
     */
    @Excel(name = "计划数量")
    private BigDecimal plannedQty;
    /**
     * 已完成数量
     */
    @Excel(name = "已完成数量")
    private BigDecimal completedQty;
    /**
     * 报废数量
     */
    @Excel(name = "报废数量")
    private BigDecimal scrapQty;
    /**
     * 单位id
     */
    @Excel(name = "单位id")
    private Long unitId;
    /**
     * 单位名称
     */
    @Excel(name = "单位名称")
    private String unitName;
    /**
     * 计划开始日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "计划开始日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date planStartDate;
    /**
     * 计划结束日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "计划结束日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date planEndDate;
    /**
     * 实际开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "实际开始时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date actualStartDate;
    /**
     * 实际结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "实际结束时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date actualEndDate;
    /**
     * 工单状态
     */
    @Excel(name = "工单状态")
    private String woStatus;
    /**
     * 车间ID
     */
    @Excel(name = "车间ID")
    private Long workshopId;
    /**
     * 车间名称
     */
    @Excel(name = "车间名称")
    private String workshopName;
    /**
     * 工作站ID
     */
    @Excel(name = "工作站ID")
    private Long workstationId;
    /**
     * 工作站名称
     */
    @Excel(name = "工作站名称")
    private String workstationName;
    /**
     * 生产线ID
     */
    @Excel(name = "生产线ID")
    private Long productionLineId;
    /**
     * 负责人ID
     */
    @Excel(name = "负责人ID")
    private Long responsibleId;
    /**
     * 负责人
     */
    @Excel(name = "负责人")
    private String responsibleName;
    /**
     * 班组ID
     */
    @Excel(name = "班组ID")
    private Long teamId;
    /**
     * 班组
     */
    @Excel(name = "班组")
    private String teamName;
    /**
     * 优先级
     */
    @Excel(name = "优先级")
    private Long priority;
    /**
     * BOMid
     */
    private Long bomId;
    /**
     * BOM版本
     */
    private String bomVersion;
    /**
     * 预留字段1
     */
    private String bomName;
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
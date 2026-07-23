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
 * 生产工单对象 prd_work_order
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_work_order")
public class PrdWorkOrder extends TenantEntity {

    /**
     * 工单ID
     */
    @TableId(value = "wo_id")
    private Long woId;

    /**
     * 工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 来源计划ID
     */
    private Long planId;

    /**
     * 计划名称
     */
    @ExcelProperty("计划名称")
    private String planName;

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
     * 销售订单编号
     */
    @ExcelProperty("销售订单编号")
    private String sourceNo;

    /**
     * 来源单据行ID
     */
    @ExcelProperty("来源单据行ID")
    private Long sourceLineId;

    /**
     * 产品物料ID
     */
    @ExcelProperty("产品物料ID")
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
     * 产品规格
     */
    @ExcelProperty("产品规格")
    private String materialSpec;

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
     * 单位id
     */
    @ExcelProperty("单位id")
    private Long unitId;

    /**
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String unitName;

    /**
     * 计划开始日期
     */
    @ExcelProperty("计划开始日期")
    private Date planStartDate;

    /**
     * 计划结束日期
     */
    @ExcelProperty("计划结束日期")
    private Date planEndDate;

    /**
     * 实际开始时间
     */
    @ExcelProperty("实际开始时间")
    private Date actualStartDate;

    /**
     * 实际结束时间
     */
    @ExcelProperty("实际结束时间")
    private Date actualEndDate;

    /**
     * 工单状态
     */
    @ExcelProperty("工单状态")
    private String woStatus;

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
     * 工作站ID
     */
    @ExcelProperty("工作站ID")
    private Long workstationId;

    /**
     * 工作站名称
     */
    @ExcelProperty("工作站名称")
    private String workstationName;

    /**
     * 生产线ID
     */
    @ExcelProperty("生产线ID")
    private Long productionLineId;

    /**
     * 负责人ID
     */
    @ExcelProperty("负责人ID")
    private Long responsibleId;

    /**
     * 负责人
     */
    @ExcelProperty("负责人")
    private String responsibleName;

    /**
     * 班组ID
     */
    @ExcelProperty("班组ID")
    private Long teamId;

    /**
     * 班组
     */
    @ExcelProperty("班组")
    private String teamName;

    /**
     * 优先级
     */
    @ExcelProperty("优先级")
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
     * bomName
     */
    private String bomName;

    /**
     * attr2
     */
    private String attr2;

    /**
     * attr3
     */
    private Long attr3;

    /**
     * attr4
     */
    private Long attr4;

    /**
     * 备注
     */
    private String remark;

    /**
     * 已选审批流程模板ID
     */
    private Long selectFlowId;

    /**
     * 是否倒冲工单: 0-否, 1-是
     */
    @ExcelProperty("是否倒冲")
    private Integer backflushFlag;

    /**
     * 累计发料数量
     */
    @ExcelProperty("累计发料数量")
    private BigDecimal issuedQty;

    /**
     * 质检状态(PENDING/QUALIFIED/UNQUALIFIED/PARTIAL) - IPQC聚合
     */
    @ExcelProperty("质检状态")
    private String qualityStatus;
    /**
     * 质检记录ID(IPQC)
     */
    private Long qualityCheckId;
    /**
     * 质检单号(IPQC)
     */
    @ExcelProperty("质检单号")
    private String qualityCheckNo;
    /**
     * 检验员ID
     */
    private Long inspectorId;
    /**
     * 检验员姓名
     */
    @ExcelProperty("检验员")
    private String inspectorName;
    /**
     * 检验时间
     */
    @ExcelProperty("检验时间")
    private Date inspectTime;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

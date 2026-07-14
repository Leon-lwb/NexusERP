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
 * MES工单对象 pro_work_order
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_work_order")
public class ProWorkOrder extends TenantEntity {

    /** MES工单ID(主键) */
    @TableId(value = "mes_wo_id")
    private Long mesWoId;

    /** 关联ERP工单ID */
    private Long erpWoId;

    /** MES工单编号 */
    private String woNo;

    /** 产品物料ID */
    private Long materialId;

    /** 产品编码(冗余) */
    private String materialCode;

    /** 产品名称(冗余) */
    private String materialName;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 已完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 单位ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 计划开工日期 */
    private Date planStartDate;

    /** 计划完工日期 */
    private Date planEndDate;

    /** 实际开工时间 */
    private Date actualStartDate;

    /** 实际完工时间 */
    private Date actualEndDate;

    /** 车间ID */
    private Long workshopId;

    /** 车间名称(冗余) */
    private String workshopName;

    /** BOM ID */
    private Long bomId;

    /** 关联设备ID(P1-6 工单-设备联动) */
    private Long equipmentId;

    /** 工艺路线ID */
    private Long routingId;

    /** 工单状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String woStatus;

    /** 优先级(1最高,9最低) */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

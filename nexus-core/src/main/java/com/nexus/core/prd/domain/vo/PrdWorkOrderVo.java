
package com.nexus.core.prd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单对象 prd_work_order vo
 *
 * @author nexus
 * @date 2025-12-16
 */
@Data
public class PrdWorkOrderVo extends BasePageDtoRequest {

    /**
     * 工单ID
     */
    private Long woId;
    /**
     * 工单编号
     */
    private String woNo;
    /**
     * 计划名称
     */
    private String planName;
    /**
     * 来源类型
     */
    private String sourceType;
    /**
     * 来源单据ID
     */
    private Long sourceId;
    /**
     * 销售订单编号
     */
    private String sourceNo;
    /**
     * 来源单据行ID
     */
    private Long sourceLineId;
    /**
     * 产品物料ID
     */
    private Long materialId;
    /**
     * 产品编码
     */
    private String materialCode;
    /**
     * 产品名称
     */
    private String materialName;
    /**
     * 产品规格
     */
    private String materialSpec;
    /**
     * 计划数量
     */
    private BigDecimal plannedQty;
    /**
     * 已完成数量
     */
    private BigDecimal completedQty;
    /**
     * 报废数量
     */
    private BigDecimal scrapQty;
    /**
     * 单位id
     */
    private Long unitId;
    /**
     * 单位名称
     */
    private String unitName;
    /**
     * 计划开始日期
     */
    private LocalDateTime planStartDate;
    /**
     * 计划结束日期
     */
    private LocalDateTime planEndDate;
    /**
     * 实际开始时间
     */
    private LocalDateTime actualStartDate;
    /**
     * 实际结束时间
     */
    private LocalDateTime actualEndDate;
    /**
     * 工单状态
     */
    private String woStatus;
    /**
     * 车间ID
     */
    private Long workshopId;
    /**
     * 车间名称
     */
    private String workshopName;
    /**
     * 工作站ID
     */
    private Long workstationId;
    /**
     * 工作站名称
     */
    private String workstationName;
    /**
     * 生产线ID
     */
    private Long productionLineId;
    /**
     * 负责人ID
     */
    private Long responsibleId;
    /**
     * 负责人
     */
    private String responsibleName;
    /**
     * 班组ID
     */
    private Long teamId;
    /**
     * 班组
     */
    private String teamName;
    /**
     * 优先级
     */
    private Long priority;

}
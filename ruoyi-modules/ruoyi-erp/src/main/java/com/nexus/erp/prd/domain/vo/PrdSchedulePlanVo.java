package com.nexus.erp.prd.domain.vo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * APS排程方案视图对象
 *
 * @author nexus
 */
@Data
@AutoMapper(target = PrdSchedulePlan.class)
@EqualsAndHashCode(callSuper = true)
public class PrdSchedulePlanVo extends BaseEntity {

    /**
     * 排程方案ID
     */
    private Long planId;

    /**
     * 方案编号
     */
    private String planNo;

    /**
     * 方案名称
     */
    private String planName;

    /**
     * 车间ID
     */
    private Long workshopId;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 排产起始日
     */
    private Date startDate;

    /**
     * 排产跨度(天)
     */
    private Integer horizonDays;

    /**
     * 排序策略
     */
    private String strategy;

    /**
     * 参与排产工单数
     */
    private Integer woCount;

    /**
     * 排产工序任务数
     */
    private Integer taskCount;

    /**
     * 跳过工单数
     */
    private Integer skippedCount;

    /**
     * 总负荷工时(小时)
     */
    private BigDecimal totalHours;

    /**
     * 拖期工单数
     */
    private Integer delayedCount;

    /**
     * 总完工日期
     */
    private Date makespanDate;

    /**
     * 资源利用率快照JSON
     */
    private String utilizationJson;

    /**
     * 状态: COMPLETED-已生成, PUBLISHED-已发布
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}

package com.nexus.erp.prd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * APS排程方案对象 prd_schedule_plan
 * <p>
 * 一次排程运行的整体结果：参与工单数、总负荷、拖期数、资源利用率快照等汇总信息。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_schedule_plan")
public class PrdSchedulePlan extends TenantEntity {

    /**
     * 排程方案ID
     */
    @TableId(value = "plan_id")
    private Long planId;

    /**
     * 方案编号(APS+yyyyMMdd+4位流水)
     */
    private String planNo;

    /**
     * 方案名称
     */
    private String planName;

    /**
     * 车间ID(空=全部车间)
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
     * 排序策略: EDD-最早交期, CR-临界比, PRIORITY-优先级
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
     * 跳过工单数(无工艺路线等)
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

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

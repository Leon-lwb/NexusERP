package com.nexus.erp.prd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * APS 排程甘特图数据视图对象
 * <p>
 * 按工单分组聚合工序任务：工单一行，每道工序一个甘特条；
 * 附资源利用率面板与方案汇总指标，前端一次拉取渲染。
 *
 * @author nexus
 */
@Data
public class ScheduleGanttVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 排程方案ID
     */
    private Long planId;

    /**
     * 方案编号
     */
    private String planNo;

    /**
     * 排序策略
     */
    private String strategy;

    /**
     * 排产起始日
     */
    private Date startDate;

    /**
     * 排产跨度(天)
     */
    private Integer horizonDays;

    /**
     * 参与排产工单数
     */
    private Integer woCount;

    /**
     * 拖期工单数
     */
    private Integer delayedCount;

    /**
     * 跳过工单数
     */
    private Integer skippedCount;

    /**
     * 总负荷工时(小时)
     */
    private BigDecimal totalHours;

    /**
     * 甘特行(每工单一行)
     */
    private List<GanttRow> rows = new ArrayList<>();

    /**
     * 资源利用率列表(降序, 首位即瓶颈)
     */
    private List<ResourceRateVo> loads = new ArrayList<>();

    /**
     * 甘特图行(一个工单)
     */
    @Data
    public static class GanttRow implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 工单ID
         */
        private Long woId;

        /**
         * 工单编号
         */
        private String woNo;

        /**
         * 产品编码
         */
        private String materialCode;

        /**
         * 产品名称
         */
        private String materialName;

        /**
         * 交期
         */
        private Date dueDate;

        /**
         * 是否拖期
         */
        private Boolean delay;

        /**
         * 工序甘特条(按工序序号升序)
         */
        private List<GanttBar> bars = new ArrayList<>();
    }

    /**
     * 甘特条(一道工序)
     */
    @Data
    public static class GanttBar implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 任务ID
         */
        private Long taskId;

        /**
         * 工序序号
         */
        private Integer seqNum;

        /**
         * 工序名称
         */
        private String processName;

        /**
         * 资源类型: WORKSTATION/WORKSHOP/OUTSOURCE
         */
        private String resourceType;

        /**
         * 资源ID
         */
        private Long resourceId;

        /**
         * 资源名称
         */
        private String resourceName;

        /**
         * 工时(小时, 含准备)
         */
        private BigDecimal hours;

        /**
         * 开始时间
         */
        private Date startTime;

        /**
         * 结束时间
         */
        private Date endTime;

        /**
         * 占用天数
         */
        private Integer durationDays;
    }

    /**
     * 资源利用率项
     */
    @Data
    public static class ResourceRateVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 资源键(WORKSTATION:1 形式)
         */
        private String resourceKey;

        /**
         * 资源名称
         */
        private String resourceName;

        /**
         * 已排工时
         */
        private BigDecimal bookedHours;

        /**
         * 可用工时
         */
        private BigDecimal availableHours;

        /**
         * 利用率(%)
         */
        private BigDecimal rate;
    }
}

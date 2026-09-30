package com.nexus.erp.prd.schedule;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 排程引擎数据模型（纯 POJO，与 MyBatis/Spring 解耦，便于单元测试）
 *
 * @author nexus
 */
public final class ScheduleModel {

    /**
     * 工具类禁止实例化
     */
    private ScheduleModel() {
    }

    /**
     * 排序策略
     */
    public enum Strategy {

        /**
         * 最早交期优先（EDD）
         */
        EDD,

        /**
         * 临界比优先（CR = 剩余工期/剩余工时，小者先排）
         */
        CR,

        /**
         * 工单优先级字段优先
         */
        PRIORITY;

        /**
         * 解析策略字符串，空或无法识别时回退 EDD
         *
         * @param code 策略编码
         * @return 策略枚举
         */
        public static Strategy parse(String code) {
            if (code == null || code.isBlank()) {
                return EDD;
            }
            try {
                return valueOf(code.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return EDD;
            }
        }
    }

    /**
     * 待排产工单
     */
    @Data
    public static class WoInfo {

        private Long woId;
        private String woNo;
        private Long materialId;
        private String materialCode;
        private String materialName;
        /**
         * 剩余数量 = 计划数量 - 已完成数量
         */
        private BigDecimal remainingQty;
        /**
         * 优先级(小者优先)
         */
        private Long priority;
        /**
         * 交期(取工单计划结束日期)
         */
        private LocalDate dueDate;
        private Long workshopId;
        private String workshopName;
    }

    /**
     * 工艺路线步骤(排程工序输入)
     */
    @Data
    public static class StepInfo {

        private Long stepId;
        private Integer seqNum;
        private String processCode;
        private String processName;
        private Long workstationId;
        private String workstationCode;
        private String workstationName;
        /**
         * 标准工时(小时/件)
         */
        private BigDecimal standardHours;
        /**
         * 准备工时(小时)
         */
        private BigDecimal setupHours;
        /**
         * 是否委外工序(1=是)
         */
        private Integer isOutsource;
        /**
         * 等待时间(小时, 委外耗时)
         */
        private BigDecimal waitTime;
        /**
         * 转移时间(小时, 委外耗时)
         */
        private BigDecimal transferTime;
    }

    /**
     * 排程结果任务(工序级)
     */
    @Data
    public static class ScheduledTask {

        private WoInfo wo;
        private StepInfo step;
        /**
         * 资源类型: WORKSTATION/WORKSHOP/OUTSOURCE
         */
        private String resourceType;
        private Long resourceId;
        private String resourceName;
        private BigDecimal setupHours;
        /**
         * 加工工时 = 标准工时 × 剩余数量
         */
        private BigDecimal processHours;
        /** 逐日内部资源占用工时（含准备工时）；假期/无负荷日及委外不占。 */
        private Map<LocalDate, BigDecimal> dayLoads = Map.of();
        /**
         * 开始日(含)
         */
        private LocalDate startDay;
        /**
         * 结束日(含)
         */
        private LocalDate endDay;
        private int durationDays;
        /**
         * 所属工单拖期
         */
        private boolean delay;
    }

    /**
     * 资源负荷(利用率统计)
     */
    @Data
    public static class ResourceLoad {

        /**
         * 资源键: WORKSTATION:{id} / WORKSHOP:{id}
         */
        private String resourceKey;
        private String resourceName;
        private BigDecimal bookedHours;
        private BigDecimal availableHours;
        private BigDecimal rate;
    }

    /**
     * 排程结果
     */
    @Data
    public static class ScheduleResult {

        private List<ScheduledTask> tasks;
        /**
         * 被跳过的工单(无工艺路线/数量非法)
         */
        private List<WoInfo> skipped;
        private List<ResourceLoad> loads;
        /**
         * 总完工日期
         */
        private LocalDate makespan;
        private int delayedWoCount;
        private BigDecimal totalHours;
    }

    /**
     * 排程输入
     */
    @Data
    public static class ScheduleInput {

        private List<WoInfo> workOrders;
        /**
         * 工艺路线提供者: 物料ID → 步骤列表(seqNum 升序)
         */
        private Function<Long, List<StepInfo>> routingProvider;
        /**
         * 产能视图: 日期 → (资源键 → 剩余小时)。资源键格式 {@link #resourceKey(String, Long)}
         */
        private Map<LocalDate, Map<String, BigDecimal>> capacityByDate;
        /**
         * 假期(产能为 0)
         */
        private Set<LocalDate> holidays;
        private LocalDate startDate;
        private int horizonDays;
        private Strategy strategy;
    }

    /**
     * 构造资源键
     *
     * @param type 资源类型(WORKSTATION/WORKSHOP)
     * @param id   资源ID
     * @return 资源键
     */
    public static String resourceKey(String type, Long id) {
        return type + ":" + id;
    }
}

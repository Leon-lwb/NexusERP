package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES设备 OEE 聚合记录对象 eq_oee_record
 * <p>
 * 日粒度聚合: 按 (租户, 设备, 统计日期) 唯一。
 * 首版策略(稼动率优先):
 * <ul>
 *   <li>planned_minutes: 计划生产时长, 首版默认 480(8h 单班), 待 P1-6 工单联动后从工单推导;</li>
 *   <li>downtime_minutes: 停机时长, 来自 eq_repair.downtime_hours 当日求和 * 60;</li>
 *   <li>run_minutes = planned - downtime - maintenance;</li>
 *   <li>availability = run / planned (即稼动率);</li>
 *   <li>performance_rate / quality_rate: 首版占位 1.0, 待工单报工/质量数据补全;</li>
 *   <li>oee = availability * performance * quality (首版 = availability)。</li>
 * </ul>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_oee_record")
public class EqOeeRecord extends TenantEntity {

    /** 记录ID(主键) */
    @TableId(value = "record_id")
    private Long recordId;

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码(冗余) */
    private String equipmentCode;

    /** 设备名称(冗余) */
    private String equipmentName;

    /** 设备类型(冗余) */
    private String equipmentType;

    /** 工作中心ID(冗余, 车间级聚合用) */
    private Long workcenterId;

    /** 统计日期(日粒度) */
    private Date statDate;

    /** 计划生产时长(分钟) */
    private BigDecimal plannedMinutes;

    /** 停机时长(分钟, 来自 eq_repair.downtime_hours 求和) */
    private BigDecimal downtimeMinutes;

    /** 保养占用时长(分钟, 预留, 首版0) */
    private BigDecimal maintenanceMinutes;

    /** 运行时长(分钟) */
    private BigDecimal runMinutes;

    /** 故障次数(eq_repair 当日报修数) */
    private Integer faultCount;

    /** 可用率/稼动率(0~1) */
    private BigDecimal availability;

    /** 表现率(首版占位1.0) */
    private BigDecimal performanceRate;

    /** 质量率(首版占位1.0) */
    private BigDecimal qualityRate;

    /** OEE = availability * performance * quality */
    private BigDecimal oee;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

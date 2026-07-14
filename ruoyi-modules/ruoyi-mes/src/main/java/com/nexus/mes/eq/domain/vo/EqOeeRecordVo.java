package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqOeeRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES设备 OEE 聚合记录视图对象 eq_oee_record
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqOeeRecord.class)
public class EqOeeRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long recordId;

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码 */
    @ExcelProperty("设备编码")
    private String equipmentCode;

    /** 设备名称 */
    @ExcelProperty("设备名称")
    private String equipmentName;

    /** 设备类型 */
    @ExcelProperty("设备类型")
    private String equipmentType;

    /** 工作中心ID */
    private Long workcenterId;

    /** 统计日期 */
    @ExcelProperty("统计日期")
    private Date statDate;

    /** 计划生产时长(分钟) */
    @ExcelProperty("计划时长(分)")
    private BigDecimal plannedMinutes;

    /** 停机时长(分钟) */
    @ExcelProperty("停机时长(分)")
    private BigDecimal downtimeMinutes;

    /** 保养占用时长(分钟) */
    private BigDecimal maintenanceMinutes;

    /** 运行时长(分钟) */
    @ExcelProperty("运行时长(分)")
    private BigDecimal runMinutes;

    /** 故障次数 */
    @ExcelProperty("故障次数")
    private Integer faultCount;

    /** 可用率/稼动率(0~1) */
    @ExcelProperty("稼动率")
    private BigDecimal availability;

    /** 表现率(0~1) */
    private BigDecimal performanceRate;

    /** 质量率(0~1) */
    private BigDecimal qualityRate;

    /** OEE(0~1) */
    @ExcelProperty("OEE")
    private BigDecimal oee;

    /** 备注 */
    private String remark;
}

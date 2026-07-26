package com.nexus.mes.tg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.tg.domain.TgMaintenanceRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装保养记录视图对象 tg_maintenance_record
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TgMaintenanceRecord.class)
public class TgMaintenanceRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @ExcelProperty("记录ID")
    private Long recordId;

    /** 工装ID */
    private Long toolingId;

    /** 关联计划ID */
    private Long planId;

    /** 保养类型 */
    @ExcelProperty("保养类型")
    private String maintainType;

    /** 保养时间 */
    @ExcelProperty("保养时间")
    private Date maintainTime;

    /** 保养人ID */
    private Long maintainerId;

    /** 保养人姓名(冗余) */
    @ExcelProperty("保养人")
    private String maintainerName;

    /** 保养前次数 */
    @ExcelProperty("保养前(次)")
    private Integer usedCountBefore;

    /** 保养后次数 */
    @ExcelProperty("保养后(次)")
    private Integer usedCountAfter;

    /** 保养前时长 */
    @ExcelProperty("保养前(小时)")
    private BigDecimal usedHoursBefore;

    /** 保养后时长 */
    @ExcelProperty("保养后(小时)")
    private BigDecimal usedHoursAfter;

    /** 保养内容 */
    @ExcelProperty("保养内容")
    private String description;

    /** 保养成本 */
    @ExcelProperty("成本")
    private BigDecimal cost;

    /** 备注 */
    private String remark;
}

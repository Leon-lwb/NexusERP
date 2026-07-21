package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqMaintenanceRecord;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES设备保养执行记录视图对象 eq_maintenance_record
 *
 * <p>属于MES 设备管理模块，保养记录视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqMaintenanceRecord.class)
public class EqMaintenanceRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @ExcelProperty("记录ID")
    private Long recordId;

    /** 关联保养计划ID */
    private Long planId;

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码(联表查询填充) */
    @ExcelProperty("设备编码")
    private String equipmentCode;

    /** 设备名称(联表查询填充) */
    @ExcelProperty("设备名称")
    private String equipmentName;

    /** 保养类型 */
    @ExcelProperty("保养类型")
    private String maintenanceType;

    /** 执行人ID */
    private Long executorId;

    /** 执行人姓名 */
    @ExcelProperty("执行人")
    private String executorName;

    /** 执行时间 */
    @ExcelProperty("执行时间")
    private Date executeTime;

    /** 保养内容/执行情况 */
    @ExcelProperty("保养内容")
    private String content;

    /** 结果 */
    @ExcelProperty("结果")
    private String result;

    /** 备注 */
    private String remark;
}

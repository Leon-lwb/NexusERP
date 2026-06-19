package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqRepair;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES设备维修记录视图对象 eq_repair
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqRepair.class)
public class EqRepairVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 维修ID */
    @ExcelProperty("维修ID")
    private Long repairId;

    /** 维修单号 */
    @ExcelProperty("维修单号")
    private String repairNo;

    /** 设备ID */
    private Long equipmentId;

    /** 故障类型 */
    @ExcelProperty("故障类型")
    private String faultType;

    /** 故障描述 */
    @ExcelProperty("故障描述")
    private String faultDesc;

    /** 报修人ID */
    private Long reporterId;

    /** 报修人姓名 */
    @ExcelProperty("报修人")
    private String reporterName;

    /** 维修人ID */
    private Long repairerId;

    /** 维修人姓名 */
    @ExcelProperty("维修人")
    private String repairerName;

    /** 维修开始时间 */
    @ExcelProperty("维修开始时间")
    private Date repairStart;

    /** 维修结束时间 */
    @ExcelProperty("维修结束时间")
    private Date repairEnd;

    /** 停机时长(小时) */
    @ExcelProperty("停机时长(小时)")
    private BigDecimal downtimeHours;

    /** 维修成本 */
    @ExcelProperty("维修成本")
    private BigDecimal repairCost;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}

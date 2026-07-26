package com.nexus.mes.tg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.tg.domain.TgUsageRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装使用/领用记录视图对象 tg_usage_record
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TgUsageRecord.class)
public class TgUsageRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 使用记录ID */
    @ExcelProperty("记录ID")
    private Long usageId;

    /** 工装ID */
    private Long toolingId;

    /** 工单ID */
    private Long woId;

    /** 工单编号(冗余) */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 工位ID */
    private Long workstationId;

    /** 工位名称(冗余) */
    @ExcelProperty("工位")
    private String workstationName;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名(冗余) */
    @ExcelProperty("操作员")
    private String operatorName;

    /** 领用时间 */
    @ExcelProperty("领用时间")
    private Date checkoutTime;

    /** 归还时间 */
    @ExcelProperty("归还时间")
    private Date returnTime;

    /** 使用次数 */
    @ExcelProperty("使用次数")
    private Integer usageCount;

    /** 使用时长 */
    @ExcelProperty("使用时长")
    private BigDecimal usageHours;

    /** 状态: OUT/RETURNED */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}

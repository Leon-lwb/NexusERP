package com.nexus.mes.lt.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.lt.domain.LtWoConsumption;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单批次消耗视图对象 lt_wo_consumption
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = LtWoConsumption.class)
public class LtWoConsumptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消耗记录ID */
    @ExcelProperty("消耗记录ID")
    private Long consumptionId;
    private Long sourceAllocationId;

    /** 工单ID */
    private Long woId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 批次ID */
    private Long lotId;

    /** 批次编码 */
    @ExcelProperty("批次编码")
    private String lotCode;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 消耗数量 */
    @ExcelProperty("消耗数量")
    private BigDecimal consumedQty;

    /** 消耗时间 */
    @ExcelProperty("消耗时间")
    private Date consumedTime;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    @ExcelProperty("操作员")
    private String operatorName;

    /** 备注 */
    private String remark;
}

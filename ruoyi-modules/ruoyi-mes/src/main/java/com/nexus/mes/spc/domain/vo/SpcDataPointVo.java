package com.nexus.mes.spc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.spc.domain.SpcDataPoint;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES SPC数据点视图对象 spc_data_point
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SpcDataPoint.class)
public class SpcDataPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据点ID */
    private Long dataId;

    /** 控制图ID */
    private Long chartId;

    /** 样本组号 */
    @ExcelProperty("样本组号")
    private Integer sampleGroup;

    /** 组内序号 */
    private Integer sampleIndex;

    /** 值 */
    @ExcelProperty("值")
    private BigDecimal value;

    /** 组内离散度: 极差R/标准差S/移动极差MR */
    @ExcelProperty("组内离散度")
    private BigDecimal subValue;

    /** 是否超控制限(0/1) */
    @ExcelProperty("是否OOC")
    private String isOoc;

    /** 违规规则 */
    @ExcelProperty("违规规则")
    private String oocRule;

    /** 采集时间 */
    @ExcelProperty("采集时间")
    private Date collectTime;
}

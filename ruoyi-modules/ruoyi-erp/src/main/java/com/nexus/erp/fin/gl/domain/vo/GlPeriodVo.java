package com.nexus.erp.fin.gl.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 会计期间视图对象 gl_period
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = GlPeriod.class)
public class GlPeriodVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "期间ID")
    private Long periodId;

    @ExcelProperty(value = "会计年度")
    private Integer fiscalYear;

    @ExcelProperty(value = "期间号")
    private Integer periodNo;

    @ExcelProperty(value = "名称")
    private String periodName;

    @ExcelProperty(value = "开始日期")
    private Date startDate;

    @ExcelProperty(value = "结束日期")
    private Date endDate;

    @ExcelProperty(value = "状态")
    private String status;

    private Long closedBy;

    private Date closedTime;

    private Integer version;
}

package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchHoliday;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 节假日设置视图对象 sch_holiday
 *
 * @author nexus
 * @date 2025-12-17
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchHoliday.class)
public class SchHolidayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节假日ID
     */
    @ExcelProperty(value = "节假日ID")
    private Long holidayId;

    /**
     * 日期
     */
    @ExcelProperty(value = "日期")
    private Date holidayDate;

    /**
     * 节假日名称
     */
    @ExcelProperty(value = "节假日名称")
    private String holidayName;

    /**
     * 类型
     */
    @ExcelProperty(value = "类型")
    private String holidayType;

    /**
     * 年份
     */
    @ExcelProperty(value = "年份")
    private Long year;

    /**
     * 是否调休工作日
     */
    @ExcelProperty(value = "是否调休工作日")
    private Integer isWorkingDay;
}

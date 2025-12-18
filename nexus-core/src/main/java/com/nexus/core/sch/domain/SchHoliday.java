package com.nexus.core.sch.domain;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 节假日设置对象 sch_holiday
 *
 * @author nexus
 * @date 2025-12-17
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchHolidayMapper.SchHolidayResult")
public class SchHoliday extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 节假日ID
     */
    @TableId(value = "holiday_id", type = IdType.AUTO)
    private Long holidayId;
    /**
     * 日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date holidayDate;
    /**
     * 节假日名称
     */
    @Excel(name = "节假日名称")
    private String holidayName;
    /**
     * 类型
     */
    @Excel(name = "类型")
    private String holidayType;
    /**
     * 年份
     */
    @Excel(name = "年份")
    private Long year;
    /**
     * 是否调休工作日
     */
    @Excel(name = "是否调休工作日")
    private Integer isWorkingDay;
}
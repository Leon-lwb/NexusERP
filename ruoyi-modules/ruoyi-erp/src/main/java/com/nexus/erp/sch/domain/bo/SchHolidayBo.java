package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchHoliday;

import java.util.Date;

/**
 * 节假日设置业务对象 sch_holiday
 *
 * @author nexus
 * @date 2025-12-17
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchHoliday.class, reverseConvertGenerate = false)
public class SchHolidayBo extends BaseEntity {

    /**
     * 节假日ID
     */
    private Long holidayId;

    /**
     * 日期
     */
    @NotNull(message = "日期不能为空")
    private Date holidayDate;

    /**
     * 节假日名称
     */
    @NotBlank(message = "节假日名称不能为空")
    private String holidayName;

    /**
     * 类型
     */
    private String holidayType;

    /**
     * 年份
     */
    private Long year;

    /**
     * 是否调休工作日
     */
    private Integer isWorkingDay;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}

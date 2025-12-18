
package com.nexus.core.sch.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.time.LocalDateTime;

/**
 * 节假日设置对象 sch_holiday vo
 *
 * @author nexus
 * @date 2025-12-17
 */
@Data
public class SchHolidayVo extends BasePageDtoRequest {

    /**
     * 节假日ID
     */
    private Long holidayId;
    /**
     * 日期
     */
    private LocalDateTime holidayDate;
    /**
     * 节假日名称
     */
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
     * 备注
     */
    private String remark;

}
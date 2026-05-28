package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchShift;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 班次定义业务对象 sch_shift
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchShift.class, reverseConvertGenerate = false)
public class SchShiftBo extends BaseEntity {

    /**
     * 班次ID
     */
    private Long shiftId;

    /**
     * 班次编码
     */
    private String shiftCode;

    /**
     * 班次名称
     */
    @NotBlank(message = "班次名称不能为空")
    private String shiftName;

    /**
     * 排班计划ID
     */
    private Long planId;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 工时(小时)
     */
    private BigDecimal workHours;

    /**
     * 是否跨天
     */
    private Integer isCrossDay;

    /**
     * 休息开始时间
     */
    private Date restStartTime;

    /**
     * 休息结束时间
     */
    private Date restEndTime;

    /**
     * 迟到阈值(分钟)
     */
    private Long lateThreshold;

    /**
     * 早退阈值(分钟)
     */
    private Long earlyLeaveThreshold;

    /**
     * 班次类别
     */
    private String shiftCategory;

    /**
     * 班次显示颜色
     */
    private String color;

    /**
     * 排序
     */
    private Long sortOrder;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}

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
 * 排班计划对象 sch_plan
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchPlanMapper.SchPlanResult")
public class SchPlan extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID
     */
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;
    /**
     * 计划编码
     */
    @Excel(name = "计划编码")
    private String planCode;
    /**
     * 计划名称
     */
    @Excel(name = "计划名称")
    private String planName;
    /**
     * 开始日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "开始日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startDate;
    /**
     * 结束日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "结束日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date endDate;
    /**
     * 周期类型
     */
    @Excel(name = "周期类型")
    private String periodType;
    /**
     * 轮班方式
     */
    @Excel(name = "轮班方式")
    private Integer rotationType;
    /**
     * 状态
     */
    @Excel(name = "状态")
    private String status;
    /**
     * 发布人
     */
    @Excel(name = "发布人")
    private String publishedBy;
    /**
     * 发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "发布时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date publishedTime;
}
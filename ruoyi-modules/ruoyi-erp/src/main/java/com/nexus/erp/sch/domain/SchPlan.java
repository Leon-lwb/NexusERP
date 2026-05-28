package com.nexus.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 排班计划对象 sch_plan
 *
 * @author nexus
 * @date 2025-12-25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_plan")
public class SchPlan extends TenantEntity {

    /**
     * 计划ID
     */
    @TableId(value = "plan_id")
    private Long planId;
    /**
     * 计划编码
     */
    @ExcelProperty("计划编码")
    private String planCode;
    /**
     * 计划名称
     */
    @ExcelProperty("计划名称")
    private String planName;
    /**
     * 开始日期
     */
    @ExcelProperty("开始日期")
    private Date startDate;
    /**
     * 结束日期
     */
    @ExcelProperty("结束日期")
    private Date endDate;
    /**
     * 周期类型
     */
    @ExcelProperty("周期类型")
    private String periodType;
    /**
     * 轮班方式
     */
    @ExcelProperty("轮班方式")
    private Integer rotationType;
    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;
    /**
     * 发布人
     */
    @ExcelProperty("发布人")
    private String publishedBy;
    /**
     * 发布时间
     */
    @ExcelProperty("发布时间")
    private Date publishedTime;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}

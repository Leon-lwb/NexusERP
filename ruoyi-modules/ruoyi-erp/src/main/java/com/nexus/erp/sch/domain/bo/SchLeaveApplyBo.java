package com.nexus.erp.sch.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * App 请假申请入参对象
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
public class SchLeaveApplyBo {

    /** 请假类型(字典 sch_leave_type) */
    @NotBlank(message = "请假类型不能为空")
    private String leaveType;

    /** 请假开始日期，格式 yyyy-MM-dd */
    @NotBlank(message = "开始日期不能为空")
    private String startDate;

    /** 请假结束日期，格式 yyyy-MM-dd */
    @NotBlank(message = "结束日期不能为空")
    private String endDate;

    /** 请假事由 */
    @NotBlank(message = "请假事由不能为空")
    private String reason;
}

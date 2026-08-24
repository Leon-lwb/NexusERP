package com.nexus.erp.sch.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 考勤请假申请对象 sch_leave_apply
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_leave_apply")
public class SchLeaveApply extends TenantEntity {

    /**
     * 请假申请ID
     */
    @TableId(value = "leave_id")
    private Long leaveId;

    /**
     * 申请单号
     */
    private String leaveNo;

    /**
     * 申请人(工人)ID
     */
    private Long workerId;

    /**
     * 申请人姓名
     */
    private String workerName;

    /**
     * 请假类型(字典 sch_leave_type)
     */
    private String leaveType;

    /**
     * 请假开始日期
     */
    private Date startDate;

    /**
     * 请假结束日期
     */
    private Date endDate;

    /**
     * 请假时长(天)
     */
    private BigDecimal durationDays;

    /**
     * 请假事由
     */
    private String reason;

    /**
     * 申请状态: pending待审批 / approved已通过 / rejected已驳回 / cancelled已取消
     */
    private String applyStatus;

    /**
     * 审批人用户ID
     */
    private Long approveUserId;

    /**
     * 审批人姓名
     */
    private String approveUserName;

    /**
     * 审批时间
     */
    private Date approveTime;

    /**
     * 审批备注
     */
    private String approveRemark;

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

package com.nexus.erp.sch.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchLeaveApply;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 考勤请假申请视图对象 sch_leave_apply
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
@AutoMapper(target = SchLeaveApply.class)
public class SchLeaveApplyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 请假申请ID */
    private Long leaveId;

    /** 申请单号 */
    private String leaveNo;

    /** 申请人(工人)ID */
    private Long workerId;

    /** 申请人姓名 */
    private String workerName;

    /** 请假类型(字典 sch_leave_type) */
    private String leaveType;

    /** 请假开始日期 */
    private Date startDate;

    /** 请假结束日期 */
    private Date endDate;

    /** 请假时长(天) */
    private BigDecimal durationDays;

    /** 请假事由 */
    private String reason;

    /** 申请状态: pending/approved/rejected/cancelled */
    private String applyStatus;

    /** 审批人用户ID */
    private Long approveUserId;

    /** 审批人姓名 */
    private String approveUserName;

    /** 审批时间 */
    private Date approveTime;

    /** 审批备注 */
    private String approveRemark;

    /** 创建时间 */
    private Date createTime;
}

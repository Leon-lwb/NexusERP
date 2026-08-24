package com.nexus.erp.sch.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchSupplementApply;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 考勤补卡申请视图对象 sch_supplement_apply
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
@AutoMapper(target = SchSupplementApply.class)
public class SchSupplementApplyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 补卡申请ID */
    private Long applyId;

    /** 申请单号 */
    private String applyNo;

    /** 申请人(工人)ID */
    private Long workerId;

    /** 申请人姓名 */
    private String workerName;

    /** 补卡日期 */
    private Date applyDate;

    /** 补卡类型: clock_in/clock_out/full_day */
    private String applyType;

    /** 补卡原因 */
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

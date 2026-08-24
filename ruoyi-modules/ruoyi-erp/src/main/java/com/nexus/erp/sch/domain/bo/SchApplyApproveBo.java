package com.nexus.erp.sch.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * App 考勤申请审批入参对象（补卡/请假共用）
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
public class SchApplyApproveBo {

    /** 申请单ID（补卡 applyId / 请假 leaveId） */
    @NotNull(message = "申请单ID不能为空")
    private Long applyId;

    /** 审批结果: true通过 / false驳回 */
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    /** 审批备注(驳回时建议填写原因) */
    @NotBlank(message = "审批备注不能为空")
    private String approveRemark;
}

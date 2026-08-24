package com.nexus.erp.sch.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 考勤补卡申请对象 sch_supplement_apply
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_supplement_apply")
public class SchSupplementApply extends TenantEntity {

    /**
     * 补卡申请ID
     */
    @TableId(value = "apply_id")
    private Long applyId;

    /**
     * 申请单号
     */
    private String applyNo;

    /**
     * 申请人(工人)ID
     */
    private Long workerId;

    /**
     * 申请人姓名
     */
    private String workerName;

    /**
     * 补卡日期
     */
    private Date applyDate;

    /**
     * 补卡类型: clock_in上班缺卡 / clock_out下班缺卡 / full_day全天缺卡
     */
    private String applyType;

    /**
     * 补卡原因
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

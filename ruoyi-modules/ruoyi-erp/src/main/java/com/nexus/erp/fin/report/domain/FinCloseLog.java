package com.nexus.erp.fin.report.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 期末关账执行日志 fin_close_log(向导步骤审计)
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fin_close_log")
public class FinCloseLog extends TenantEntity {

    /** 日志ID */
    @TableId(value = "log_id")
    private Long logId;

    /** 期间ID */
    private Long periodId;

    /** 类型 MONTHLY-月结 YEARLY-年结 */
    private String closeType;

    /** 步骤 TRIAL_CHECK/CARRY_PL/REVALUATION/REPORT_SNAPSHOT/CLOSE_PERIOD/CARRY_FORWARD */
    private String step;

    /** 状态 SUCCESS/FAILED */
    private String status;

    /** 结果/错误信息 */
    private String message;

    /** 操作人 */
    private Long operatorId;

    /** 执行时间 */
    private Date createTime;
}

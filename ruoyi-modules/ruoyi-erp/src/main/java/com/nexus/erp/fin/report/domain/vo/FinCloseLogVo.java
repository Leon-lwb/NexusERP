package com.nexus.erp.fin.report.domain.vo;

import com.nexus.erp.fin.report.domain.FinCloseLog;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 关账日志视图对象
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
@AutoMapper(target = FinCloseLog.class)
public class FinCloseLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 期间ID */
    private Long periodId;

    /** 类型 MONTHLY/YEARLY */
    private String closeType;

    /** 步骤 */
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

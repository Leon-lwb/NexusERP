package com.nexus.erp.fin.gl.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会计期间 gl_period
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("gl_period")
public class GlPeriod extends TenantEntity {

    /** 期间ID */
    @TableId(value = "period_id")
    private Long periodId;

    /** 会计年度 */
    private Integer fiscalYear;

    /** 期间号 1-12(0=调整期) */
    private Integer periodNo;

    /** 名称(如 2026-07) */
    private String periodName;

    /** 开始日期 */
    private Date startDate;

    /** 结束日期 */
    private Date endDate;

    /** 状态 OPEN/CLOSED/LOCKED */
    private String status;

    /** 关账人 */
    private Long closedBy;

    /** 关账时间 */
    private Date closedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}

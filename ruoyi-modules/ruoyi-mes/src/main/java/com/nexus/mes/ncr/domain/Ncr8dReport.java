package com.nexus.mes.ncr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES NCR 8D报告对象 ncr_8d_report
 * <p>属于 MES NCR 不合格品管理模块，表达 8D 问题解决报告。</p>
 * <p>D1~D8 对应 8D 方法论八个步骤。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ncr_8d_report")
public class Ncr8dReport extends TenantEntity {

    /** 报告ID(主键) */
    @TableId(value = "report_id")
    private Long reportId;

    /** NCR单ID */
    private Long ncrId;

    /** D1 组建团队 */
    private String d1Team;

    /** D2 问题描述 */
    private String d2Problem;

    /** D3 临时措施 */
    private String d3Interim;

    /** D4 根本原因 */
    private String d4RootCause;

    /** D5 永久措施 */
    private String d5Permanent;

    /** D6 实施验证 */
    private String d6Implement;

    /** D7 预防再发 */
    private String d7Prevent;

    /** D8 团队认可 */
    private String d8Recognize;

    /** 状态: DRAFT/IN_PROGRESS/COMPLETED */
    private String status;

    /** 完成人ID */
    private Long completedBy;

    /** 完成时间 */
    private Date completedTime;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
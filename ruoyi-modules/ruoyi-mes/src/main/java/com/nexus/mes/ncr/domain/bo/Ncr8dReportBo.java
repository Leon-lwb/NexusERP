package com.nexus.mes.ncr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.ncr.domain.Ncr8dReport;

import java.util.Date;

/**
 * MES NCR 8D报告业务对象 ncr_8d_report
 * <p>属于 MES NCR 不合格品管理模块，承载 8D 报告的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Ncr8dReport.class, reverseConvertGenerate = false)
public class Ncr8dReportBo extends BaseEntity {

    /** 报告ID */
    private Long reportId;

    /** NCR单ID(查询用) */
    @NotNull(message = "NCR单ID不能为空")
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

    /** 状态(查询用) */
    private String status;

    /** 完成人ID */
    private Long completedBy;

    /** 完成时间 */
    private Date completedTime;

    /** 批量删除ID集合 */
    private Long[] ids;
}
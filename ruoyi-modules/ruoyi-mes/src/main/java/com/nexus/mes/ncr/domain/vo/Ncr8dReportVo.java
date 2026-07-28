package com.nexus.mes.ncr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.ncr.domain.Ncr8dReport;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES NCR 8D报告视图对象 ncr_8d_report
 * <p>属于 MES NCR 不合格品管理模块，作为 8D 报告的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Ncr8dReport.class)
public class Ncr8dReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报告ID */
    @ExcelProperty("报告ID")
    private Long reportId;

    /** NCR单ID */
    private Long ncrId;

    /** D1 组建团队 */
    @ExcelProperty("D1 组建团队")
    private String d1Team;

    /** D2 问题描述 */
    @ExcelProperty("D2 问题描述")
    private String d2Problem;

    /** D3 临时措施 */
    @ExcelProperty("D3 临时措施")
    private String d3Interim;

    /** D4 根本原因 */
    @ExcelProperty("D4 根本原因")
    private String d4RootCause;

    /** D5 永久措施 */
    @ExcelProperty("D5 永久措施")
    private String d5Permanent;

    /** D6 实施验证 */
    @ExcelProperty("D6 实施验证")
    private String d6Implement;

    /** D7 预防再发 */
    @ExcelProperty("D7 预防再发")
    private String d7Prevent;

    /** D8 团队认可 */
    @ExcelProperty("D8 团队认可")
    private String d8Recognize;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 完成人ID */
    private Long completedBy;

    /** 完成人名称(由 @Translation 按 completedBy 自动翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "completedBy")
    private String completedByName;

    /** 完成时间 */
    @ExcelProperty("完成时间")
    private Date completedTime;
}
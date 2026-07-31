package com.nexus.erp.fin.report.domain.vo;

import com.nexus.erp.fin.report.domain.ReportItem;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 报表项目映射视图对象
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
@AutoMapper(target = ReportItem.class)
public class ReportItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long itemId;

    /** 报表类型 */
    private String reportType;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 父项目ID */
    private Long parentItemId;

    /** 是否合计行 */
    private String isSummary;

    /** 科目编码匹配 */
    private String accountCodeMatch;

    /** 取值符号 */
    private String sign;

    /** 状态 */
    private String status;
}

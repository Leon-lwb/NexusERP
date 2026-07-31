package com.nexus.erp.fin.report.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.fin.report.domain.ReportItem;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 报表项目映射业务对象
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReportItem.class, reverseConvertGenerate = false)
public class ReportItemBo extends BaseEntity {

    /** 项目ID */
    private Long itemId;

    /** 报表类型 BALANCE/INCOME/CASH */
    private String reportType;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 父项目ID */
    private Long parentItemId;

    /** 是否合计行 0-否 1-是 */
    private String isSummary;

    /** 科目编码匹配 */
    private String accountCodeMatch;

    /** 取值符号 1-正 -1-负 */
    private String sign;

    /** 状态 0-正常 1-停用 */
    private String status;

    /** 批量操作ID */
    private Long[] ids;
}

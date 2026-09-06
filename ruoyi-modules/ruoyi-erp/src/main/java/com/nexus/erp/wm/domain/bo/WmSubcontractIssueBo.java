package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSubcontractIssue;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外发料单业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmSubcontractIssue.class, reverseConvertGenerate = false)
public class WmSubcontractIssueBo extends BaseEntity {

    /** 发料单ID */
    private Long issueId;
    /** 发料单号 */
    private String issueNo;
    /** 委外订单ID */
    private Long subcontractId;
    /** 委外单号(冗余) */
    private String subcontractNo;
    /** 发料日期 */
    private Date issueDate;
    /** 状态: DRAFT/POSTED */
    private String status;
    /** 发料人 */
    private String issuerName;
    /** 发料总数量 */
    private BigDecimal totalQty;
    /** 创建部门 */
    private Long createDept;
    /** 创建者 */
    private Long createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新者 */
    private Long updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 备注 */
    private String remark;

    /** 发料单行列表(创建时提交) */
    private java.util.List<WmSubcontractIssueLineBo> lines;
}

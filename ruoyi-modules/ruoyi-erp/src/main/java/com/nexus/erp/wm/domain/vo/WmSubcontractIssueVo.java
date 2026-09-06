package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSubcontractIssue;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外发料单视图对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmSubcontractIssue.class)
public class WmSubcontractIssueVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
}

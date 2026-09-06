package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外发料单对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_subcontract_issue")
public class WmSubcontractIssue extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 发料单ID */
    Long issueId;
    /** 发料单号 */
    String issueNo;
    /** 委外订单ID */
    Long subcontractId;
    /** 委外单号(冗余) */
    String subcontractNo;
    /** 发料日期 */
    Date issueDate;
    /** 状态: DRAFT/POSTED */
    String status;
    /** 发料人 */
    String issuerName;
    /** 发料总数量 */
    BigDecimal totalQty;
    /** 创建部门 */
    Long createDept;
    /** 创建者 */
    Long createBy;
    /** 创建时间 */
    Date createTime;
    /** 更新者 */
    Long updateBy;
    /** 更新时间 */
    Date updateTime;
    /** 备注 */
    String remark;
}

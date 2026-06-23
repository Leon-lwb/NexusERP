package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_production_issue")
public class WmProductionIssue extends TenantEntity {

    @TableId(value = "issue_id")
    private Long issueId;

    private String issueCode;
    private Long woId;
    private String woNo;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Date issueDate;
    private String status;
    /** 领料类型(NORMAL普通/BACKFLUSH倒冲) */
    private String issueType;
    /** 领料人ID */
    private Long issuerId;
    /** 领料人名称 */
    private String issuerName;
    /** 总数量 */
    private BigDecimal totalQty;
    /** 总金额 */
    private BigDecimal totalAmount;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableField(exist = false)
    private List<WmProductionIssueLine> wmProductionIssueLineList;

    @TableLogic
    private String delFlag;
}

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

/**
 * 生产领料单对象 wm_production_issue
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_production_issue")
public class WmProductionIssue extends TenantEntity {

    /**
     * 生产领料单ID
     */
    @TableId(value = "issue_id")
    private Long issueId;

    /**
     * 领料单号
     */
    private String issueCode;

    /**
     * 关联工单ID
     */
    private Long woId;

    /**
     * 工单编号
     */
    private String woNo;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 领料日期
     */
    private Date issueDate;

    /**
     * 状态
     */
    private String status;

    /**
     * 领料类型(NORMAL普通/BACKFLUSH倒冲)
     */
    private String issueType;

    /**
     * 领料人ID
     */
    private Long issuerId;

    /**
     * 领料人名称
     */
    private String issuerName;

    /**
     * 总数量
     */
    private BigDecimal totalQty;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 生产领料单行项目列表
     */
    @TableField(exist = false)
    private List<WmProductionIssueLine> wmProductionIssueLineList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

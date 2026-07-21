package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

/**
 * 杂项出库单对象 wm_misc_issue
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_misc_issue")
public class WmMiscIssue extends TenantEntity {

    /**
     * 出库单ID
     */
    @TableId(value = "issue_id")
    private Long issueId;

    /**
     * 出库单编码
     */
    private String issueCode;

    /**
     * 出库类型
     */
    private String issueType;

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
     * 出库日期
     */
    private Date issueDate;

    /**
     * 单据状态
     */
    private String status;

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
     * 杂项出库单行项目列表
     */
    @TableField(exist = false)
    private List<WmMiscIssueLine> wmMiscIssueLineList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

package com.nexus.core.wm.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 销售出库对象 wm_sales_issue
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmSalesIssueMapper.WmSalesIssueResult")
public class WmSalesIssue extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 出库单ID
     */
    @TableId(value = "issue_id", type = IdType.AUTO)
    private Long issueId;
    /**
     * 出库单编号
     */
    @Excel(name = "出库单编号")
    private String issueCode;
    /**
     * 出库单名称
     */
    @Excel(name = "出库单名称")
    private String issueName;
    /**
     * 销售订单ID
     */
    @Excel(name = "销售订单ID")
    private Long soId;
    /**
     * 销售订单编号
     */
    @Excel(name = "销售订单编号")
    private String soNo;
    /**
     * 客户ID
     */
    @Excel(name = "客户ID")
    private Long clientId;
    /**
     * 客户编码
     */
    @Excel(name = "客户编码")
    private String clientCode;
    /**
     * 客户名称
     */
    @Excel(name = "客户名称")
    private String clientName;
    /**
     * 仓库ID
     */
    @Excel(name = "仓库ID")
    private Long warehouseId;
    /**
     * 仓库编码
     */
    @Excel(name = "仓库编码")
    private String warehouseCode;
    /**
     * 仓库名称
     */
    @Excel(name = "仓库名称")
    private String warehouseName;
    /**
     * 出库日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "出库日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date issueDate;
    /**
     * 出库总数量
     */
    @Excel(name = "出库总数量")
    private BigDecimal totalQuantity;
    /**
     * 出库总金额
     */
    @Excel(name = "出库总金额")
    private BigDecimal totalAmount;
    /**
     * 单据状态
     */
    @Excel(name = "单据状态")
    private String status;
    /**
     * 已选审批流程模板ID
     */
    private Long selectFlowId;
    /**
     * 预留字段1
     */
    @Excel(name = "预留字段1")
    private String attr1;
    /**
     * 预留字段2
     */
    @Excel(name = "预留字段2")
    private String attr2;
    /**
     * 预留字段3
     */
    @Excel(name = "预留字段3")
    private Long attr3;
    /**
     * 预留字段4
     */
    @Excel(name = "预留字段4")
    private Long attr4;

    /**
     * 销售出库明细列表
     */
    @TableField(exist = false)
    private List<WmSalesIssueLine> wmSalesIssueLineList;
}
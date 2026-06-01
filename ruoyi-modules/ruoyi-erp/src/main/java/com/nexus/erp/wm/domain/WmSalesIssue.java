package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
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
 * 销售出库对象 wm_sales_issue
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_sales_issue")
public class WmSalesIssue extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 出库单ID
     */
    @TableId(value = "issue_id")
    private Long issueId;

    /**
     * 出库单编号
     */
    @ExcelProperty("出库单编号")
    private String issueCode;

    /**
     * 出库单名称
     */
    @ExcelProperty("出库单名称")
    private String issueName;

    /**
     * 销售订单ID
     */
    @ExcelProperty("销售订单ID")
    private Long soId;

    /**
     * 销售订单编号
     */
    @ExcelProperty("销售订单编号")
    private String soNo;

    /**
     * 客户ID
     */
    @ExcelProperty("客户ID")
    private Long clientId;

    /**
     * 客户编码
     */
    @ExcelProperty("客户编码")
    private String clientCode;

    /**
     * 客户名称
     */
    @ExcelProperty("客户名称")
    private String clientName;

    /**
     * 仓库ID
     */
    @ExcelProperty("仓库ID")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 出库日期
     */
    @ExcelProperty("出库日期")
    private Date issueDate;

    /**
     * 出库总数量
     */
    @ExcelProperty("出库总数量")
    private BigDecimal totalQuantity;

    /**
     * 出库总金额
     */
    @ExcelProperty("出库总金额")
    private BigDecimal totalAmount;

    /**
     * 单据状态
     */
    @ExcelProperty("单据状态")
    private String status;

    /**
     * 已选审批流程模板ID
     */
    private Long selectFlowId;

    /**
     * 预留字段1
     */
    @ExcelProperty("预留字段1")
    private String attr1;

    /**
     * 预留字段2
     */
    @ExcelProperty("预留字段2")
    private String attr2;

    /**
     * 预留字段3
     */
    @ExcelProperty("预留字段3")
    private Long attr3;

    /**
     * 预留字段4
     */
    @ExcelProperty("预留字段4")
    private Long attr4;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

    /**
     * 销售出库明细列表
     */
    @TableField(exist = false)
    private List<WmSalesIssueLine> wmSalesIssueLineList;
}

package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmSalesIssue;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售出库视图对象 wm_sales_issue
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmSalesIssue.class)
public class WmSalesIssueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 出库单ID
     */
    @ExcelProperty(value = "出库单ID")
    private Long issueId;

    /**
     * 出库单编号
     */
    @ExcelProperty(value = "出库单编号")
    private String issueCode;

    /**
     * 出库单名称
     */
    @ExcelProperty(value = "出库单名称")
    private String issueName;

    /**
     * 销售订单ID
     */
    private Long soId;

    /**
     * 销售订单编号
     */
    @ExcelProperty(value = "销售订单编号")
    private String soNo;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户编码
     */
    @ExcelProperty(value = "客户编码")
    private String clientCode;

    /**
     * 客户名称
     */
    @ExcelProperty(value = "客户名称")
    private String clientName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 出库日期
     */
    @ExcelProperty(value = "出库日期")
    private Date issueDate;

    /**
     * 出库总数量
     */
    @ExcelProperty(value = "出库总数量")
    private BigDecimal totalQuantity;

    /**
     * 出库总金额
     */
    @ExcelProperty(value = "出库总金额")
    private BigDecimal totalAmount;

    /**
     * 单据状态
     */
    @ExcelProperty(value = "单据状态")
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    @ExcelProperty(value = "预留字段1")
    private String attr1;

    /**
     * 预留字段2
     */
    @ExcelProperty(value = "预留字段2")
    private String attr2;

    /**
     * 预留字段3
     */
    @ExcelProperty(value = "预留字段3")
    private Long attr3;

    /**
     * 预留字段4
     */
    @ExcelProperty(value = "预留字段4")
    private Long attr4;

    /**
     * 销售出库明细列表
     */
    private List<WmSalesIssueLineVo> wmSalesIssueLineList;
}

package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSalesIssue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售出库业务对象 wm_sales_issue
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmSalesIssue.class, reverseConvertGenerate = false)
public class WmSalesIssueBo extends BaseEntity {

    /**
     * 出库单ID
     */
    private Long issueId;

    /**
     * 出库单编号
     */
    private String issueCode;

    /**
     * 出库单名称
     */
    private String issueName;

    /**
     * 销售订单ID
     */
    private Long soId;

    /**
     * 销售订单编号
     */
    private String soNo;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户编码
     */
    private String clientCode;

    /**
     * 客户名称
     */
    private String clientName;

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
     * 出库总数量
     */
    private BigDecimal totalQuantity;

    /**
     * 出库总金额
     */
    private BigDecimal totalAmount;

    /**
     * 单据状态
     */
    private String status;

    /**
     * 已选审批流程模板ID
     */
    private Long selectFlowId;

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
     * 备注
     */
    private String remark;

    /**
     * 销售出库明细列表
     */
    private List<WmSalesIssueLineBo> wmSalesIssueLineList;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}

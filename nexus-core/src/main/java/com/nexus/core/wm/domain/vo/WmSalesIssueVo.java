
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售出库对象 wm_sales_issue vo
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
public class WmSalesIssueVo extends BasePageDtoRequest {

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
    private LocalDateTime issueDate;
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

}
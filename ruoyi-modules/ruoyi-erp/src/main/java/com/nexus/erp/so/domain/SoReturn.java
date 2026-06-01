package com.nexus.erp.so.domain;

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
 * 销售退货对象 so_return
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_return")
public class SoReturn extends TenantEntity {

    /** 退货ID */
    @TableId(value = "return_id")
    private Long returnId;

    /** 退货单号 */
    private String returnNo;
    /** 销售订单ID */
    private Long soId;
    /** 销售订单号 */
    private String soNo;
    /** 客户ID */
    private Long clientId;
    /** 客户名称 */
    private String clientName;
    /** 销售代表ID */
    private Long salesRepId;
    /** 销售代表名称 */
    private String salesRepName;
    /** 退货日期 */
    private Date returnDate;
    /** 退货原因 */
    private String returnReason;
    /** 退货类型 */
    private String returnType;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 仓库ID */
    private Long warehouseId;
    /** 仓库编码 */
    private String warehouseCode;
    /** 仓库名称 */
    private String warehouseName;
    /** 状态 */
    private String status;
    /** 审批流程ID */
    private Long selectFlowId;
    /** 备注 */
    private String remark;
    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private Long attr3;
    /** 扩展字段4 */
    private Long attr4;

    /** 退货明细行列表（非数据库字段） */
    @TableField(exist = false)
    private List<SoReturnLine> soReturnLineList;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

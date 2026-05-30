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
 * 库存调拨单对象 wm_transfer
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_transfer")
public class WmTransfer extends TenantEntity {

    /** 调拨单ID */
    @TableId(value = "transfer_id")
    private Long transferId;

    /** 调拨单号 */
    private String transferNo;
    /** 来源仓库ID */
    private Long fromWarehouseId;
    /** 来源仓库编码 */
    private String fromWarehouseCode;
    /** 来源仓库名称 */
    private String fromWarehouseName;
    /** 来源库区ID */
    private Long fromAreaId;
    /** 来源库区编码 */
    private String fromAreaCode;
    /** 来源库区名称 */
    private String fromAreaName;
    /** 目标仓库ID */
    private Long toWarehouseId;
    /** 目标仓库编码 */
    private String toWarehouseCode;
    /** 目标仓库名称 */
    private String toWarehouseName;
    /** 目标库区ID */
    private Long toAreaId;
    /** 目标库区编码 */
    private String toAreaCode;
    /** 目标库区名称 */
    private String toAreaName;
    /** 调拨类型 */
    private String transferType;
    /** 调拨总数量 */
    private BigDecimal totalQuantity;
    /** 单据状态 */
    private String status;
    /** 操作人ID */
    private Long operatorId;
    /** 操作人名称 */
    private String operatorName;
    /** 调拨日期 */
    private Date transferDate;
    /** 执行日期 */
    private Date executeDate;
    /** 审批流程ID */
    private Long selectFlowId;
    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;

    /** 调拨单行列表 */
    @TableField(exist = false)
    private List<WmTransferLine> wmTransferLineList;
}

package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 逐 MRP 需求明细的采购来源占用；业务写入只能在同一 ERP 事务的物料 guard 下进行。 */
@Data
@TableName("pu_requirement_allocation_ledger")
public class PuRequirementAllocationLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "allocation_id", type = IdType.ASSIGN_ID)
    private Long allocationId;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("material_id")
    private Long materialId;

    @TableField("source_type")
    private String sourceType;

    @TableField("detail_id")
    private Long detailId;

    @TableField("requisition_line_id")
    private Long requisitionLineId;

    @TableField("allocation_no")
    private Integer allocationNo;

    @TableField("order_line_id")
    private Long orderLineId;

    @TableField("base_uom_id")
    private Long baseUomId;

    @TableField("covered_demand_base_qty")
    private BigDecimal coveredDemandBaseQty;

    @TableField("occupied_base_qty")
    private BigDecimal occupiedBaseQty;

    @TableField("fulfilled_base_qty")
    private BigDecimal fulfilledBaseQty;

    @TableField("released_base_qty")
    private BigDecimal releasedBaseQty;

    @TableField("allocation_status")
    private String allocationStatus;

    @TableField("allocation_key")
    private String allocationKey;

    @TableField("decision_idempotency_key")
    private String decisionIdempotencyKey;

    /** INSERT 使用 SQL 默认 0；修改/释放仅使用 Mapper 的版本 CAS。 */
    @Version
    @TableField("version")
    private Integer version;

    @TableField("create_time")
    private Date createTime;

    @TableField("transfer_time")
    private Date transferTime;

    @TableField("released_at")
    private Date releasedAt;

    @TableField("release_reason")
    private String releaseReason;

    @TableField("update_time")
    private Date updateTime;
}

package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 来源占用状态变更的追加事件；eventId 由调用方提供雪花 ID。 */
@Data
@TableName("pu_requirement_allocation_event")
public class PuRequirementAllocationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "event_id", type = IdType.ASSIGN_ID)
    private Long eventId;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("allocation_id")
    private Long allocationId;

    /** Allocation-local causal version: CREATE=0, transition=the resulting ledger CAS version. NULL legacy evidence is untrusted. */
    @TableField("event_sequence")
    private Integer eventSequence;

    @TableField("event_key")
    private String eventKey;

    @TableField("event_type")
    private String eventType;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    @TableField("occupied_base_before")
    private BigDecimal occupiedBaseBefore;

    @TableField("occupied_base_after")
    private BigDecimal occupiedBaseAfter;

    @TableField("requisition_line_id")
    private Long requisitionLineId;

    @TableField("order_line_id")
    private Long orderLineId;

    @TableField("actor_id")
    private Long actorId;

    @TableField("occurred_at")
    private Date occurredAt;
}

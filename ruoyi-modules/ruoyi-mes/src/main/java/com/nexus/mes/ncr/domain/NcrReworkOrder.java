package com.nexus.mes.ncr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES NCR返工单对象 ncr_rework_order
 * <p>属于 MES NCR 不合格品管理模块，表达 NCR 处置产生的返工工单关联记录。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ncr_rework_order")
public class NcrReworkOrder extends TenantEntity {

    /** 返工记录ID(主键) */
    @TableId(value = "rework_id")
    private Long reworkId;

    /** 返工单编号 */
    private String reworkCode;

    /** NCR单ID */
    private Long ncrId;

    /** 原始工单ID */
    private Long originalWoId;

    /** 原始工单编号(冗余) */
    private String originalWoNo;

    /** 返工工单ID */
    private Long reworkWoId;

    /** 返工工单编号(冗余) */
    private String reworkWoNo;

    /** 返工数量 */
    private BigDecimal reworkQty;

    /** 返工原因 */
    private String reworkReason;

    /** 状态: PENDING/IN_PROGRESS/COMPLETED */
    private String status;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
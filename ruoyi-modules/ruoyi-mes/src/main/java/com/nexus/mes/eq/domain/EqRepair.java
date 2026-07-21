package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES设备维修记录对象 eq_repair
 *
 * <p>属于MES 设备管理模块，维修持久化实体。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_repair")
public class EqRepair extends TenantEntity {

    /** 维修ID(主键) */
    @TableId(value = "repair_id")
    private Long repairId;

    /** 维修单号 */
    private String repairNo;

    /** 设备ID */
    private Long equipmentId;

    /** 故障类型 */
    private String faultType;

    /** 故障描述 */
    private String faultDesc;

    /** 报修人ID */
    private Long reporterId;

    /** 报修人姓名 */
    private String reporterName;

    /** 维修人ID */
    private Long repairerId;

    /** 维修人姓名 */
    private String repairerName;

    /** 维修开始时间 */
    private Date repairStart;

    /** 维修结束时间 */
    private Date repairEnd;

    /** 停机时长(小时) */
    private BigDecimal downtimeHours;

    /** 维修成本 */
    private BigDecimal repairCost;

    /** 状态: REPORTED/REPAIRING/COMPLETED */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

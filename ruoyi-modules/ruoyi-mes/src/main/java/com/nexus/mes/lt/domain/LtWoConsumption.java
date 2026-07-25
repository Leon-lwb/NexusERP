package com.nexus.mes.lt.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单批次消耗记录对象 lt_wo_consumption
 * <p>属于 MES 批次管理模块，表达 工单领料批次消耗 的领域模型。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lt_wo_consumption")
public class LtWoConsumption extends TenantEntity {

    /** 消耗记录ID(主键) */
    @TableId(value = "consumption_id")
    private Long consumptionId;

    /** 工单ID */
    private Long woId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 批次ID */
    private Long lotId;

    /** 批次编码(冗余) */
    private String lotCode;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 消耗数量 */
    private BigDecimal consumedQty;

    /** 消耗时间 */
    private Date consumedTime;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名(冗余) */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

package com.nexus.mes.lt.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.lt.domain.LtWoConsumption;

import java.math.BigDecimal;

/**
 * MES工单批次消耗业务对象 lt_wo_consumption
 * <p>属于 MES 批次管理模块，承载 工单批次消耗记录 的增删入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = LtWoConsumption.class, reverseConvertGenerate = false)
public class LtWoConsumptionBo extends BaseEntity {

    /** 消耗记录ID */
    private Long consumptionId;

    /** 工单ID */
    @NotNull(message = "工单ID不能为空")
    private Long woId;

    /** 批次ID */
    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 消耗数量 */
    @NotNull(message = "消耗数量不能为空")
    private BigDecimal consumedQty;

    /** 操作员ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

package com.nexus.mes.ncr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.ncr.domain.NcrReworkOrder;

import java.math.BigDecimal;

/**
 * MES NCR返工单业务对象 ncr_rework_order
 * <p>属于 MES NCR 不合格品管理模块，承载 NCR 返工单关联的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = NcrReworkOrder.class, reverseConvertGenerate = false)
public class NcrReworkOrderBo extends BaseEntity {

    /** 返工记录ID */
    private Long reworkId;

    /** 返工单编号 */
    private String reworkCode;

    /** NCR单ID(查询用) */
    @NotNull(message = "NCR单ID不能为空")
    private Long ncrId;

    /** 原始工单ID(查询用) */
    @NotNull(message = "原始工单ID不能为空")
    private Long originalWoId;

    /** 原始工单编号 */
    private String originalWoNo;

    /** 返工工单ID(查询用) */
    @NotNull(message = "返工工单ID不能为空")
    private Long reworkWoId;

    /** 返工工单编号 */
    private String reworkWoNo;

    /** 返工数量 */
    @NotNull(message = "返工数量不能为空")
    private BigDecimal reworkQty;

    /** 返工原因 */
    private String reworkReason;

    /** 状态(查询用) */
    private String status;

    /** 批量删除ID集合 */
    private Long[] ids;
}
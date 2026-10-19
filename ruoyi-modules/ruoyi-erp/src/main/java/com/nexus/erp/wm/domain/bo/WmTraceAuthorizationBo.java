package com.nexus.erp.wm.domain.bo;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * 来源额度授权请求；保留原单、物料及实际选择，不能以客户端编号代替来源核验。
 */
@Data
public class WmTraceAuthorizationBo {
    @NotNull private Long erpWoId;
    @NotNull private Long mesWoId;

    @NotBlank
    @Size(max = 128)
    private String commandId;

    @NotNull private Long warehouseId;
    private Long locationId;

    @Min(1)
    private long policyVersion;

    private Long materialId;
    private Long requirementDetailId;
    private List<Selection> selections;

    /**
     * 原业务行的实际身份、批次与库存选择。
     *
     * @param sourceStockId 原库存行引用，保留领用或移库时的真实来源。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @param erpBatchId 经来源凭据核实的库存批次身份。
     * @param quantity 实际业务数量，不能以物料清单估算替代实际身份或用量。
     */
    public record Selection(
            Long sourceStockId, Long identityId, Long erpBatchId, BigDecimal quantity) {}
}

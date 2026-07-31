package com.nexus.erp.fin.fa.domain.bo;

import com.nexus.erp.fin.fa.domain.FaDisposal;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 资产处置 BO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@AutoMapper(target = FaDisposal.class)
public class FaDisposalBo {

    /** 处置ID(新增为空) */
    private Long disposalId;

    /** 资产ID */
    @NotNull(message = "资产不能为空")
    private Long assetId;

    /** 类型 SALE-出售 SCRAP-报废 */
    @NotBlank(message = "处置类型不能为空")
    private String disposalType;

    /** 处置日期 */
    @NotNull(message = "处置日期不能为空")
    private Date disposalDate;

    /** 处置收入(售价/残值回收) */
    private BigDecimal disposalPrice;

    /** 备注 */
    private String remark;
}

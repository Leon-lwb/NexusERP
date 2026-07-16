package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqSpareStockLog;

import java.math.BigDecimal;

/**
 * MES备件出入库业务对象(入库/出库)
 * <p>
 * transaction_type: IN(入库,库存+) / OUT(出库,库存-)。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqSpareStockLog.class, reverseConvertGenerate = false)
public class EqSpareStockBo extends BaseEntity {

    /** 备件ID */
    @NotNull(message = "备件ID不能为空")
    private Long spareId;

    /** 备件编码(仅流水查询条件, 出入库提交时忽略) */
    private String spareCode;

    /** 流水类型: IN/OUT */
    @NotNull(message = "流水类型不能为空")
    private String transactionType;

    /** 变动数量(正数) */
    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private BigDecimal quantity;

    /** 关联单据类型(MANUAL) */
    private String refType;

    /** 关联单据ID */
    private Long refId;

    /** 备注 */
    private String remark;
}

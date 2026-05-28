package com.nexus.erp.prd.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdReworkOrder;

import java.math.BigDecimal;

/**
 * 返工订单业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdReworkOrder.class, reverseConvertGenerate = false)
public class PrdReworkOrderBo extends BaseEntity {

    private Long reworkId;
    private String reworkNo;
    private Long sourceWoId;
    private String sourceWoNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomName;
    private BigDecimal reworkQty;
    private String reworkReason;
    private BigDecimal actualQty;
    private BigDecimal scrapQty;
    private String status;
    private String remark;
    private Long[] ids;
}

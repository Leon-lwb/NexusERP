package org.dromara.erp.wm.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import org.dromara.erp.wm.domain.WmStockReconciliation;

import java.math.BigDecimal;

/**
 * 库存台账校验业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockReconciliation.class, reverseConvertGenerate = false)
public class WmStockReconciliationBo extends BaseEntity {

    private Long reconId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomName;
    private BigDecimal bookQty;
    private BigDecimal txBalance;
    private BigDecimal diffQty;
    private String status;
    private String remark;
    private Long[] ids;
}

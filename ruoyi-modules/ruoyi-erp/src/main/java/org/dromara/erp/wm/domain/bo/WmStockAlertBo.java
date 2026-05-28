package org.dromara.erp.wm.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.github.linpeilie.annotations.AutoMapper;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.wm.domain.WmStockAlert;

import java.math.BigDecimal;

/**
 * 安全库存预警业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockAlert.class, reverseConvertGenerate = false)
public class WmStockAlertBo extends BaseEntity {

    private Long alertId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomName;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal currentQty;
    private BigDecimal safetyStock;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private String alertType;
    private String alertLevel;
    private Integer handledFlag;
    private String handledRemark;
    private String remark;
    private Long[] ids;
}

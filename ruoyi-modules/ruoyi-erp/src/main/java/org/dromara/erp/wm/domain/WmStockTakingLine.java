package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_stock_taking_line")
public class WmStockTakingLine extends TenantEntity {

    @TableId(value = "line_id")
    private Long lineId;

    private Long planId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private Long batchId;
    private String batchCode;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private Long stockId;
    private BigDecimal bookQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal differenceQuantity;
    private String status;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableLogic
    private String delFlag;
}

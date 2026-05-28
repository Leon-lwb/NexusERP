package com.nexus.erp.wm.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockTakingLine.class)
public class WmStockTakingLineBo extends BaseEntity {

    @NotNull(message = "盘点行ID不能为空", groups = {EditGroup.class})
    private Long lineId;

    @NotNull(message = "盘点计划ID不能为空", groups = {AddGroup.class, EditGroup.class})
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
}

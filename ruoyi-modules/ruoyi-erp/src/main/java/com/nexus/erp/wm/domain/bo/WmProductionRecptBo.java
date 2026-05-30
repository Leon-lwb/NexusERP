package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmProductionRecpt;

import java.math.BigDecimal;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmProductionRecpt.class)
public class WmProductionRecptBo extends BaseEntity {

    @NotNull(message = "生产入库ID不能为空", groups = {EditGroup.class})
    private Long recptId;

    @NotBlank(message = "入库单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String recptCode;

    private Long woId;
    private String woNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private BigDecimal quantity;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private Date recptDate;
    private String status;
    private String remark;
}

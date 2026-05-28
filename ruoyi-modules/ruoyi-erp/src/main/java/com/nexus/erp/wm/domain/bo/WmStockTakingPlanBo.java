package com.nexus.erp.wm.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockTakingPlan.class)
public class WmStockTakingPlanBo extends BaseEntity {

    @NotNull(message = "盘点计划ID不能为空", groups = {EditGroup.class})
    private Long planId;

    @NotBlank(message = "盘点计划编号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String planCode;

    @NotBlank(message = "盘点计划名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String planName;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Date plannedDate;
    private String status;
    private String remark;

    private List<WmStockTakingLine> wmStockTakingLineList;
}

package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_stock_taking_plan")
public class WmStockTakingPlan extends TenantEntity {

    @TableId(value = "plan_id")
    private Long planId;

    private String planCode;
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

    @TableField(exist = false)
    private List<WmStockTakingLine> wmStockTakingLineList;

    @TableLogic
    private String delFlag;
}

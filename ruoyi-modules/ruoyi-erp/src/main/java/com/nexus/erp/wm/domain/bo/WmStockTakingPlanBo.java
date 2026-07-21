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

/**
 * 盘点计划业务对象 wm_stock_taking_plan
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockTakingPlan.class)
public class WmStockTakingPlanBo extends BaseEntity {

    /**
     * 盘点计划ID
     */
    @NotNull(message = "盘点计划ID不能为空", groups = {EditGroup.class})
    private Long planId;

    /**
     * 计划编号
     */
    @NotBlank(message = "盘点计划编号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String planCode;

    /**
     * 计划名称
     */
    @NotBlank(message = "盘点计划名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String planName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 库区ID
     */
    private Long areaId;

    /**
     * 库区编码
     */
    private String areaCode;

    /**
     * 库区名称
     */
    private String areaName;

    /**
     * 计划日期
     */
    private Date plannedDate;

    /**
     * 状态
     */
    private String status;

    /**
     * 审批流程ID
     */
    private Long selectFlowId;

    /**
     * 流程实例ID
     */
    private String actProcessId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 盘点单行项目列表
     */
    private List<WmStockTakingLine> wmStockTakingLineList;
}

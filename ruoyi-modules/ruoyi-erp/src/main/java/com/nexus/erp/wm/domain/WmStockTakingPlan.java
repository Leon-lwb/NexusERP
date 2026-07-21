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

/**
 * 盘点计划对象 wm_stock_taking_plan
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_stock_taking_plan")
public class WmStockTakingPlan extends TenantEntity {

    /**
     * 盘点计划ID
     */
    @TableId(value = "plan_id")
    private Long planId;

    /**
     * 计划编号
     */
    private String planCode;

    /**
     * 计划名称
     */
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
    @TableField(exist = false)
    private List<WmStockTakingLine> wmStockTakingLineList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

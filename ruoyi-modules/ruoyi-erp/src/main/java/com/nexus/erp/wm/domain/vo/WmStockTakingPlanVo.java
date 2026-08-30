package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson2.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 盘点计划视图对象 wm_stock_taking_plan
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmStockTakingPlan.class)
public class WmStockTakingPlanVo implements Serializable {

    /**
     * 盘点计划ID
     */
    @ExcelProperty("盘点计划ID")
    private Long planId;

    /**
     * 计划编号
     */
    @ExcelProperty("计划编号")
    private String planCode;

    /**
     * 计划名称
     */
    @ExcelProperty("计划名称")
    private String planName;

    /**
     * 仓库ID
     */
    @ExcelProperty("仓库ID")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 库区ID
     */
    @ExcelProperty("库区ID")
    private Long areaId;

    /**
     * 库区编码
     */
    @ExcelProperty("库区编码")
    private String areaCode;

    /**
     * 库区名称
     */
    @ExcelProperty("库区名称")
    private String areaName;

    /**
     * 计划日期
     */
    @ExcelProperty("计划日期")
    private Date plannedDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 盘点模式 OPEN明盘/BLIND盲盘
     */
    @ExcelProperty("盘点模式")
    private String countMode;

    /**
     * 审批流程ID
     */
    @ExcelProperty("审批流程ID")
    private Long selectFlowId;

    /**
     * 流程实例ID
     */
    @ExcelProperty("流程实例ID")
    private String actProcessId;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;

    /**
     * 盘点单行项目列表
     */
    @JSONField(serialize = false)
    private List<WmStockTakingLine> wmStockTakingLineList;
}

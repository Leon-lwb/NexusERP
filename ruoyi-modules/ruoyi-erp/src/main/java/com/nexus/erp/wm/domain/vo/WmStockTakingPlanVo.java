package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@AutoMapper(target = WmStockTakingPlan.class)
public class WmStockTakingPlanVo implements Serializable {

    @ExcelProperty("盘点计划ID")
    private Long planId;

    @ExcelProperty("计划编号")
    private String planCode;

    @ExcelProperty("计划名称")
    private String planName;

    @ExcelProperty("仓库ID")
    private Long warehouseId;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("库区ID")
    private Long areaId;

    @ExcelProperty("库区编码")
    private String areaCode;

    @ExcelProperty("库区名称")
    private String areaName;

    @ExcelProperty("计划日期")
    private Date plannedDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @JSONField(serialize = false)
    private List<WmStockTakingLine> wmStockTakingLineList;
}

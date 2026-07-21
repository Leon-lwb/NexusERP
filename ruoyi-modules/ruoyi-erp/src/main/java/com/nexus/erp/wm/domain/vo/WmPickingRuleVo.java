package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmPickingRule;

import java.io.Serial;
import java.io.Serializable;

/**
 * 拣货规则视图对象 wm_picking_rule
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmPickingRule.class)
public class WmPickingRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @ExcelProperty(value = "规则ID")
    private Long ruleId;

    /**
     * 规则编码
     */
    @ExcelProperty(value = "规则编码")
    private String ruleCode;

    /**
     * 规则名称
     */
    @ExcelProperty(value = "规则名称")
    private String ruleName;

    /**
     * 仓库ID
     */
    @ExcelProperty(value = "仓库ID")
    private Long warehouseId;

    /**
     * 物料分类ID
     */
    @ExcelProperty(value = "物料分类ID")
    private Long materialCategoryId;

    /**
     * 策略类型
     */
    @ExcelProperty(value = "策略类型")
    private String strategyType;

    /**
     * 优先级
     */
    @ExcelProperty(value = "优先级")
    private Integer priority;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    /**
     * 物料分类名称
     */
    @ExcelProperty(value = "物料分类名称")
    private String categoryName;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}

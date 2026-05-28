package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmPickingRule;

import java.io.Serial;
import java.io.Serializable;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmPickingRule.class)
public class WmPickingRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "规则ID")
    private Long ruleId;

    @ExcelProperty(value = "规则编码")
    private String ruleCode;

    @ExcelProperty(value = "规则名称")
    private String ruleName;

    @ExcelProperty(value = "仓库ID")
    private Long warehouseId;

    @ExcelProperty(value = "物料分类ID")
    private Long materialCategoryId;

    @ExcelProperty(value = "策略类型")
    private String strategyType;

    @ExcelProperty(value = "优先级")
    private Integer priority;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;
}

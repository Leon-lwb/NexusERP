package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.erp.wm.domain.WmPutawayRule;

import java.io.Serial;
import java.io.Serializable;

@Data
@ExcelIgnoreUnannotated
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmPutawayRule.class)
public class WmPutawayRuleVo extends WmPutawayRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    @ExcelProperty(value = "物料分类名称")
    private String categoryName;
}

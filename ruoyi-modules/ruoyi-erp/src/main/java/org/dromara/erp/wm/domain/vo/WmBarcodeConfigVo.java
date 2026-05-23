package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmBarcodeConfig;

import java.io.Serial;
import java.io.Serializable;

/**
 * 条码配置视图对象
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmBarcodeConfig.class)
public class WmBarcodeConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long configId;

    @ExcelProperty(value = "规则编码")
    private String configCode;

    @ExcelProperty(value = "规则名称")
    private String configName;

    @ExcelProperty(value = "前缀")
    private String prefix;

    @ExcelProperty(value = "日期格式")
    private String datePattern;

    @ExcelProperty(value = "流水号长度")
    private Integer sequenceLength;

    @ExcelProperty(value = "是否校验位")
    private Long withCheckDigit;

    @ExcelProperty(value = "业务类型")
    private String bizType;

    @ExcelProperty(value = "物料类别")
    private Long categoryId;

    @ExcelProperty(value = "启用状态")
    private Long enableFlag;

    @ExcelProperty(value = "备注")
    private String remark;
}

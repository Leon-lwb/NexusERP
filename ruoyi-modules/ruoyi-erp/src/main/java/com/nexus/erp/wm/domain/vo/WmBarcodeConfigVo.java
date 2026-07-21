package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmBarcodeConfig;

import java.io.Serial;
import java.io.Serializable;

/**
 * 条码配置视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmBarcodeConfig.class)
public class WmBarcodeConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 配置ID
     */
    private Long configId;

    /**
     * 规则编码
     */
    @ExcelProperty(value = "规则编码")
    private String configCode;

    /**
     * 规则名称
     */
    @ExcelProperty(value = "规则名称")
    private String configName;

    /**
     * 前缀
     */
    @ExcelProperty(value = "前缀")
    private String prefix;

    /**
     * 日期格式
     */
    @ExcelProperty(value = "日期格式")
    private String datePattern;

    /**
     * 流水号长度
     */
    @ExcelProperty(value = "流水号长度")
    private Integer sequenceLength;

    /**
     * 是否校验位
     */
    @ExcelProperty(value = "是否校验位")
    private Long withCheckDigit;

    /**
     * 业务类型
     */
    @ExcelProperty(value = "业务类型")
    private String bizType;

    /**
     * 物料类别
     */
    @ExcelProperty(value = "物料类别")
    private Long categoryId;

    /**
     * 启用状态
     */
    @ExcelProperty(value = "启用状态")
    private Long enableFlag;

    /**
     * 当前序列号
     */
    private Long currentSequence;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}

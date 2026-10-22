package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexus.erp.bd.domain.BdSerialNumberRule;

import io.github.linpeilie.annotations.AutoMapper;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产品序列号/条码规则视图对象 bd_serial_number_rule
 *
 * @author nexus
 * @date 2026-05-24
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdSerialNumberRule.class)
public class BdSerialNumberRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @ExcelProperty(value = "规则ID")
    private Long ruleId;

    /** 冻结编号规则的并发版本，已预留号码仍按原快照重放。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long ruleVersion;

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
     * 规则类型
     */
    @ExcelProperty(value = "规则类型")
    private String ruleType;

    /**
     * 前缀
     */
    @ExcelProperty(value = "前缀")
    private String prefix;

    /**
     * 日期格式
     */
    @ExcelProperty(value = "日期格式")
    private String dateFormat;

    /**
     * 流水号长度
     */
    @ExcelProperty(value = "流水号长度")
    private Integer seqLength;

    /**
     * 流水号重置周期
     */
    @ExcelProperty(value = "重置周期")
    private String seqCycle;

    /**
     * 当前流水号
     */
    @ExcelProperty(value = "当前流水号")
    private Integer currentSeq;

    /**
     * 校验位规则
     */
    @ExcelProperty(value = "校验位规则")
    private String checkDigitRule;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 备注
     */
    private String remark;
}

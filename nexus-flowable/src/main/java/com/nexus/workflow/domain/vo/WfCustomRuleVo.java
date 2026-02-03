package com.nexus.workflow.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;



/**
 * 流程自定义业务规则视图对象 wf_custom_rule
 *
 * @author nbacheng
 * @date 2023-11-23
 */
@Data
@ExcelIgnoreUnannotated
public class WfCustomRuleVo {

    private static final long serialVersionUID = 1L;

    /**
     * 业务规则主键
     */
    @ExcelProperty(value = "业务规则主键")
    private Long id;

    /**
     * 流程配置主表ID
     */
    @ExcelProperty(value = "流程配置主表ID")
    private Long configId;

    /**
     * 字段编码
     */
    @ExcelProperty(value = "字段编码")
    private String colCode;

    /**
     * 字段名称
     */
    @ExcelProperty(value = "字段名称")
    private String colName;

    /**
     * java类型
     */
    @ExcelProperty(value = "java类型")
    private String javaType;

    /**
     * java字段名
     */
    @ExcelProperty(value = "java字段名")
    private String javaField;

    /**
     * 属性0-隐藏1-只读默认2-可编辑
     */
    @ExcelProperty(value = "属性0-隐藏1-只读默认2-可编辑")
    private String attribute;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long sort;


}

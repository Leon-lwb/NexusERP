package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProStepCheckRule;

import java.io.Serial;
import java.io.Serializable;

/**
 * MES工序校验规则配置视图对象 pro_step_check_rule
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProStepCheckRule.class)
public class ProStepCheckRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    @ExcelProperty("规则ID")
    private Long ruleId;

    /** 工序ID */
    private Long processId;

    /** 工序编码(冗余,前端选择器带入) */
    @ExcelProperty("工序编码")
    private String processCode;

    /** 工序名称(冗余,前端选择器带入) */
    @ExcelProperty("工序名称")
    private String processName;

    /** 校验类型 */
    @ExcelProperty("校验类型")
    private String checkType;

    /** 校验阶段: ENTRY/EXIT */
    @ExcelProperty("校验阶段")
    private String checkStage;

    /** 是否启用(0停用 1启用) */
    @ExcelProperty("是否启用")
    private String enabled;

    /** 校验失败提示 */
    @ExcelProperty("失败提示")
    private String errorMsg;

    /** 排序 */
    private Integer sortOrder;
}

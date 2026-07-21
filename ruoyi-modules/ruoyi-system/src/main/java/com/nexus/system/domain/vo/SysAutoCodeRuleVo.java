package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysAutoCodeRule;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自动编码规则视图对象 sys_auto_code_rule
 * <p>用于前端展示与传输自动编码规则信息，属于系统管理模块。</p>
 */
@Data
@AutoMapper(target = SysAutoCodeRule.class)
public class SysAutoCodeRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long ruleId;
    /** 规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;
    /** 规则描述 */
    private String ruleDesc;
    /** 编码最大长度 */
    private Integer maxLength;
    /** 是否补位（0否 1是） */
    private String isPadded;
    /** 补位字符 */
    private String paddedChar;
    /** 补位方式 */
    private String paddedMethod;
    /** 启用标志（0否 1是） */
    private String enableFlag;
    /** 备注 */
    private String remark;
    /** 扩展属性1 */
    private String attr1;
    /** 扩展属性2 */
    private String attr2;
    /** 扩展属性3 */
    private Integer attr3;
    /** 扩展属性4 */
    private Integer attr4;
}

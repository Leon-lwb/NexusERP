
package com.nexus.system.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 编码生成规则对象 sys_auto_code_rule vo
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
public class SysAutoCodeRuleVo extends BasePageDtoRequest {

    /**
     * 规则ID
     */
    private Long ruleId;
    /**
     * 规则编码
     */
    private String ruleCode;
    /**
     * 规则名称
     */
    private String ruleName;
    /**
     * 描述
     */
    private String ruleDesc;
    /**
     * 最大长度
     */
    private Long maxLength;
    /**
     * 是否补齐
     */
    private String isPadded;
    /**
     * 补齐字符
     */
    private String paddedChar;
    /**
     * 补齐方式
     */
    private String paddedMethod;
    /**
     * 是否启用
     */
    private String enableFlag;
    /**
     * 备注
     */
    private String remark;
    /**
     * 预留字段1
     */
    private String attr1;
    /**
     * 预留字段2
     */
    private String attr2;
    /**
     * 预留字段3
     */
    private Long attr3;
    /**
     * 预留字段4
     */
    private Long attr4;

}
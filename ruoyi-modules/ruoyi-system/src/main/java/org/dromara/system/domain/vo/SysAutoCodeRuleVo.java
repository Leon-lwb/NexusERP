package org.dromara.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.system.domain.SysAutoCodeRule;

import java.io.Serial;
import java.io.Serializable;

@Data
@AutoMapper(target = SysAutoCodeRule.class)
public class SysAutoCodeRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    private Long ruleId;
    private String ruleCode;
    private String ruleName;
    private String ruleDesc;
    private Integer maxLength;
    private String isPadded;
    private String paddedChar;
    private String paddedMethod;
    private String enableFlag;
    private String remark;
    private String attr1;
    private String attr2;
    private Integer attr3;
    private Integer attr4;
}

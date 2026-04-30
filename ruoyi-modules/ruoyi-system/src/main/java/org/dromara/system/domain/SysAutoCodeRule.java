package org.dromara.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auto_code_rule")
public class SysAutoCodeRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId
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

    private String delFlag;

    private String tenantId;
}

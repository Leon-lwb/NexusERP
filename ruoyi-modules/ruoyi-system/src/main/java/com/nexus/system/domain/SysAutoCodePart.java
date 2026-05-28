package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auto_code_part")
public class SysAutoCodePart extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long partId;

    private Long ruleId;

    private Integer partIndex;

    private String partType;

    private String partCode;

    private String partName;

    private Integer partLength;

    private String dateFormat;

    private String inputCharacter;

    private String fixCharacter;

    private Integer seriaStartNo;

    private Integer seriaStep;

    private Integer seriaNowNo;

    private String cycleFlag;

    private String cycleMethod;

    private String remark;

    private String attr1;

    private String attr2;

    private Integer attr3;

    private Integer attr4;

    private String delFlag;

    private String tenantId;
}

package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auto_code_result")
public class SysAutoCodeResult extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long codeId;

    private Long ruleId;

    private String genDate;

    private Integer genIndex;

    private String lastResult;

    private Integer lastSerialNo;

    private String lastInputChar;

    private String remark;

    private String attr1;

    private String attr2;

    private Integer attr3;

    private Integer attr4;

    private String delFlag;

    private String tenantId;
}

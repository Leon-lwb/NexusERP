package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自动编码规则对象 sys_auto_code_rule
 * <p>定义单据/业务编号的自动生成规则，包含编码最大长度、补位策略与启停标志，属于系统管理模块。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auto_code_rule")
public class SysAutoCodeRule extends BaseEntity {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    @TableId
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

    /** 删除标志（0代表存在 1代表删除） */
    private String delFlag;

    /** 租户ID */
    private String tenantId;
}

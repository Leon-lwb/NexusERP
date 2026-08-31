package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自动编码生成结果对象 sys_auto_code_result
 * <p>记录某条编码规则每次生成的结果与流水号游标，供下次生成时续号，属于系统管理模块。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auto_code_result")
public class SysAutoCodeResult extends BaseEntity {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 编码结果ID */
    @TableId
    private Long codeId;

    /** 所属规则ID */
    private Long ruleId;

    /** 生成日期 */
    private String genDate;

    /** 生成序号 */
    private Integer genIndex;

    /** 上一次生成结果 */
    private String lastResult;

    /** 上一次流水号 */
    private Integer lastSerialNo;

    /** 上一次传入字符 */
    private String lastInputChar;

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

package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysAutoCodeResult;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自动编码生成结果视图对象 sys_auto_code_result
 * <p>用于前端展示与传输编码生成结果及流水号游标信息，属于系统管理模块。</p>
 */
@Data
@AutoMapper(target = SysAutoCodeResult.class)
public class SysAutoCodeResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 编码结果ID */
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
}

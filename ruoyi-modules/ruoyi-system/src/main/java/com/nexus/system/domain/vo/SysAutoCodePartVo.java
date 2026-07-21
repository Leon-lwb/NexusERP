package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysAutoCodePart;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自动编码组成部分视图对象 sys_auto_code_part
 * <p>用于前端展示与传输编码规则下各组成单元的信息，属于系统管理模块。</p>
 */
@Data
@AutoMapper(target = SysAutoCodePart.class)
public class SysAutoCodePartVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 组成部分ID */
    private Long partId;
    /** 所属规则ID */
    private Long ruleId;
    /** 组成部分序号 */
    private Integer partIndex;
    /** 组成部分类型 */
    private String partType;
    /** 组成部分编码 */
    private String partCode;
    /** 组成部分名称 */
    private String partName;
    /** 组成部分长度 */
    private Integer partLength;
    /** 日期格式化模板 */
    private String dateFormat;
    /** 传入字符 */
    private String inputCharacter;
    /** 固定字符 */
    private String fixCharacter;
    /** 流水号起始值 */
    private Integer seriaStartNo;
    /** 流水号步长 */
    private Integer seriaStep;
    /** 流水号当前值 */
    private Integer seriaNowNo;
    /** 是否重置标志（0否 1是） */
    private String cycleFlag;
    /** 重置周期方式 */
    private String cycleMethod;
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

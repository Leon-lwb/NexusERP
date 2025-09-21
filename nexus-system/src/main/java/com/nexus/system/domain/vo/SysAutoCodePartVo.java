
package com.nexus.system.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 编码规则对象 sys_auto_code_part vo
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
public class SysAutoCodePartVo extends BasePageDtoRequest {

    /**
     * 分段ID
     */
    private Long partId;
    /**
     * 规则ID
     */
    private Long ruleId;
    /**
     * 分段序号
     */
    private Long partIndex;
    /**
     * 分段类型，INPUTCHAR：输入字符，NOWDATE：当前日期时间，FIXCHAR：固定字符，SERIALNO：流水号
     */
    private String partType;
    /**
     * 分段编号
     */
    private String partCode;
    /**
     * 分段名称
     */
    private String partName;
    /**
     * 分段长度
     */
    private Long partLength;
    /**
     * ${column.columnComment}
     */
    private String dateFormat;
    /**
     * 输入字符
     */
    private String inputCharacter;
    /**
     * 固定字符
     */
    private String fixCharacter;
    /**
     * 流水号起始值
     */
    private Long seriaStartNo;
    /**
     * 流水号步长
     */
    private Long seriaStep;
    /**
     * 流水号当前值
     */
    private Long seriaNowNo;
    /**
     * 流水号是否循环
     */
    private String cycleFlag;
    /**
     * 循环方式，YEAR：按年，MONTH：按月，DAY：按天，HOUR：按小时，MINITE：按分钟，OTHER：按传入字符变
     */
    private String cycleMethod;
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
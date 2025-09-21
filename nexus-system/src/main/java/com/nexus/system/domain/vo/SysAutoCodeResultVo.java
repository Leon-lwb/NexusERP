
package com.nexus.system.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 编码生成记录对象 sys_auto_code_result vo
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
public class SysAutoCodeResultVo extends BasePageDtoRequest {

    /**
     * 记录ID
     */
    private Long codeId;
    /**
     * 规则ID
     */
    private Long ruleId;
    /**
     * 生成日期时间
     */
    private String genDate;
    /**
     * 最后产生的序号
     */
    private Long genIndex;
    /**
     * 最后产生的值
     */
    private String lastResult;
    /**
     * 最后产生的流水号
     */
    private Long lastSerialNo;
    /**
     * 最后传入的参数
     */
    private String lastInputChar;
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
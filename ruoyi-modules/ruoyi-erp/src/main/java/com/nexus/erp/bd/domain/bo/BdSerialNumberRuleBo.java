package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdSerialNumberRule;

/**
 * 产品序列号/条码规则业务对象 bd_serial_number_rule
 *
 * @author nexus
 * @date 2026-05-24
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdSerialNumberRule.class, reverseConvertGenerate = false)
public class BdSerialNumberRuleBo extends BaseEntity {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 冻结编号规则的并发版本，已预留号码仍按原快照重放。
     */
    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private Long ruleVersion;

    /**
     * 规则编码
     */
    @NotBlank(message = "规则编码不能为空")
    private String ruleCode;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    /**
     * 规则类型
     */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /**
     * 前缀
     */
    private String prefix;

    /**
     * 日期格式
     */
    private String dateFormat;

    /**
     * 流水号长度
     */
    private Integer seqLength;

    /**
     * 流水号重置周期
     */
    private String seqCycle;

    /**
     * 当前流水号
     */
    private Integer currentSeq;

    /**
     * 校验位规则
     */
    private String checkDigitRule;

    /**
     * 状态
     */
    private String status;

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

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}

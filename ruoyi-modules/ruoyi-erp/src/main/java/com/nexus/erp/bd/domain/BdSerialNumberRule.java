package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * 产品序列号/条码/批次号规则对象 bd_serial_number_rule
 * <p>
 * 与 {@code SysAutoCodeUtil}（sys_auto_code_rule 表）的分工：
 * <ul>
 *   <li>{@code sys_auto_code_rule} — 单据编码（采购单号/工单号/收货单号等），灵活的组合式架构</li>
 *   <li>{@code bd_serial_number_rule} — 产品序列号/条码/批次号，带校验位（MOD10/MOD11）</li>
 * </ul>
 * 两者互补：单据号走系统级编码引擎，产品级唯一标识走本表。
 *
 * @author nexus
 * @date 2026-05-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_serial_number_rule")
public class BdSerialNumberRule extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 规则ID */
    @TableId(value = "rule_id")
    private Long ruleId;

    /** 冻结编号规则的并发版本，已预留号码仍按原快照重放。 */
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private Long ruleVersion;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型: PRODUCT_SN(产品序列号) / BARCODE(条码) / BATCH_NO(批次号) */
    private String ruleType;

    /** 前缀(固定字符，如 SN/M/BN) */
    private String prefix;

    /** 日期格式(如yyyyMMdd，空=无日期段) */
    private String dateFormat;

    /** 流水号长度(补零位数) */
    private Integer seqLength;

    /** 流水号重置周期(DAY/MONTH/YEAR/NONE) */
    private String seqCycle;

    /** 当前流水号 */
    private Integer currentSeq;

    /** 校验位规则: NONE / MOD10 / MOD11 */
    private String checkDigitRule;

    /** 状态(ACTIVE/INACTIVE) */
    private String status;

    /** 预留字段1 */
    private String attr1;

    /** 预留字段2 */
    private String attr2;

    /** 预留字段3 */
    private Long attr3;

    /** 预留字段4 */
    private Long attr4;

    /** 删除标志 */
    @TableLogic
    private String delFlag;

    /** 备注 */
    private String remark;
}

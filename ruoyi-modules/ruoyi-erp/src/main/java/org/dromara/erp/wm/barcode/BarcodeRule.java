package org.dromara.erp.wm.barcode;

import lombok.Data;

/**
 * 条码规则配置的轻量领域对象
 *
 * 说明：
 * - 这是 P1-2 条码能力的开发起点，用于先固化规则生成/解析能力
 * - 后续可替换为数据库实体 wm_barcode_config
 */
@Data
public class BarcodeRule {

    /**
     * 规则编码
     */
    private String ruleCode;

    /**
     * 条码前缀
     */
    private String prefix;

    /**
     * 日期格式，例如 yyyyMMdd
     */
    private String datePattern = "yyyyMMdd";

    /**
     * 流水号长度
     */
    private int sequenceLength = 6;

    /**
     * 是否包含校验位
     */
    private boolean withCheckDigit;
}

package com.nexus.erp.wm.barcode;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 条码生成器
 *
 * <p>支持前缀 + 日期 + 流水号的条码格式。 流水号通过外部序列提供者获取，支持数据库序列、Redis等分布式方案。
 */
@Component
public class BarcodeGenerator {

    /**
     * 根据条码规则和序列号生成条码
     *
     * @param rule     条码规则配置
     * @param bizDate  业务日期
     * @param sequence 序列号（由调用方提供，确保分布式唯一性）
     * @return 生成的条码字符串
     */
    public String generate(BarcodeRule rule, Date bizDate, long sequence) {
        if (rule == null) {
            throw new IllegalArgumentException("条码规则不能为空");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("序列号必须大于0");
        }

        StringBuilder code = new StringBuilder();
        if (rule.getPrefix() != null) {
            code.append(rule.getPrefix());
        }

        String datePattern = rule.getDatePattern() == null ? "yyyyMMdd" : rule.getDatePattern();
        int seqLen = rule.getSequenceLength() <= 0 ? 6 : rule.getSequenceLength();
        code.append((bizDate == null ? new Date() : bizDate).toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofPattern(datePattern)));
        code.append(String.format("%0" + seqLen + "d", sequence));

        if (rule.isWithCheckDigit()) {
            code.append(calculateCheckDigit(code.toString()));
        }
        return code.toString();
    }

    /**
     * 校验位计算（加权和取模）
     */
    private int calculateCheckDigit(String value) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += value.charAt(i) * weight;
        }
        return sum % 10;
    }
}

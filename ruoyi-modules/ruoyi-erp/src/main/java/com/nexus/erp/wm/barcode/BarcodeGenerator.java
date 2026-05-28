package com.nexus.erp.wm.barcode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 条码生成器
 *
 * 当前版本先提供轻量实现：
 * - 支持前缀 + 日期 + 流水号
 * - 后续可扩展为数据库持久化流水与校验位
 */
public class BarcodeGenerator {

    private final AtomicLong sequence = new AtomicLong(0L);

    public String generate(BarcodeRule rule, Date bizDate) {
        if (rule == null) {
            throw new IllegalArgumentException("条码规则不能为空");
        }
        StringBuilder code = new StringBuilder();
        if (rule.getPrefix() != null) {
            code.append(rule.getPrefix());
        }
        String datePattern = rule.getDatePattern() == null ? "yyyyMMdd" : rule.getDatePattern();
        code.append((bizDate == null ? new Date() : bizDate).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern(datePattern)));
        code.append(String.format("%0" + rule.getSequenceLength() + "d", sequence.incrementAndGet()));
        if (rule.isWithCheckDigit()) {
            code.append(calculateCheckDigit(code.toString()));
        }
        return code.toString();
    }

    /**
     * 简单校验位计算
     *
     * 说明：
     * - 这里先使用字符和取模的轻量方式，便于快速联调
     * - 后续如果需要工业级条码校验，可替换为更严格的校验算法
     */
    private int calculateCheckDigit(String value) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            sum += value.charAt(i);
        }
        return sum % 10;
    }
}

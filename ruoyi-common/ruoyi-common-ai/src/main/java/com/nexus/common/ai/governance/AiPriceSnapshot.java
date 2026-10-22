package com.nexus.common.ai.governance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 不含端点和密钥的不可变计价快照，金额单位为每百万Token。 */
public record AiPriceSnapshot(
        Long modelId,
        String modelName,
        String protocol,
        String currency,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        Integer contextWindow,
        Integer outputLimit) {
    public AiPriceSnapshot {
        if (modelId == null) throw new IllegalArgumentException("模型身份缺失");
        if (currency != null && !currency.matches("[A-Z]{3}")) currency = null;
        try {
            if (inputPrice != null) inputPrice = inputPrice.setScale(12, RoundingMode.UNNECESSARY);
            if (outputPrice != null)
                outputPrice = outputPrice.setScale(12, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("模型价格精度超过12位小数");
        }
        if ((inputPrice != null && (inputPrice.signum() < 0 || inputPrice.precision() > 28))
                || (outputPrice != null
                        && (outputPrice.signum() < 0 || outputPrice.precision() > 28)))
            throw new IllegalArgumentException("模型价格不合法");
    }

    public boolean priced() {
        return currency != null
                && currency.matches("[A-Z]{3}")
                && inputPrice != null
                && outputPrice != null
                && inputPrice.signum() >= 0
                && outputPrice.signum() >= 0;
    }
}

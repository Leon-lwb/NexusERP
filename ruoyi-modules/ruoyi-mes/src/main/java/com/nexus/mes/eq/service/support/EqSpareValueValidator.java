package com.nexus.mes.eq.service.support;

import com.nexus.common.core.exception.ServiceException;

import java.math.BigDecimal;

/**
 * 备件数量与参考成本的统一值域校验。
 *
 * <p>校验与现有数量 {@code decimal(14,4)}、成本 {@code decimal(18,4)} 保持一致，
 * 防止接口外的维修、退库等内部调用因数据库舍入产生零数量流水或数量漂移。</p>
 *
 * @author Nexus
 */
public final class EqSpareValueValidator {

    /** 数量字段的最大可表示值。 */
    public static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999999.9999");
    private static final BigDecimal MAX_COST = new BigDecimal("99999999999999.9999");

    private EqSpareValueValidator() {
    }

    /**
     * 校验必填的正数量。
     *
     * @param value 数量
     * @param label 中文业务字段名
     */
    public static void requirePositiveQuantity(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new ServiceException(label + "必须大于0");
        }
        validate(value, label, MAX_QUANTITY);
    }

    /**
     * 校验选填的非负数量，例如安全库存。
     *
     * @param value 数量；为空时由业务层取默认值
     * @param label 中文业务字段名
     */
    public static void validateOptionalQuantity(BigDecimal value, String label) {
        validate(value, label, MAX_QUANTITY);
    }

    /**
     * 校验选填的非负参考成本。
     *
     * @param value 参考单价；允许为空
     */
    public static void validateOptionalCost(BigDecimal value) {
        validate(value, "备件参考单价", MAX_COST);
    }

    /**
     * 拒绝负数、有效小数超出四位或超出库字段范围的值。
     *
     * @param value 待校验值
     * @param label 中文字段名
     * @param maximum 最大允许值
     */
    private static void validate(BigDecimal value, String label, BigDecimal maximum) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0) {
            throw new ServiceException(label + "不能为负数");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new ServiceException(label + "最多保留4位小数");
        }
        if (value.compareTo(maximum) > 0) {
            throw new ServiceException(label + "超出允许范围");
        }
    }
}

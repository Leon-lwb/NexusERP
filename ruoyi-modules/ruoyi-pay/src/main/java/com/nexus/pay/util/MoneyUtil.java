package com.nexus.pay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 支付金额单位换算工具
 * <p>pay_order 自 decimal 迁移后,数据库统一存"元"(decimal(18,2))。
 * 各支付平台 API 金额单位不同:支付宝要"元",微信/收钱吧要"分"(整数)。
 * 本工具集中处理元↔分换算,避免三处 SDK 各自手算出错(单位错一处即真金白银 bug)。</p>
 *
 * <p><b>空值策略</b>:{@link #yuanToFen(BigDecimal)} 对 null 抛 NPE(支付场景宁可失败也不静默按 0 元扣款),
 * 与迁移前 {@code Integer.parseInt(null)} 的失败语义一致。</p>
 *
 * @author nexus
 * @since 2026-07-16
 */
public final class MoneyUtil {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyUtil() {
    }

    /**
     * 元 -> 分(四舍五入取整)
     *
     * @param yuan 金额(元),不可为 null
     * @return 金额(分)
     */
    public static long yuanToFen(BigDecimal yuan) {
        return yuan.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 分 -> 元(保留2位小数)
     *
     * @param fen 金额(分)
     * @return 金额(元)
     */
    public static BigDecimal fenToYuan(long fen) {
        return BigDecimal.valueOf(fen).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}

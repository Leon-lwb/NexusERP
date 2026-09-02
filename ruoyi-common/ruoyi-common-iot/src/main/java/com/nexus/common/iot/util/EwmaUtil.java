package com.nexus.common.iot.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * EWMA（指数加权移动平均）统计工具
 * <p>
 * 用于 IoT 时序数据的异常检测：维护均值与标准差的指数加权估计，
 * 新点 z = |x − mean| / s 超过阈值即判定异常。
 * 标准差采用 EWMA 方差的平方根（EWMA 控制图标准做法）：
 * <pre>
 *   mean' = λ·x + (1−λ)·mean
 *   var'  = (1−λ)·(var + λ·(x − mean)²)
 * </pre>
 * 纯静态工具，无状态；状态由调用方持有（如 Redis）。
 * <p>
 * 唯一实现，两处消费方共用（在线/离线口径一致）：
 * <ul>
 *   <li>ruoyi-iot {@code IotAnomalyDetector}：实时管道在线检测（Redis 滚动状态）；</li>
 *   <li>ruoyi-mes {@code EqHealthServiceImpl}：设备健康评分 24h 时序离线重放。</li>
 * </ul>
 * 默认参数（λ/预热/z 阈值）统一定义在本类，iot 侧配置键为
 * {@code nexus.iot.pipeline.anomaly.*}，MES 离线重放读取同一组键以保持口径一致。
 *
 * @author nexus
 */
public final class EwmaUtil {

    /**
     * 默认平滑系数 λ
     */
    public static final double DEFAULT_LAMBDA = 0.3;

    /**
     * 默认预热样本数(达到后才开始判定, 避免开局误报)
     */
    public static final int DEFAULT_WARMUP = 20;

    /**
     * 默认告警 z-score 阈值(MEDIUM)
     */
    public static final double DEFAULT_Z_WARN = 3.0;

    /**
     * 默认高级告警 z-score 阈值(HIGH)
     */
    public static final double DEFAULT_Z_HIGH = 4.0;

    private EwmaUtil() {
    }

    /**
     * EWMA 状态（均值与方差估计）
     */
    public record EwmaState(BigDecimal mean, BigDecimal variance, long count) {

        /**
         * 标准差(方差为负时按 0 处理, 至少返回极小值避免除零)
         */
        public BigDecimal std() {
            if (variance == null || variance.compareTo(BigDecimal.ZERO) <= 0) {
                return new BigDecimal("1e-9");
            }
            return variance.sqrt(MathContext.DECIMAL64);
        }
    }

    /**
     * 用首点初始化状态
     *
     * @param x 首个观测值
     * @return 初始状态(方差 0)
     */
    public static EwmaState init(BigDecimal x) {
        return new EwmaState(x, BigDecimal.ZERO, 1);
    }

    /**
     * EWMA 更新（不改变入参，返回新状态）
     *
     * @param state 当前状态(可为 null, null 时等价 init)
     * @param x     新观测值
     * @param lambda 平滑系数 λ∈(0,1]
     * @return 更新后的状态
     */
    public static EwmaState update(EwmaState state, BigDecimal x, double lambda) {
        if (state == null) {
            return init(x);
        }
        BigDecimal lam = BigDecimal.valueOf(lambda);
        BigDecimal oneMinus = BigDecimal.ONE.subtract(lam);
        BigDecimal oldMean = state.mean();
        BigDecimal newMean = lam.multiply(x, MathContext.DECIMAL64)
            .add(oneMinus.multiply(oldMean, MathContext.DECIMAL64));
        BigDecimal diff = x.subtract(oldMean);
        BigDecimal newVar = oneMinus.multiply(
            state.variance().add(lam.multiply(diff.multiply(diff, MathContext.DECIMAL64), MathContext.DECIMAL64), MathContext.DECIMAL64),
            MathContext.DECIMAL64);
        return new EwmaState(newMean, newVar, state.count() + 1);
    }

    /**
     * 计算新点的 z-score（基于更新前的状态， 即"该点偏离既有正常区间多少个标准差"）
     *
     * @param state 更新前状态
     * @param x     新观测值
     * @return z-score；状态不足(未初始化)返回 0
     */
    public static BigDecimal zScore(EwmaState state, BigDecimal x) {
        if (state == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = x.subtract(state.mean()).abs();
        return diff.divide(state.std(), 4, RoundingMode.HALF_UP);
    }

    /**
     * 判断是否预热完成（样本数达到 warmup 才开始判定，避免开局误报）
     *
     * @param state   当前状态
     * @param warmup  预热样本数
     * @return 是否可判定
     */
    public static boolean warmedUp(EwmaState state, int warmup) {
        return state != null && state.count() >= warmup;
    }
}

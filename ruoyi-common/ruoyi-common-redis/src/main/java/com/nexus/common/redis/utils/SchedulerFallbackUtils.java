package com.nexus.common.redis.utils;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.nexus.common.core.utils.SpringUtils;

/**
 * 定时任务本地兜底执行工具（SnailJob 客户端未启用时的 {@code @Scheduled} 兜底）。
 *
 * <p><b>背景</b>：生产环境 {@code application-prod.yml} 中 {@code snail-job.enabled: false}，
 * SnailJob 客户端不启动，于是所有仅以 {@code @JobExecutor} 声明的任务
 * （IoT 心跳/数据清理/Stream 裁剪、MES 保养到期/设备状态联动/健康度/OEE、ERP 库存预警等）
 * <b>没有任何调度器会回调它们</b>，任务静默不执行。</p>
 *
 * <p><b>兜底策略</b>（用户已确认，不修改生产 {@code snail-job.enabled}）：任务类上再补一个
 * {@code @Scheduled} 薄壳方法，由本工具串起三步：</p>
 * <ol>
 *   <li><b>开关判断</b>：{@code snail-job.enabled=true} 时直接返回，保证不会与 SnailJob 双跑；</li>
 *   <li><b>Lock4j 分布式锁</b>：按任务名加锁，多实例部署下同一时刻只有一个节点执行；</li>
 *   <li><b>执行既有执行体</b>：只加兜底入口，不改动 {@code jobExecute} 语义。</li>
 * </ol>
 *
 * <p><b>为什么用 {@link LockTemplate} 而不是 {@code @Lock4j} 注解</b>：注解由 AOP 织入，加锁发生在
 * 方法体之前，无法满足“先判开关、再取锁”的顺序；且 lock4j 默认
 * {@code LockFailureStrategy} 在抢锁失败时抛 {@code LockException}，对每分钟级任务会造成
 * ERROR 日志噪声。{@link LockTemplate#lock} 抢锁失败返回 {@code null}，可静默跳过。</p>
 *
 * <p><b>测试</b>：{@code ruoyi-modules/ruoyi-iot/src/test/.../IotSchedulerFallbackTest}。</p>
 *
 * @author nexus
 */
public final class SchedulerFallbackUtils {

    /** 分布式锁 key 前缀，避免与业务锁冲突。 */
    private static final String LOCK_KEY_PREFIX = "scheduler:fallback:";

    /** 抢锁等待时间（毫秒）：1ms ≈ 非阻塞，抢不到立即放弃（由持有锁的实例执行本次任务）。 */
    private static final long LOCK_ACQUIRE_TIMEOUT_MS = 1L;

    private SchedulerFallbackUtils() {
    }

    /**
     * 判断本地 {@code @Scheduled} 兜底是否应当执行。
     *
     * @param snailJobEnabled {@code snail-job.enabled} 配置值
     * @return true 表示 SnailJob 客户端未启用，应由本地兜底执行
     */
    public static boolean localFallbackEnabled(boolean snailJobEnabled) {
        return !snailJobEnabled;
    }

    /**
     * 开关判断 → Lock4j 分布式锁 → 执行体。
     *
     * @param snailJobEnabled {@code snail-job.enabled} 配置值
     * @param taskName        任务名（与 SnailJob {@code @JobExecutor(name=...)} / {@code sj_job.executor_info} 一致）
     * @param lockExpireMs    锁过期时间（毫秒），应大于单次任务最长耗时
     * @param body            既有执行体（通常是 {@code () -> jobExecute(null)}）
     * @return true 表示本次真正执行了执行体；false 表示因开关开启或抢锁失败而跳过
     */
    public static boolean runIfFallbackEnabled(boolean snailJobEnabled, String taskName,
                                               long lockExpireMs, Runnable body) {
        if (!localFallbackEnabled(snailJobEnabled)) {
            return false;
        }
        LockTemplate lockTemplate = SpringUtils.getBean(LockTemplate.class);
        LockInfo lockInfo = lockTemplate.lock(LOCK_KEY_PREFIX + taskName, lockExpireMs, LOCK_ACQUIRE_TIMEOUT_MS);
        if (lockInfo == null) {
            // 其他实例正在执行同一任务，本次跳过（不抛异常、不打 ERROR 日志）
            return false;
        }
        try {
            body.run();
            return true;
        } finally {
            lockTemplate.releaseLock(lockInfo);
        }
    }
}

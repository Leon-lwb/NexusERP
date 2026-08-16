package com.nexus.erp.wm.service.impl;

import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 按日自增序号提供器（批次号/序列号共用）。
 *
 * <p>修复旧实现 generateBatchCode()/batchGenerate() 的初始化竞态：
 * 旧代码先 setObjectIfAbsent(key, 0) 再 setCacheObject(key, base)，两步之间若有并发线程已 incr，
 * 后执行的覆盖会把序号打回历史最大值，导致新序号与库内批次号/序列号重复
 * （uk_batch_code / uk_sn_code 唯一键冲突，sys_oper_log 曾有 Duplicate entry 'B202607060001' 实据）。</p>
 *
 * <p>本提供器把"读缓存 → 初始化(查库最大序号) → 原子自增"放进同一把 Redisson 分布式锁，
 * 双机集群跨实例安全；锁粒度按 seqKey（含日期）隔离，锁内仅做 Redis 读写，不持有数据库事务。</p>
 *
 * <p>依赖注入 {@link RedissonClient}（Spring Bean），不触碰 RedisUtils 静态类，便于纯 Mockito 单测。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DailyCodeSequenceProvider {

    private static final Duration SEQ_TTL = Duration.ofHours(48);

    private final RedissonClient redissonClient;

    /**
     * 取下一个序号。
     *
     * @param seqKey       含日期的 Redis 序号键，如 erp:batch:seq:20260816
     * @param maxSeqLoader 当天键不存在时，从库中读取当日最大序号（作为自增起点）
     * @return 下一个序号（= max + 1）
     */
    public long next(String seqKey, LongSupplier maxSeqLoader) {
        RLock lock = redissonClient.getLock("erp:code:seq:lock:" + seqKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("序号生成繁忙，请稍后重试");
            }
            RBucket<Long> bucket = redissonClient.getBucket(seqKey);
            Long cached = bucket.get();
            if (cached == null) {
                bucket.set(maxSeqLoader.getAsLong(), SEQ_TTL);
            }
            RAtomicLong atomic = redissonClient.getAtomicLong(seqKey);
            return atomic.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("序号生成被中断，请重试");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
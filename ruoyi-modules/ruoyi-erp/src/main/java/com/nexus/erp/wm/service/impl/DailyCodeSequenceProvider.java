package com.nexus.erp.wm.service.impl;

import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
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
     * @throws ServiceException 序号起点无效、已无法递增或原子自增结果未前进时抛出
     */
    public long next(String seqKey, LongSupplier maxSeqLoader) {
        RLock lock = redissonClient.getLock("erp:code:seq:lock:" + seqKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("序号生成繁忙，请稍后重试");
            }
            // Object 型 JSON codec 会将小整数解码为 Integer，泛型 Long 不能约束实际返回类型。
            RBucket<Object> bucket = redissonClient.getBucket(seqKey);
            Object cached = bucket.get();
            long current;
            if (cached == null) {
                current = parseSequence(maxSeqLoader.getAsLong());
                bucket.set(current, SEQ_TTL);
            } else {
                current = parseSequence(cached);
            }
            RAtomicLong atomic = redissonClient.getAtomicLong(seqKey);
            long next = atomic.incrementAndGet();
            if (next <= current) {
                throw new ServiceException("序号自增结果异常，请核实后重试");
            }
            return next;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("序号生成被中断，请重试");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * 无损读取可递增的非负整数起点，不对小数、字符串或未知数值类型进行猜测转换。
     *
     * <p>原子自增要求整数缓存；仅接受整数包装类型和精确处于 long 范围内的 BigInteger。
     * Long.MAX_VALUE 已无下一个 long 序号，必须在写缓存或自增之前拒绝。</p>
     *
     * @param value 缓存值或历史最大序号
     * @return 可安全递增的整数起点
     * @throws ServiceException 类型不支持、负值或超出可递增范围时抛出
     */
    private long parseSequence(Object value) {
        long sequence;
        if (value instanceof BigInteger integer) {
            try {
                sequence = integer.longValueExact();
            } catch (ArithmeticException e) {
                throw new ServiceException("序号起点超出可递增范围，请核实后重试");
            }
        } else if (value instanceof Byte || value instanceof Short
            || value instanceof Integer || value instanceof Long) {
            // 这些整数包装类型转为 long 不会丢失精度；其他 Number 不使用 longValue 截断。
            sequence = ((Number) value).longValue();
        } else {
            throw new ServiceException("序号起点不是有效整数，请核实后重试");
        }
        if (sequence < 0 || sequence == Long.MAX_VALUE) {
            throw new ServiceException("序号起点超出可递增范围，请核实后重试");
        }
        return sequence;
    }
}

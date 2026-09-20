package com.nexus.erp.wm.service.mq;

import com.nexus.common.redis.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MES 备件过账消费幂等闸门（MES-EQ-04）
 * <p>
 * 用 Redis 原子占位（{@code setObjectIfAbsent}）实现"同一业务幂等键只过账一次"，
 * 独立成 Bean 以便单测注入 mock（项目规范禁止对 {@code RedisUtils} 做静态打桩）。
 * <p>
 * 与持久层查重（{@code wm_material_stock_tx} 按 {@code source_doc_type='MES_SPARE'} +
 * {@code source_doc_code=bizKey}）构成双重幂等：Redis 挡住重投，DB 查重挡住 Redis 过期后的迟到重投。
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmSpareStockDedupGuard {

    /** 幂等键前缀 */
    private static final String KEY_PREFIX = "mes:spare:erp:posted:";
    /** 幂等窗口：7 天（远大于 MQ 重投与人工补投窗口） */
    private static final Duration TTL = Duration.ofDays(7);

    /**
     * 尝试占位（原子）
     *
     * @param bizKey 业务幂等键
     * @return true = 本次为首次过账；false = 重复消息，应跳过
     */
    public boolean tryMark(String bizKey) {
        return RedisUtils.setObjectIfAbsent(KEY_PREFIX + bizKey, "1", TTL);
    }

    /**
     * 释放占位（过账事务回滚时调用，避免"标记成功但实际未过账"导致消息丢失）
     *
     * @param bizKey 业务幂等键
     */
    public void release(String bizKey) {
        try {
            RedisUtils.deleteObject(KEY_PREFIX + bizKey);
        } catch (Exception e) {
            log.warn("释放备件过账幂等占位失败: bizKey={}, error={}", bizKey, e.getMessage());
        }
    }
}

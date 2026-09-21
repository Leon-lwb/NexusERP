package com.nexus.mes.eq.service.support;

import com.nexus.common.redis.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/**
 * 备件退库单号生成器（MES-EQ-04 收口）
 * <p>
 * 格式 {@code MESRT + yyyyMMdd + 4位当日序号}，序号用 Redis 原子自增
 * （键 {@code mes:spare:return:no:<yyyyMMdd>}，2 天过期）保证多实例下不重号
 * （与 {@code MesSpareErpConsumer.nextRequisitionNo} 同一写法）。
 * <p>
 * 独立成 Bean：项目规范禁止对 {@code RedisUtils} 做静态打桩，抽成可注入组件后
 * Service 单测可注入 mock 而不触碰 Redis。
 *
 * @author Nexus
 */
@Slf4j
@Component
public class EqSpareReturnNoGenerator {

    /** 单号序号 Redis 键前缀 */
    private static final String KEY_PREFIX = "mes:spare:return:no:";
    /** 单号前缀 */
    private static final String NO_PREFIX = "MESRT";
    /** 键过期时间：2 天（跨日不重号即可，无需长期保留） */
    private static final Duration TTL = Duration.ofDays(2);

    /**
     * 生成下一个退库单号
     *
     * @return 退库单号，形如 {@code MESRT202609200001}
     */
    public String nextReturnNo() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = KEY_PREFIX + day;
        long seq = RedisUtils.incrAtomicValue(key);
        RedisUtils.expire(key, TTL);
        String no = NO_PREFIX + day + String.format("%04d", seq % 10000);
        log.debug("生成备件退库单号: {} (seq={})", no, seq);
        return no;
    }
}

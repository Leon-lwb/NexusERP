package com.nexus.erp.wm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PDA 接口幂等标记
 * <p>
 * 标注的接口按请求头 {@code X-Idempotent-Key} 幂等去重(键格式 单号:行号:动作):
 * <ul>
 *   <li>首次请求正常执行, 键写入 Redis (TTL 24h)</li>
 *   <li>重复请求(离线队列自动重放等)直接返回成功 + replayed 标记, 不重复执行业务</li>
 *   <li>未携带幂等键的请求不做去重, 直接执行</li>
 * </ul>
 * 被标注方法的返回类型必须为 {@code R<PdaActionResultVo>}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PdaIdempotent {
}

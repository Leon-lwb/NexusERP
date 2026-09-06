package com.nexus.common.satoken.pda;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PDA 接口幂等标记(全系统唯一实现, 原 ERP/MES 双套合并下沉, N4)
 * <p>
 * 按请求头 {@code X-Idempotent-Key} 幂等去重(键格式 单号/设备码:行号|0:动作):
 * <ul>
 *   <li>首次请求正常执行, 键写入 Redis (TTL 24h, 按租户隔离)</li>
 *   <li>重复请求(离线队列自动重放等)直接返回成功 + replayed 标记, 不重复执行业务</li>
 *   <li>未携带幂等键的请求不做去重, 直接执行</li>
 *   <li>业务失败/异常释放幂等键, 允许修正后重试</li>
 * </ul>
 * 被标注方法的返回类型必须为 {@code R<..ActionResultVo>} 之类带 ok 标记的结果 VO
 * (实现 {@link PdaActionResult}), 否则失败释放逻辑退化为仅按 R.code 判断。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PdaIdempotent {
}

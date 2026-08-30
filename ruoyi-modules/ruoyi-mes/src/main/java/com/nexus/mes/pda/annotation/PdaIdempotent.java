package com.nexus.mes.pda.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PDA 接口幂等标记 (MES 侧)
 * <p>
 * 与 ruoyi-erp 的 wm.annotation.PdaIdempotent 同语义(两模块互不依赖, 各自持有):
 * 按请求头 {@code X-Idempotent-Key} 幂等去重(键格式 单号/设备码:行号|0:动作)。
 * 技术债: 待 MES 流程稳定后考虑下沉到 ruoyi-common。
 * 被标注方法的返回类型必须为 {@code R<com.nexus.mes.pda.domain.vo.PdaActionResultVo>}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PdaIdempotent {
}

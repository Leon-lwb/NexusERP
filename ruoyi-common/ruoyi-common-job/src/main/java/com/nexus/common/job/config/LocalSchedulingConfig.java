package com.nexus.common.job.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 本地调度兜底配置（{@code snail-job.enabled=false} 时生效）。
 *
 * <p><b>为什么需要本类</b>：全仓 {@code @Scheduled} 的生效前提是容器里存在
 * {@code ScheduledAnnotationBeanPostProcessor}，它只由 {@code @EnableScheduling} 注册。
 * 而本项目此前唯一的 {@code @EnableScheduling} 挂在 {@link SnailJobConfig} 上，该类标了
 * {@code @ConditionalOnProperty(snail-job.enabled=true)} —— 于是生产环境
 * （{@code application-prod.yml: snail-job.enabled: false}）**根本没有开启 {@code @Scheduled}
 * 注解处理**：任何写在任务类里的 {@code @Scheduled} 兜底都会静默失效（既无日志也无异常）。
 * 这正是 {@code SoContractExpiryTask} 注释里“用 {@code @Scheduled} 写的任务在生产不会执行”的
 * 真实原因，也是“定时任务在生产永不执行”这一 P0 的第二层根因。</p>
 *
 * <p><b>生效条件</b>：仅在 {@code snail-job.enabled=false}（或缺省）时注册
 * {@code @EnableScheduling}；当 {@code snail-job.enabled=true} 时由 {@link SnailJobConfig}
 * 提供，避免重复注册（{@code ruoyi-iot} 的 {@code IotConfiguration} 另有独立的
 * 无条件 {@code @EnableScheduling}，与本类叠加同样安全）。</p>
 *
 * <p><b>影响面</b>：开启后，全仓既有的 {@code @Scheduled} 任务在生产也会开始执行
 * （{@code ImMessageClearTask} 每小时清理、{@code FaceVerifyBackfillTask} 每小时人脸补跑、
 * {@code AiThreadPoolConfig#cleanupOutputChannelRegistry} 每 10 分钟清理）——这些任务
 * 本来就是为生产设计的，此前因缺少 {@code @EnableScheduling} 而未执行。</p>
 *
 * <p>调度线程：本类不额外注册 {@code TaskScheduler} Bean，{@code @Scheduled} 方法将复用
 * 容器中唯一的 {@code TaskScheduler}（{@code iotTaskScheduler}）。若后续任务执行时间变长，
 * 建议把该调度器线程数从 2 调大，或为本兜底任务单独注册调度器。</p>
 *
 * @author nexus
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "false", matchIfMissing = true)
@EnableScheduling
public class LocalSchedulingConfig {
}

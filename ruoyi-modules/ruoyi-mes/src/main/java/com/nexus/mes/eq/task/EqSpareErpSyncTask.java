package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import com.nexus.mes.eq.mapper.EqSpareErpSyncMapper;
import com.nexus.mes.eq.service.IEqSparePartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * MES 备件 → ERP 过账同步对账/自动重投定时任务（MES-EQ-04 收口）
 * <p>
 * 周期扫描 {@code eq_spare_erp_sync} 中<b>未确认 ERP 落账</b>且<b>已停滞</b>的记录，逐条调用
 * {@link IEqSparePartService#retryErpSync(Long)} 对账/重投：
 * <ol>
 *   <li>先按业务幂等键查 ERP {@code wm_material_stock_tx} 流水，已落账则只标记 {@code SYNCED}
 *       并用 ERP 可用量回写 MES 派生缓存 {@code eq_spare_part.current_stock}；</li>
 *   <li>未落账才重新投递 MQ（重投次数 +1），投递失败写 {@code FAILED} 与失败原因。</li>
 * </ol>
 * <p>
 * <b>幂等</b>：{@link IEqSparePartService#retryErpSync} 对 {@code SYNCED} 记录直接早返回（不重投），
 * 且本任务的扫描条件本身就把 {@code SYNCED} 排除在外（双重保险），因此不会对已落账记录重复过账；
 * ERP 侧另有 Redis 原子占位 + 流水查重兜底。
 * <p>
 * <b>数据源</b>：类上必须标 {@code @DS("mes")}，否则运行期报
 * {@code Table 'ry-vue.eq_spare_erp_sync' doesn't exist}。
 * <p>
 * <b>调度</b>：与 {@code sj_job.id=30}（{@code biz_30}，executor {@code eqSpareErpSyncTask}）一致，
 * cron 为每 10 分钟一次（与 {@code @Scheduled} 上的表达式、{@code sj_job.trigger_interval} 三处相同）；
 * 生产 {@code snail-job.enabled=false} 时由
 * {@link #scheduledExecute()} 本地兜底（{@link SchedulerFallbackUtils}：开关判断 + Lock4j 分布式锁）。
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DS("mes")
@JobExecutor(name = "eqSpareErpSyncTask")
public class EqSpareErpSyncTask {

    /** 任务名：必须与 {@code @JobExecutor(name)} 及 {@code sj_job.executor_info} 完全一致 */
    private static final String TASK_NAME = "eqSpareErpSyncTask";
    /** 需要被对账/重投的同步状态（SYNCED 不在其中：已确认落账无需再动） */
    private static final List<String> PENDING_STATUS = Arrays.asList(
        IEqSparePartService.SYNC_PENDING, IEqSparePartService.SYNC_SENT, IEqSparePartService.SYNC_FAILED);
    /** 单次扫描上限，避免异常堆积时单轮耗时过长（下一轮继续处理） */
    private static final int MAX_SCAN_ROWS = 500;

    /**
     * 本地兜底调度开关：SnailJob 客户端启用时让位，避免同一任务被双重调度。
     */
    @Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 停滞阈值（分钟）：{@code last_publish_time} 早于 {@code now - N 分钟} 的记录才进入重投，
     * 避免与"投递后等待 ERP 消费"的正常窗口抢跑。默认 5 分钟。
     */
    @Value("${nexus.mes.eq.spare.erp-sync-stale-minutes:5}")
    private long staleMinutes = 5L;

    /**
     * 单条记录重投次数上限（0 = 不限制，保持默认行为）。用于异常堆积时的熔断逃生口，
     * 达到上限的记录保留 {@code FAILED} 状态等待人工在过账台账页处理。
     */
    @Value("${nexus.mes.eq.spare.erp-sync-max-retry:0}")
    private int maxRetry = 0;

    private final EqSpareErpSyncMapper erpSyncMapper;
    private final IEqSparePartService sparePartService;

    /**
     * 本地 {@code @Scheduled} 兜底入口（cron 与 {@code sj_job.id=30} 一致：每 10 分钟）。
     * <p>先判 {@code snail-job.enabled}（开启则不重复跑），再按任务名加 Lock4j 分布式锁
     * （多实例下同一时刻只有一个节点执行），锁过期时间大于单次任务最长耗时。</p>
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void scheduledExecute() {
        SchedulerFallbackUtils.runIfFallbackEnabled(snailJobEnabled, TASK_NAME, 600000L, () -> jobExecute(null));
    }

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数（本任务不使用）
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[备件ERP过账对账任务] 开始执行");
        try {
            Date cutoff = new Date(System.currentTimeMillis() - staleMinutes * 60_000L);
            // 跨租户扫描: 忽略租户插件(定时线程无 Sa-Token 上下文, 且需覆盖全部租户的停滞记录)
            List<EqSpareErpSync> candidates = TenantHelper.ignore(() -> erpSyncMapper.selectList(scanWrapper(cutoff)));
            if (candidates == null || candidates.isEmpty()) {
                SnailJobLog.REMOTE.info("[备件ERP过账对账任务] 无停滞记录, 跳过");
                return ExecuteResult.success("无停滞记录");
            }
            int synced = 0;
            int resent = 0;
            int failed = 0;
            int skipped = 0;
            for (EqSpareErpSync sync : candidates) {
                if (maxRetry > 0 && sync.getRetryCount() != null && sync.getRetryCount() >= maxRetry) {
                    log.warn("[备件ERP过账对账任务] 重投次数达上限({}), 跳过待人工处理: syncId={}, bizKey={}",
                        maxRetry, sync.getSyncId(), sync.getBizKey());
                    skipped++;
                    continue;
                }
                try {
                    // 单条按自身租户执行: retryErpSync 内部的对账查询/派生缓存回写都带 tenant_id
                    boolean confirmed = TenantHelper.dynamic(sync.getTenantId(), () -> sparePartService.retryErpSync(sync.getSyncId()));
                    if (confirmed) {
                        synced++;
                    } else {
                        resent++;
                    }
                } catch (Exception e) {
                    // 单条失败只记 WARN, 不中断整批
                    failed++;
                    log.warn("[备件ERP过账对账任务] 单条对账/重投失败, 继续处理后续: syncId={}, bizKey={}, error={}",
                        sync.getSyncId(), sync.getBizKey(), e.getMessage(), e);
                }
            }
            String summary = String.format("扫描 %d 条(停滞阈值 %d 分钟): 已确认落账 %d, 重投 %d, 失败 %d, 跳过 %d",
                candidates.size(), staleMinutes, synced, resent, failed, skipped);
            SnailJobLog.REMOTE.info("[备件ERP过账对账任务] 完成, {}", summary);
            return ExecuteResult.success(summary);
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[备件ERP过账对账任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("备件ERP过账对账任务失败: " + e.getMessage());
        }
    }

    /**
     * 构造停滞记录扫描条件：状态 ∈ (PENDING/SENT/FAILED) 且
     * ({@code last_publish_time} 为空 或 早于 {@code now - staleMinutes})。
     * <p>{@code last_publish_time} 为空说明"事务已提交但投递前进程退出"，属最需要兜底的一类，
     * 必须纳入扫描，否则永久停滞。</p>
     *
     * @param cutoff 停滞时间阈值
     * @return 查询条件
     */
    private LambdaQueryWrapper<EqSpareErpSync> scanWrapper(Date cutoff) {
        return Wrappers.<EqSpareErpSync>lambdaQuery()
            .in(EqSpareErpSync::getSyncStatus, PENDING_STATUS)
            .and(w -> w.isNull(EqSpareErpSync::getLastPublishTime)
                .or().le(EqSpareErpSync::getLastPublishTime, cutoff))
            .orderByAsc(EqSpareErpSync::getSyncId)
            .last("LIMIT " + MAX_SCAN_ROWS);
    }
}

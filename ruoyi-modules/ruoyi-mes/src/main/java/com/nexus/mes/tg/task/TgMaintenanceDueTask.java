package com.nexus.mes.tg.task;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.tg.domain.vo.TgToolingVo;
import com.nexus.mes.tg.service.ITgToolingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工装校准/保养到期自动提醒定时任务 (MES-TG-05)。
 *
 * <p>此前 tg 包下<b>没有任何定时任务</b>：{@code listMaintenanceDue()} 的唯一调用方是
 * {@code GET /mes/tg/tooling/due} 与前端手动按钮 —— 计划到期后没人打开台账页就永远不会有提醒，
 * 工装继续上机（叠加 MES-TG-02 后保养门禁完全失效）。本任务补齐自动提醒：</p>
 * <ol>
 *   <li>遍历有效租户（SnailJob 线程无 Sa-Token 上下文，必须 {@link TenantHelper#dynamic} 注入）；</li>
 *   <li>扫描 {@code next_calibration_date} 落在提醒窗口/已超期的工装（{@code listCalibrationDue}）；</li>
 *   <li>扫描已启用保养计划且达到阈值的工装（{@code listMaintenanceDue}）；</li>
 *   <li>按超期天数分级（P1/P2/P3）并经 Redis 去重限频（P1 6h / P2 12h / P3 24h）后
 *       SSE 推送同租户在线用户；不自动完成校准或保养（需人工执行并留痕）。</li>
 * </ol>
 *
 * <p><b>调度模式照 B1 批次（{@code MaintenanceDueTask}）</b>：{@code @JobExecutor} 供 SnailJob 回调，
 * 另加 {@code @Scheduled} 本地兜底 —— 生产 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动，
 * 只有兜底入口会真正执行；兜底由 {@link SchedulerFallbackUtils} 串起「开关判断 → Lock4j 分布式锁 →
 * 执行体」，多实例下仅一个节点执行。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DS("mes")
@JobExecutor(name = "tgMaintenanceDueTask")
public class TgMaintenanceDueTask {

    /** 本地兜底调度开关：SnailJob 客户端启用时本地兜底让位，避免双跑。 */
    @Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（MES-TG-05）。
     * <p>cron 与 {@code sj_job} 种子一致（每天 08:30，接在设备保养到期任务 08:00 之后）。</p>
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void scheduledExecute() {
        SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "tgMaintenanceDueTask", 900000L, () -> jobExecute(null));
    }

    private static final String SSE_TYPE_CALIBRATION = "TOOLING_CALIBRATION_DUE";
    private static final String SSE_TYPE_MAINTENANCE = "TOOLING_MAINTENANCE_DUE";
    private static final String CALIBRATION_THROTTLE_PREFIX = "mes:tg:calibration:due:rl:";
    private static final String MAINTENANCE_THROTTLE_PREFIX = "mes:tg:maintenance:due:rl:";
    /** 超期升级阈值(天): >30 天 P1, >7 天 P2, 其余 P3 */
    private static final long ESCALATE_P1_DAYS = 30L;
    private static final long ESCALATE_P2_DAYS = 7L;
    /** 默认租户(查不到租户列表时回退) */
    private static final String DEFAULT_TENANT_ID = "000000";

    private final ITgToolingService tgToolingService;
    private final MesSysTenantMapper mesSysTenantMapper;

    /** 超期级别: 越久级别越高(P1 > P2 > P3)，用于提醒文案与限频窗口。 */
    static String overdueLevel(long overdueDays) {
        if (overdueDays > ESCALATE_P1_DAYS) {
            return "P1";
        }
        if (overdueDays > ESCALATE_P2_DAYS) {
            return "P2";
        }
        return "P3";
    }

    /** 按超期级别决定限频窗口: P3=24h, P2=12h, P1=6h。 */
    static Duration throttleTtl(long overdueDays) {
        return switch (overdueLevel(overdueDays)) {
            case "P1" -> Duration.ofHours(6);
            case "P2" -> Duration.ofHours(12);
            default -> Duration.ofHours(24);
        };
    }

    /** 超期天数(应完成日期到今天, 未超期返回 0)。 */
    static long overdueDays(Date dueDate) {
        if (dueDate == null) {
            return 0L;
        }
        long diff = System.currentTimeMillis() - dueDate.getTime();
        return diff <= 0 ? 0L : diff / 86400000L;
    }

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[工装到期任务] 开始执行");
        try {
            List<String> tenantIds = queryTenantIds();
            int total = 0;
            for (String tenantId : tenantIds) {
                total += TenantHelper.dynamic(tenantId, this::scanAndNotify);
            }
            SnailJobLog.REMOTE.info("[工装到期任务] 完成, 遍历 {} 个租户, 处理 {} 条到期工装",
                tenantIds.size(), total);
            return ExecuteResult.success("处理 " + total + " 条到期工装");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[工装到期任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("工装到期任务失败: " + e.getMessage());
        }
    }

    /** 查询所有有效租户ID(跨库查 master 的 sys_tenant)；为空时回退默认租户。 */
    private List<String> queryTenantIds() {
        try {
            DynamicDataSourceContextHolder.push("master");
            List<String> ids = mesSysTenantMapper.selectAllTenantIds();
            return (ids == null || ids.isEmpty()) ? List.of(DEFAULT_TENANT_ID) : ids;
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 扫描当前租户下的校准/保养到期工装并逐条提醒；返回处理条数。
     * <p>包级可见以便单测直接驱动（绕过 TenantHelper 的租户上下文）。</p>
     */
    Integer scanAndNotify() {
        int processed = 0;
        for (TgToolingVo vo : safeList(tgToolingService.listCalibrationDue())) {
            try {
                if (notifyCalibration(vo)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("[工装到期任务] 校准提醒失败: toolingId={}, cause={}",
                    vo.getToolingId(), e.getMessage(), e);
            }
        }
        for (TgToolingVo vo : safeList(tgToolingService.listMaintenanceDue())) {
            try {
                if (notifyMaintenance(vo)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("[工装到期任务] 保养提醒失败: toolingId={}, cause={}",
                    vo.getToolingId(), e.getMessage(), e);
            }
        }
        return processed;
    }

    private boolean notifyCalibration(TgToolingVo vo) {
        long overdue = overdueDays(vo.getNextCalibrationDate());
        String level = overdueLevel(overdue);
        String throttleKey = CALIBRATION_THROTTLE_PREFIX + vo.getToolingId();
        if (!tryThrottle(throttleKey, level, throttleTtl(overdue))) {
            log.debug("[工装到期任务] 校准提醒限频中, 跳过: toolingId={}", vo.getToolingId());
            return false;
        }
        String reason = String.format("[%s] 校准%s: 工装[%s(%s)] 下次校准日[%s]",
            level, overdue > 0 ? "超期 " + overdue + " 天" : "即将到期",
            vo.getToolingName(), vo.getToolingCode(), vo.getNextCalibrationDate());
        publishSse(SSE_TYPE_CALIBRATION, vo, level, overdue, reason);
        log.info("[工装到期任务] 校准提醒已发出: toolingId={}, level={}, overdueDays={}",
            vo.getToolingId(), level, overdue);
        return true;
    }

    private boolean notifyMaintenance(TgToolingVo vo) {
        // 阈值型(BY_COUNT/BY_HOURS)无「应完成日期」，统一按 P3 / 24h 限频；
        // PERIODIC 计划可给出 next_maintain_time，则按超期天数升级
        long overdue = 0L;
        String level = overdueLevel(overdue);
        String throttleKey = MAINTENANCE_THROTTLE_PREFIX + vo.getToolingId();
        if (!tryThrottle(throttleKey, level, throttleTtl(overdue))) {
            log.debug("[工装到期任务] 保养提醒限频中, 跳过: toolingId={}", vo.getToolingId());
            return false;
        }
        String reason = String.format("[%s] 保养已到期: 工装[%s(%s)] 已用%s次/%s小时, 已达计划阈值",
            level, vo.getToolingName(), vo.getToolingCode(),
            vo.getUsedCount() == null ? 0 : vo.getUsedCount(),
            vo.getUsedHours() == null ? "0" : vo.getUsedHours().toPlainString());
        publishSse(SSE_TYPE_MAINTENANCE, vo, level, overdue, reason);
        log.info("[工装到期任务] 保养提醒已发出: toolingId={}, level={}", vo.getToolingId(), level);
        return true;
    }

    /**
     * 去重限频：同一工装在同一限频窗口内只提醒一次。
     * <p>包级可见的独立接缝（便于单测替换 I/O 边界；生产行为即 {@code RedisUtils.setObjectIfAbsent}）。</p>
     *
     * @param key   Redis key（{@code mes:tg:calibration:due:rl:{toolingId}} / {@code ...maintenance...}）
     * @param level 超期级别（写入 value，便于排障）
     * @param ttl   限频窗口
     * @return true 表示本次未被限频（可以提醒）
     */
    boolean tryThrottle(String key, String level, Duration ttl) {
        return RedisUtils.setObjectIfAbsent(key, level, ttl);
    }

    /**
     * SSE 推送提醒给同租户在线用户（包级可见的独立接缝，便于单测替换）。
     */
    void publishSse(String type, TgToolingVo vo, String level, long overdueDays, String reason) {
        if (!SseMessageUtils.isEnable()) {
            return;
        }
        Set<Long> userIds = collectOnlineUsers(TenantHelper.getTenantId());
        if (userIds.isEmpty()) {
            log.debug("[工装到期任务] 无同租户在线用户, 跳过 SSE: toolingId={}", vo.getToolingId());
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("action", "DUE");
        payload.put("level", level);
        payload.put("overdueDays", overdueDays);
        payload.put("toolingId", vo.getToolingId());
        payload.put("toolingCode", vo.getToolingCode());
        payload.put("toolingName", vo.getToolingName());
        payload.put("nextCalibrationDate", vo.getNextCalibrationDate());
        payload.put("reason", reason);

        SseMessageDto dto = new SseMessageDto();
        dto.setUserIds(List.copyOf(userIds));
        dto.setMessage(JsonUtils.toJsonString(payload));
        SseMessageUtils.publishMessage(dto);
    }

    /**
     * 扫描 Sa-Token 在线 Token, 过滤指定租户的在线用户ID集合
     * （与 {@code MaintenanceDueTask.collectOnlineUsers} 同口径：定时线程无请求上下文，
     * 但 {@code StpUtil.searchTokenValue} 直接读 Redis 会话索引）。
     */
    Set<Long> collectOnlineUsers(String tenantId) {
        Set<Long> userIds = new LinkedHashSet<>();
        List<String> keys;
        try {
            keys = StpUtil.searchTokenValue("", 0, -1, false);
        } catch (Exception e) {
            log.warn("[工装到期任务] 查询在线 Token 失败, 跳过 SSE 推送: {}", e.getMessage());
            return userIds;
        }
        if (CollUtil.isEmpty(keys)) {
            return userIds;
        }
        for (String key : keys) {
            int idx = key.lastIndexOf(':');
            String token = idx >= 0 ? key.substring(idx + 1) : key;
            try {
                LoginUser loginUser = LoginHelper.getLoginUser(token);
                if (loginUser == null || loginUser.getUserId() == null) {
                    continue;
                }
                if (tenantId == null || tenantId.equals(loginUser.getTenantId())) {
                    userIds.add(loginUser.getUserId());
                }
            } catch (Exception ignore) {
                // 单个 token 解析失败不影响整体
            }
        }
        return userIds;
    }

    private List<TgToolingVo> safeList(List<TgToolingVo> list) {
        return list == null ? List.of() : list;
    }
}

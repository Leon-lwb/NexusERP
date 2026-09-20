package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqMaintenancePlan;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqMaintenancePlanMapper;
import com.nexus.mes.pro.service.IProAndonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备保养到期自动提醒定时任务
 * <p>
 * 周期性扫描 eq_maintenance_plan 中 next_date &lt;= today 且 status=ACTIVE 的计划:
 * <ol>
 *   <li>Redis 限频: 按超期级别限频(见 {@link #throttleTtl(long)}), key=mes:maintenance:due:rl:{planId};</li>
 *   <li>SSE 推送 MAINTENANCE_DUE 消息给同租户在线用户(按 Sa-Token 在线 Token 过滤租户, MES-EQ-12);</li>
 *   <li>触发 EQUIPMENT 类型安灯({@link IProAndonService#callAndon}), reason 前缀带超期级别;</li>
 *   <li>不自动完成计划(需人工确认保养完成后调 IEqMaintenancePlanService.completePlan)。</li>
 * </ol>
 * <p>
 * 超期升级策略(MES-EQ-12, 此前"1 天/30 天提醒完全一致"):
 * <ul>
 *   <li>超期 ≤7 天: P3, 每 24h 提醒一次;</li>
 *   <li>超期 8~30 天: P2, 每 12h 提醒一次;</li>
 *   <li>超期 &gt;30 天: P1, 每 6h 提醒一次(需班组长介入)。</li>
 * </ul>
 * 建议频率: 每天 8:00 一次 (cron: 0 0 8 * * ?)
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DS("mes")
@JobExecutor(name = "maintenanceDueTask")
public class MaintenanceDueTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（MES-EQ-01）。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，保养到期提醒静默不执行。cron 与 {@code sj_job} 种子一致（每天 08:00）。
     * 任务体已按“每计划 24h 限频”幂等，另由 Lock4j 按任务名加分布式锁，多实例下仅一个节点执行。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 8 * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "maintenanceDueTask", 900000L, () -> jobExecute(null));
    }

    private static final String PLAN_STATUS_ACTIVE = "ACTIVE";
    private static final String SSE_TYPE = "MAINTENANCE_DUE";
    private static final String THROTTLE_PREFIX = "mes:maintenance:due:rl:";
    /** 超期升级阈值(天): >P1 为最高级, >P2 为中级 */
    private static final long ESCALATE_P1_DAYS = 30L;
    private static final long ESCALATE_P2_DAYS = 7L;
    /** 默认租户(多租户环境需扩展为遍历租户列表) */
    private static final String DEFAULT_TENANT_ID = "000000";

    private final EqMaintenancePlanMapper planMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final IProAndonService andonService;
    private final MesSysTenantMapper mesSysTenantMapper;

    /**
     * 超期级别: 超期越久级别越高(P1 > P2 > P3), 用于安灯文案与提醒频率(MES-EQ-12)。
     */
    static String overdueLevel(long overdueDays) {
        if (overdueDays > ESCALATE_P1_DAYS) {
            return "P1";
        }
        if (overdueDays > ESCALATE_P2_DAYS) {
            return "P2";
        }
        return "P3";
    }

    /**
     * 按超期级别决定限频窗口: P3=24h, P2=12h, P1=6h。
     */
    static Duration throttleTtl(long overdueDays) {
        String level = overdueLevel(overdueDays);
        return switch (level) {
            case "P1" -> Duration.ofHours(6);
            case "P2" -> Duration.ofHours(12);
            default -> Duration.ofHours(24);
        };
    }

    /**
     * 超期天数(计划应完成日期到今天的整天数, 未超期返回 0)。
     */
    static long overdueDays(Date nextDate) {
        if (nextDate == null) {
            return 0L;
        }
        long diff = System.currentTimeMillis() - nextDate.getTime();
        return diff <= 0 ? 0L : diff / 86400000L;
    }

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[保养到期任务] 开始执行");
        try {
            // 多租户: 遍历所有有效租户, 逐个注入上下文扫描(SnailJob 线程无 Sa-Token)
            List<String> tenantIds = queryTenantIds();
            int total = 0;
            for (String tenantId : tenantIds) {
                total += TenantHelper.dynamic(tenantId, this::scanAndNotify);
            }
            SnailJobLog.REMOTE.info("[保养到期任务] 完成, 遍历 {} 个租户, 处理 {} 条到期计划", tenantIds.size(), total);
            return ExecuteResult.success("处理 " + total + " 条到期计划");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[保养到期任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("保养到期任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有有效租户ID(跨库查 master 的 sys_tenant); 为空时回退默认租户
     */
    private List<String> queryTenantIds() {
        try {
            DynamicDataSourceContextHolder.push("master");
            List<String> ids = mesSysTenantMapper.selectAllTenantIds();
            return (ids == null || ids.isEmpty()) ? List.of(DEFAULT_TENANT_ID) : ids;
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private Integer scanAndNotify() {
        Date today = new Date();
        LambdaQueryWrapper<EqMaintenancePlan> wrapper = Wrappers.<EqMaintenancePlan>lambdaQuery()
            .eq(EqMaintenancePlan::getStatus, PLAN_STATUS_ACTIVE)
            .le(EqMaintenancePlan::getNextDate, today);
        List<EqMaintenancePlan> duePlans = planMapper.selectList(wrapper);
        int processed = 0;
        for (EqMaintenancePlan plan : duePlans) {
            try {
                notifyOne(plan);
                processed++;
            } catch (Exception e) {
                log.warn("[保养到期任务] 计划 {} 通知失败: {}", plan.getPlanId(), e.getMessage(), e);
            }
        }
        return processed;
    }

    private void notifyOne(EqMaintenancePlan plan) {
        // 1. 超期天数与升级级别(MES-EQ-12): 越期越久, 提醒越频繁、级别越高
        long overdueDays = overdueDays(plan.getNextDate());
        String level = overdueLevel(overdueDays);

        // 2. Redis 限频: 按级别限频(P3 24h / P2 12h / P1 6h)
        String throttleKey = THROTTLE_PREFIX + plan.getPlanId();
        if (!RedisUtils.setObjectIfAbsent(throttleKey, level, throttleTtl(overdueDays))) {
            log.debug("[保养到期任务] 计划 {} 限频中, 跳过", plan.getPlanId());
            return;
        }

        // 3. 查设备信息
        EqEquipment equipment = plan.getEquipmentId() == null ? null :
            equipmentMapper.selectById(plan.getEquipmentId());
        String equipName = equipment == null ? "未知设备" : equipment.getEquipmentName();
        String equipCode = equipment == null ? "N/A" : equipment.getEquipmentCode();

        String reason = String.format("[%s] 保养超期 %d 天: 计划[%s] 设备[%s(%s)] 类型[%s] 应完成日期[%s]",
            level, overdueDays, plan.getPlanName(), equipName, equipCode, plan.getMaintenanceType(),
            plan.getNextDate());

        // 4. SSE 推送给同租户在线用户(真实在线用户集合, 不再恒为空集)
        publishMaintenanceSse(plan, equipment, reason, level, overdueDays);

        // 5. 触发 EQUIPMENT 类型安灯
        try {
            andonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
            log.info("[保养到期任务] 计划 {} 超期 {} 天({}), 已触发 EQUIPMENT 安灯",
                plan.getPlanId(), overdueDays, level);
        } catch (Exception e) {
            log.warn("[保养到期任务] 计划 {} 触发安灯失败: {}", plan.getPlanId(), e.getMessage());
        }
    }

    private void publishMaintenanceSse(EqMaintenancePlan plan, EqEquipment equipment, String reason,
                                       String level, long overdueDays) {
        if (!SseMessageUtils.isEnable()) {
            return;
        }
        // SnailJob/定时线程无 Sa-Token 请求上下文, 但 StpUtil.searchTokenValue 直接读 Redis 会话索引,
        // 因此可按租户拿到真实在线用户(与 ProAndonServiceImpl.collectOnlineUsersByTenant 同口径)
        Set<Long> userIds = collectOnlineUsers(TenantHelper.getTenantId());
        if (userIds.isEmpty()) {
            log.debug("[保养到期任务] 无同租户在线用户, 跳过 SSE: planId={}", plan.getPlanId());
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", SSE_TYPE);
        payload.put("action", "DUE");
        payload.put("level", level);
        payload.put("overdueDays", overdueDays);
        payload.put("planId", plan.getPlanId());
        payload.put("planName", plan.getPlanName());
        payload.put("equipmentId", plan.getEquipmentId());
        payload.put("equipmentName", equipment == null ? null : equipment.getEquipmentName());
        payload.put("nextDate", plan.getNextDate());
        payload.put("reason", reason);

        SseMessageDto dto = new SseMessageDto();
        dto.setUserIds(List.copyOf(userIds));
        dto.setMessage(JsonUtils.toJsonString(payload));
        SseMessageUtils.publishMessage(dto);
        log.info("[保养到期任务] 计划 {} SSE 已推送 {} 个在线用户", plan.getPlanId(), userIds.size());
    }

    /**
     * 扫描 Sa-Token 在线 Token, 过滤指定租户的在线用户ID集合(MES-EQ-12)。
     * <p>与 {@code ProAndonServiceImpl.collectOnlineUsersByTenant} / ruoyi-iot
     * {@code IotAlertNotifier} 同名方法逻辑一致, 消除此前"恒返回空集"的死代码。</p>
     */
    Set<Long> collectOnlineUsers(String tenantId) {
        Set<Long> userIds = new LinkedHashSet<>();
        List<String> keys;
        try {
            keys = StpUtil.searchTokenValue("", 0, -1, false);
        } catch (Exception e) {
            log.warn("[保养到期任务] 查询在线 Token 失败, 跳过 SSE 推送: {}", e.getMessage());
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
}

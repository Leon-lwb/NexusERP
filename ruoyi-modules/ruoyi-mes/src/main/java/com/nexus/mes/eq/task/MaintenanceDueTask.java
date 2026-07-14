package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备保养到期自动提醒定时任务
 * <p>
 * 周期性扫描 eq_maintenance_plan 中 next_date &lt;= today 且 status=ACTIVE 的计划:
 * <ol>
 *   <li>Redis 限频: 每计划每 24 小时仅推送一次 (key=mes:maintenance:due:rl:{planId}, TTL=24h);</li>
 *   <li>SSE 推送 MAINTENANCE_DUE 消息给租户内在线用户(SnailJob 线程无 Sa-Token 上下文, 当前简化为空集,
 *       实际靠安灯看板刷新触发);</li>
 *   <li>触发 EQUIPMENT 类型安灯呼叫({@link IProAndonService#callAndon});</li>
 *   <li>不自动完成计划(需人工确认保养完成后调 IEqMaintenancePlanService.completePlan)。</li>
 * </ol>
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

    private static final String PLAN_STATUS_ACTIVE = "ACTIVE";
    private static final String SSE_TYPE = "MAINTENANCE_DUE";
    private static final String THROTTLE_PREFIX = "mes:maintenance:due:rl:";
    private static final Duration THROTTLE_TTL = Duration.ofHours(24);
    /** 默认租户(多租户环境需扩展为遍历租户列表) */
    private static final String DEFAULT_TENANT_ID = "000000";

    private final EqMaintenancePlanMapper planMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final IProAndonService andonService;
    private final MesSysTenantMapper mesSysTenantMapper;

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
        // 1. Redis 限频: 每计划每 24 小时仅推送一次
        String throttleKey = THROTTLE_PREFIX + plan.getPlanId();
        if (!RedisUtils.setObjectIfAbsent(throttleKey, "1", THROTTLE_TTL)) {
            log.debug("[保养到期任务] 计划 {} 限频中, 跳过", plan.getPlanId());
            return;
        }

        // 2. 查设备信息
        EqEquipment equipment = plan.getEquipmentId() == null ? null :
            equipmentMapper.selectById(plan.getEquipmentId());
        String equipName = equipment == null ? "未知设备" : equipment.getEquipmentName();
        String equipCode = equipment == null ? "N/A" : equipment.getEquipmentCode();

        String reason = String.format("保养到期: 计划[%s] 设备[%s(%s)] 类型[%s] 应完成日期[%s]",
            plan.getPlanName(), equipName, equipCode, plan.getMaintenanceType(),
            plan.getNextDate());

        // 3. SSE 推送给租户内在线用户
        publishMaintenanceSse(plan, equipment, reason);

        // 4. 触发 EQUIPMENT 类型安灯
        try {
            andonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
            log.info("[保养到期任务] 计划 {} 已触发 EQUIPMENT 安灯", plan.getPlanId());
        } catch (Exception e) {
            log.warn("[保养到期任务] 计划 {} 触发安灯失败: {}", plan.getPlanId(), e.getMessage());
        }
    }

    private void publishMaintenanceSse(EqMaintenancePlan plan, EqEquipment equipment, String reason) {
        if (!SseMessageUtils.isEnable()) {
            return;
        }
        // 简化: SnailJob 线程无 Sa-Token token, 此处返回空集, SSE 实际靠安灯看板刷新触发
        // 若需真实推送, 参考 ProAndonServiceImpl.collectOnlineUsersByTenant 用 StpUtil.searchTokenValue
        Set<Long> userIds = collectOnlineUsers();
        if (userIds.isEmpty()) {
            log.debug("[保养到期任务] 无在线用户, 跳过 SSE: planId={}", plan.getPlanId());
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", SSE_TYPE);
        payload.put("action", "DUE");
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
    }

    /**
     * 扫描在线用户。
     * <p>
     * SnailJob 线程无 Sa-Token 上下文, {@code StpUtil.searchTokenValue} 在无 Web 请求上下文时不可靠,
     * 此处简化为空集, SSE 实际推送由前端安灯看板轮询/刷新触发。
     */
    private Set<Long> collectOnlineUsers() {
        return Collections.emptySet();
    }
}

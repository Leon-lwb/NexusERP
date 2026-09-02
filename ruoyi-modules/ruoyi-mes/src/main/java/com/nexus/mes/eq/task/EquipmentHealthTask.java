package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.eq.service.IEqHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备健康评分定时任务（预测性维护）
 * <p>
 * 周期性评估全部关联 IoT 的设备健康(24h EWMA 异常点 + 维修历史 → 评分)，
 * LOW 级且评分下滑时由 Service 内部触发 EQUIPMENT 安灯。
 * 建议频率: 每天 1-2 次 (cron: 0 0 7 * * ?)。
 * <p>
 * 多租户: 遍历有效租户逐个注入上下文(同 {@link MaintenanceDueTask} 模式)。
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DS("mes")
@JobExecutor(name = "equipmentHealthTask")
public class EquipmentHealthTask {

    /** 默认租户(多租户环境需扩展为遍历租户列表) */
    private static final String DEFAULT_TENANT_ID = "000000";

    private final IEqHealthService healthService;
    private final MesSysTenantMapper mesSysTenantMapper;

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[设备健康任务] 开始执行");
        try {
            List<String> tenantIds = queryTenantIds();
            int total = 0;
            for (String tenantId : tenantIds) {
                total += TenantHelper.dynamic(tenantId, healthService::evaluateAll);
            }
            SnailJobLog.REMOTE.info("[设备健康任务] 完成, 遍历 {} 个租户, 评估 {} 台设备", tenantIds.size(), total);
            return ExecuteResult.success("评估 " + total + " 台设备");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[设备健康任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("设备健康任务失败: " + e.getMessage());
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
}

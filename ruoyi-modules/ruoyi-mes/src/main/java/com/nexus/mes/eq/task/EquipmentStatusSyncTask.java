package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IoT 设备状态联动定时任务（F8-5）。
 * <p>
 * 周期性扫描 eq_equipment 中 iot_device_id 非空且状态在 {NORMAL, OFFLINE} 内的设备，
 * 调 {@link IotDeviceDataQueryService#getOnlineStatus}（Redis）回写 eq_equipment.status：
 * <ul>
 *   <li>IoT ONLINE + 当前 OFFLINE -> NORMAL（离线恢复）；</li>
 *   <li>IoT OFFLINE + 当前 NORMAL -> OFFLINE（掉线）；</li>
 *   <li>IoT UNKNOWN/null（无缓存）-> 跳过，不臆断；</li>
 *   <li>FAULT/MAINTENANCE/SCRAPPED 受管状态一律不覆盖（CAS WHERE status IN(NORMAL,OFFLINE)）。</li>
 * </ul>
 * 多租户: 遍历所有有效租户逐个注入上下文（SnailJob 线程无 Sa-Token），对齐 MaintenanceDueTask 模式。
 * <p>
 * 建议频率: 每 1 分钟一次（cron: 7 * * * * ?，避开整点 :00）。
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DS("mes")
@JobExecutor(name = "equipmentStatusSyncTask")
public class EquipmentStatusSyncTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（MES-EQ-01）。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，设备掉线/恢复状态永不回写看板。cron 与 {@code sj_job} 种子一致
     * （每 1 分钟，避开整点第 7 秒）。任务体是 CAS 条件更新（{@code WHERE status IN(NORMAL,OFFLINE)}）
     * 本身幂等，另由 Lock4j 按任务名加分布式锁，多实例下仅一个节点执行。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "7 */1 * * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "equipmentStatusSyncTask", 120000L, () -> jobExecute(null));
    }

    /** IoT 侧在线状态值 */
    private static final String IOT_ONLINE = "ONLINE";
    /** IoT 侧离线状态值 */
    private static final String IOT_OFFLINE = "OFFLINE";
    /** 默认租户（多租户环境需扩展为遍历租户列表） */
    private static final String DEFAULT_TENANT_ID = "000000";
    /** 仅同步受 IoT 驱动的两态；FAULT/MAINTENANCE/SCRAPPED 为受管状态不碰 */
    private static final List<String> SYNCABLE_STATUS =
        List.of(IEqEquipmentService.STATUS_NORMAL, IEqEquipmentService.STATUS_OFFLINE);

    private final EqEquipmentMapper equipmentMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;
    private final MesSysTenantMapper mesSysTenantMapper;

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[设备状态联动任务] 开始执行");
        try {
            List<String> tenantIds = queryTenantIds();
            int total = 0;
            for (String tenantId : tenantIds) {
                total += TenantHelper.dynamic(tenantId, this::syncTenant);
            }
            SnailJobLog.REMOTE.info("[设备状态联动任务] 完成, 遍历 {} 个租户, 同步 {} 台设备", tenantIds.size(), total);
            return ExecuteResult.success("同步 " + total + " 台设备");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[设备状态联动任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("设备状态联动任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有有效租户ID（跨库查 master 的 sys_tenant）；为空时回退默认租户。
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

    /**
     * 同步当前租户（由 TenantHelper.dynamic 注入）下所有 IoT 绑定设备的运行状态。
     *
     * @return 本租户实际发生状态变更的设备数
     */
    private Integer syncTenant() {
        List<EqEquipment> list = equipmentMapper.selectList(
            Wrappers.<EqEquipment>lambdaQuery()
                .isNotNull(EqEquipment::getIotDeviceId)
                .in(EqEquipment::getStatus, SYNCABLE_STATUS));
        int changed = 0;
        for (EqEquipment eq : list) {
            try {
                if (syncOne(eq)) {
                    changed++;
                }
            } catch (Exception e) {
                log.warn("[设备状态联动任务] 设备 {} 同步失败: {}", eq.getEquipmentId(), e.getMessage(), e);
            }
        }
        return changed;
    }

    /**
     * 同步单台设备状态。
     * <p>
     * CAS 更新 WHERE status IN(NORMAL,OFFLINE)：若设备刚被维修/保养/报废流程置为受管状态，
     * 本次更新命中 0 行，不覆盖。
     *
     * @param eq 设备
     * @return true 表示实际变更了状态
     */
    boolean syncOne(EqEquipment eq) {
        String iotStatus = iotDeviceDataQueryService.getOnlineStatus(eq.getIotDeviceId());
        String target;
        if (IOT_ONLINE.equals(iotStatus)) {
            target = IEqEquipmentService.STATUS_NORMAL;
        } else if (IOT_OFFLINE.equals(iotStatus)) {
            target = IEqEquipmentService.STATUS_OFFLINE;
        } else {
            // UNKNOWN 或 null：IoT 侧无缓存，不臆断，跳过
            return false;
        }
        if (target.equals(eq.getStatus())) {
            return false; // 无变化
        }
        int rows = equipmentMapper.update(null,
            Wrappers.<EqEquipment>lambdaUpdate()
                .set(EqEquipment::getStatus, target)
                .eq(EqEquipment::getEquipmentId, eq.getEquipmentId())
                .in(EqEquipment::getStatus, SYNCABLE_STATUS));
        return rows > 0;
    }
}

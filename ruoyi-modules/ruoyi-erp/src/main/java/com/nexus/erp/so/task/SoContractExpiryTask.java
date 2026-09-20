package com.nexus.erp.so.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.so.service.ISoContractService;
import com.nexus.system.domain.SysTenant;
import com.nexus.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 销售合同到期扫描任务（SnailJob 执行器）。
 *
 * <p><b>为什么需要本任务</b>：{@code so_contract_status} 字典里有 EXPIRED（已到期），
 * 但全仓此前**没有任何代码**会写入该状态 —— 合同一旦生效就永远停在 EFFECTIVE，
 * 过期合同仍可被引用、仍出现在「有效合同」统计里。本任务每天扫描并把
 * {@code expiry_date < 今天} 的 EFFECTIVE 合同置为 EXPIRED（到期日当天仍算有效）。</p>
 *
 * <p><b>调度机制（2026-09-20 修复）</b>：本任务提供两条调度入口 ——
 * ①{@code @JobExecutor}，由 SnailJob 调度（{@code snail-job.enabled=true} 时生效）；
 * ②{@link #scheduledExecute()} 的 {@code @Scheduled} 本地兜底，由
 * {@code com.nexus.common.job.config.LocalSchedulingConfig} 在
 * {@code snail-job.enabled=false} 时开启 {@code @EnableScheduling} 才会真正生效，
 * 并用 Lock4j 分布式锁保证多实例不重复执行。
 * 此前生产环境 {@code snail-job.enabled: false} 且全仓唯一的 {@code @EnableScheduling}
 * 挂在条件装配的 {@code SnailJobConfig} 上，导致 {@code @Scheduled} 注解处理根本没开启 ——
 * 单靠 {@code @Scheduled} 写的任务在生产不会执行，这正是本次修复的根因之一。</p>
 *
 * <p>多租户：任务线程无登录态，需遍历所有租户并在 {@link TenantHelper#dynamic} 下执行，
 * 否则只有默认租户的合同会被扫描。{@code sys_tenant} 在 ry-vue(master) 库，
 * 本类不加 {@code @DS} 走默认数据源；实际读写由 {@code SoContractServiceImpl} 上的
 * {@code @DS("erp")} 切到业务库。</p>
 *
 * <p>幂等：状态条件更新（EFFECTIVE → EXPIRED），重复执行或集群多节点并发执行结果一致。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "soContractExpiryTask")
public class SoContractExpiryTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，过期合同永远停在 EFFECTIVE。cron 与 {@code sj_job} 种子一致
     * （每天 01:30）。执行体是状态条件更新（EFFECTIVE → EXPIRED）本身幂等，另由 Lock4j
     * 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 1 * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "soContractExpiryTask", 900000L, () -> jobExecute(null));
    }

    private final ISoContractService soContractService;
    private final SysTenantMapper sysTenantMapper;

    /**
     * 执行销售合同到期扫描。
     *
     * @param jobArgs 任务参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行销售合同到期扫描任务");
        try {
            List<SysTenant> tenants = sysTenantMapper.selectList(Wrappers.lambdaQuery(SysTenant.class));
            if (tenants == null || tenants.isEmpty()) {
                TenantHelper.dynamic("000000", soContractService::expireOverdueContracts);
            } else {
                for (SysTenant tenant : tenants) {
                    TenantHelper.dynamic(tenant.getTenantId(), soContractService::expireOverdueContracts);
                }
            }
            SnailJobLog.REMOTE.info("销售合同到期扫描任务执行完成");
            return ExecuteResult.success("销售合同到期扫描完成");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("销售合同到期扫描任务执行失败: {}", e.getMessage());
            return ExecuteResult.failure("销售合同到期扫描失败: " + e.getMessage());
        }
    }
}

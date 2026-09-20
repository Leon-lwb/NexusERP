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
 * <p><b>为什么用 {@code @JobExecutor} 而不是 {@code @Scheduled}</b>：本项目的
 * {@code @EnableScheduling} 是条件装配的（{@code SnailJobConfig} 上标了
 * {@code @ConditionalOnProperty(snail-job.enabled=true)}），而生产环境
 * {@code snail-job.enabled: false} —— 用 {@code @Scheduled} 写的任务在生产不会执行。
 * 项目既有的定时任务（{@code StockAlertTask}、{@code VendorEvaluationTask}）统一走
 * SnailJob 执行器，本任务与之一致，并在 {@code script/sql/ry-vue.sql} 的
 * {@code sj_job} / {@code sj_job_executor} 中登记。</p>
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

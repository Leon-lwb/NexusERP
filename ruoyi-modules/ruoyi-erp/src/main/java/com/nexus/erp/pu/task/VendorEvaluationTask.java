package com.nexus.erp.pu.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.IPuVendorEvaluationService;
import com.nexus.system.domain.SysTenant;
import com.nexus.system.mapper.SysTenantMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 供应商绩效评估定时任务
 * <p>
 * 基于 SnailJob 分布式任务调度框架执行的定时任务，
 * 负责定期扫描所有供应商并触发批量绩效评估计算。
 * 评估维度包括交货及时率、质量合格率、价格竞争力，
 * 计算结果用于供应商分级管理和采购决策参考。
 * </p>
 * <p>任务线程无登录态，需遍历所有租户并在 TenantHelper.dynamic 下执行，否则评估数据串租户/写不进去。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "vendorEvaluationTask")
public class VendorEvaluationTask {

    private final IPuVendorEvaluationService vendorEvaluationService;
    private final SysTenantMapper sysTenantMapper;

    /**
     * 执行供应商绩效批量评估任务
     * <p>
     * 遍历所有租户，在每个租户上下文内执行 {@link IPuVendorEvaluationService#batchCalculateAll()}。
     *
     * @param jobArgs 任务参数，包含任务执行上下文信息
     * @return 任务执行结果，成功或失败状态及提示信息
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行供应商绩效批量评估任务");
        try {
            // sys_tenant 表在 ry-vue(master) 库，任务类无 @DS 注解走默认数据源，可直接查询
            List<SysTenant> tenants = sysTenantMapper.selectList(Wrappers.lambdaQuery(SysTenant.class));
            if (tenants == null || tenants.isEmpty()) {
                // 无租户配置时回退默认租户执行
                TenantHelper.dynamic("000000", () -> vendorEvaluationService.batchCalculateAll());
            } else {
                for (SysTenant tenant : tenants) {
                    String tenantId = String.valueOf(tenant.getTenantId());
                    TenantHelper.dynamic(tenantId, () -> vendorEvaluationService.batchCalculateAll());
                }
            }
            SnailJobLog.REMOTE.info("供应商绩效批量评估任务执行完成");
            return ExecuteResult.success("供应商绩效批量评估完成");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("供应商绩效批量评估任务执行失败: {}", e.getMessage());
            return ExecuteResult.failure("供应商绩效批量评估失败: " + e.getMessage());
        }
    }
}

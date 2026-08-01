package com.nexus.erp.wm.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.service.IWmStockAlertService;
import com.nexus.system.domain.SysTenant;
import com.nexus.system.mapper.SysTenantMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全库存预警定时任务
 * 定期扫描物料库存，当库存低于安全库存或最低库存时自动生成预警记录
 * <p>任务线程无登录态，需遍历所有租户并在 TenantHelper.dynamic 下执行，否则仅默认租户物料生成预警。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "stockAlertTask")
public class StockAlertTask {

    private final IWmStockAlertService stockAlertService;
    private final SysTenantMapper sysTenantMapper;

    /**
     * 执行安全库存预警检查任务
     *
     * @param jobArgs 任务参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行安全库存预警检查任务");
        try {
            // sys_tenant 表在 ry-vue(master) 库，任务类无 @DS 注解走默认数据源，可直接查询
            List<SysTenant> tenants = sysTenantMapper.selectList(Wrappers.lambdaQuery(SysTenant.class));
            if (tenants == null || tenants.isEmpty()) {
                TenantHelper.dynamic("000000", () -> stockAlertService.checkStockAlerts());
            } else {
                for (SysTenant tenant : tenants) {
                    TenantHelper.dynamic(tenant.getTenantId(), () -> stockAlertService.checkStockAlerts());
                }
            }
            SnailJobLog.REMOTE.info("安全库存预警检查任务执行完成");
            return ExecuteResult.success("安全库存预警检查完成");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("安全库存预警检查任务执行失败 {}", e.getMessage());
            return ExecuteResult.failure("安全库存预警检查失败 " + e.getMessage());
        }
    }
}

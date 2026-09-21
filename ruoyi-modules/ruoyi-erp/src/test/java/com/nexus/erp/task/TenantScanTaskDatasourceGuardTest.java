package com.nexus.erp.task;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.pu.task.VendorEvaluationTask;
import com.nexus.erp.wm.task.StockAlertTask;
import com.nexus.erp.wm.task.WmBatchExpiryTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 防回归：读 master 库 {@code sys_tenant} 的定时任务类**不得**带类级 {@code @DS}。
 *
 * <p><b>背景（2026-09-21 回归）</b>：这三个任务类原本无 {@code @DS}，走默认(master)数据源查
 * {@code sys_tenant}，再在 {@code TenantHelper.dynamic} 下调用各自 Service（Service 自身带
 * {@code @DS("erp")}）—— 这是正确范式。某批次为修「未标注时默认走 master」的报错，给它们补上了
 * 类级 {@code @DS("erp")}，结果：{@code sys_tenant} 只建在 ry-vue(master) 库，类级 {@code @DS}
 * 让 {@code sysTenantMapper} 查询打到 ry-erp，抛
 * {@code Table 'ry-erp.sys_tenant' doesn't exist}，又被 {@code catch (Exception)} 吞成
 * {@code ExecuteResult.failure} —— 租户循环永不执行，安全库存预警 / 批次效期标记 / 供应商绩效
 * 三个任务在生产 100% 静默失效（且返回形状「正常」）。</p>
 *
 * <p>同型正确写法见 {@code com.nexus.erp.so.task.SoContractExpiryTask}（刻意不加 {@code @DS}，
 * javadoc 说明了原因）。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class TenantScanTaskDatasourceGuardTest {

    /** 需要跨库读 master 的 sys_tenant、再逐个租户 dynamic 执行的任务类 */
    private static final Class<?>[] TENANT_SCAN_TASKS = {
        StockAlertTask.class,
        WmBatchExpiryTask.class,
        VendorEvaluationTask.class
    };

    @Test
    @DisplayName("读 sys_tenant 的定时任务类不得声明类级 @DS（否则查询打到业务库、任务静默失效）")
    void tenantScanTasksMustNotDeclareClassLevelDs() {
        for (Class<?> type : TENANT_SCAN_TASKS) {
            assertNull(type.getAnnotation(DS.class),
                type.getSimpleName() + " 不得标类级 @DS：sys_tenant 只在 master(ry-vue) 库，"
                    + "类级 @DS 会让 sysTenantMapper 打到业务库并抛 "
                    + "Table '...sys_tenant' doesn't exist，被 catch 吞成 failure、租户循环永不执行。"
                    + "erp 侧读写由对应 Service 实现类自身的 @DS(\"erp\") 提供。");
        }
    }
}

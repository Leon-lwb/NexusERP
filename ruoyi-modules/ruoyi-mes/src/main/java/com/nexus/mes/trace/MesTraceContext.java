package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 原生数据库调用也必须带租户条件，并核验当前连接确实属于制造库。
 */
@Component
@RequiredArgsConstructor
public class MesTraceContext {
    private final MesProgressTransactionGuard connectionGuard;

    public String tenant() {
        String tenant = TenantHelper.getTenantId();
        need(
                TenantHelper.isEnable() && tenant != null && tenant.matches("[0-9]{6,20}"),
                "追溯需要可信租户上下文");
        return tenant;
    }

    public void requireTenant(String tenant) {
        need(tenant != null && tenant.equals(tenant()), "追溯租户不一致");
    }

    public void requireWrite() {
        tenant();
        requireConnection();
        MesPreparedWorkOrderPolicies.markWriteStarted();
    }

    public void requireConnection() {
        need(
                TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        && "mes".equals(DynamicDataSourceContextHolder.peek()),
                "追溯写入必须属于实际MES事务");
        connectionGuard.requireMesConnection();
    }

    public void requireNoTransaction() {
        need(
                !TransactionSynchronizationManager.isActualTransactionActive(),
                "ERP预留前不得持有MES写事务，请从编排入口重试");
    }

    public static void need(boolean condition, String message) {
        if (!condition) {
            throw new ServiceException(message);
        }
    }
}

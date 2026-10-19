package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.SimpleConnectionHandle;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 追溯写入的租户、实际本地事务和业务日期边界。
 */
@Component
@RequiredArgsConstructor
public class ErpTraceContext {
    private final SqlSessionFactory sqlSessionFactory;

    public String tenant() {
        String tenant = TenantHelper.getTenantId();
        if (!TenantHelper.isEnable() || tenant == null || !tenant.matches("[0-9]{6,20}")) {
            throw new ServiceException("物料追溯需要有效租户上下文");
        }
        return tenant;
    }

    public void requireTenant(String tenant) {
        if (tenant == null || !tenant.matches("[0-9]{6,20}") || !tenant.equals(tenant())) {
            throw new ServiceException("物料追溯租户不一致");
        }
    }

    public void requireWrite(String tenant) {
        requireTenant(tenant);
        requireConnection();
    }

    public void requireConnection() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !"erp".equals(DynamicDataSourceContextHolder.peek())) {
            throw new ServiceException("身份登记必须在实际ERP写事务中执行");
        }
        try {
            DataSource source =
                    sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
            if (!(source instanceof DynamicRoutingDataSource routing)) {
                throw new ServiceException("ERP数据源不可核验");
            }
            DataSource registered = routing.getDataSources().get("erp");
            DataSource physical =
                    registered instanceof ItemDataSource item
                            ? item.getRealDataSource()
                            : registered;
            Object resource = TransactionSynchronizationManager.getResource(routing);
            if (!(physical instanceof HikariDataSource pool)
                    || !(resource instanceof ConnectionHolder holder)
                    || !(holder.getConnectionHandle() instanceof SimpleConnectionHandle)) {
                throw new ServiceException("缺少实际ERP事务连接");
            }
            var connection = holder.getConnection();
            String configured = base(pool.getJdbcUrl());
            if (connection.isClosed()
                    || connection.getAutoCommit()
                    || connection.isReadOnly()
                    || !configured.equals(base(connection.getMetaData().getURL()))
                    || (configured.startsWith("jdbc:mysql:")
                                    || configured.startsWith("jdbc:mariadb:"))
                            && !configured
                                    .substring(configured.lastIndexOf('/') + 1)
                                    .equals(connection.getCatalog())) {
                throw new ServiceException("实际事务连接不属于ERP写库");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("ERP事务连接核验失败");
        }
    }

    private static String base(String url) {
        if (url == null || !url.startsWith("jdbc:")) {
            throw new ServiceException("ERP数据源地址不可核验");
        }
        return url.split("[?;]", 2)[0];
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }
}

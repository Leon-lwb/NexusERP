package com.nexus.mes.pro.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.nexus.common.core.exception.ServiceException;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.SimpleConnectionHandle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;

/** 不借新连接、不补绑资源，核对当前事务实际连接属于配置的 MES 池，防只切路由仍钉在 ERP。 */
@Component
@RequiredArgsConstructor
public class MesProgressTransactionGuard {
    private final SqlSessionFactory sqlSessionFactory;

    public void requireMesConnection() {
        try {
            DataSource source = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
            if (!(source instanceof DynamicRoutingDataSource routing)) throw new ServiceException("工序投影路由来源无法核验");
            DataSource registered = routing.getDataSources().get("mes");
            DataSource physical = registered instanceof ItemDataSource item ? item.getRealDataSource() : registered;
            Object resource = TransactionSynchronizationManager.getResource(routing);
            if (!(physical instanceof HikariDataSource pool) || !(resource instanceof ConnectionHolder holder) || !(holder.getConnectionHandle() instanceof SimpleConnectionHandle))
                throw new ServiceException("工序投影缺少实际 MES 事务连接");
            Connection connection = holder.getConnection();
            if (connection.isClosed() || connection.getAutoCommit() || connection.isReadOnly()
                || !jdbcBase(pool.getJdbcUrl()).equals(jdbcBase(connection.getMetaData().getURL()))
                || !matchesCatalog(pool.getJdbcUrl(), connection.getCatalog()))
                throw new ServiceException("工序投影实际连接不是可写 MES 连接");
        } catch (ServiceException error) {
            throw error;
        } catch (Exception error) {
            // 连接异常可能含 URL/用户名等配置，不向日志和响应转交原异常文本。
            throw new ServiceException("工序投影事务连接核验失败");
        }
    }

    private boolean matchesCatalog(String url, String catalog) {
        String base = jdbcBase(url);
        // MySQL URL与catalog分别核对，防连接在URL不变时已被切换到其他库。
        if (base.startsWith("jdbc:mysql:") || base.startsWith("jdbc:mariadb:")) {
            return catalog != null && catalog.equals(base.substring(base.lastIndexOf('/') + 1));
        }
        return true;
    }

    private String jdbcBase(String url) {
        if (url == null || !url.startsWith("jdbc:")) throw new ServiceException("工序投影数据源地址不可核验");
        return url.split("[?;]", 2)[0];
    }
}

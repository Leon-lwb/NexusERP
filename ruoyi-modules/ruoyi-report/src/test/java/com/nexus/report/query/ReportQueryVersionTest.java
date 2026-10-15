package com.nexus.report.query;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportResourceStore;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

import java.util.*;

/** 用真实事务验证查询草稿修订、固定发布与租户边界。 */
class ReportQueryVersionTest {
    /** 执行immutableVersionsAndRevisionConflictsAreTenantBound对应的受控处理。 */
    @Test
    void immutableVersionsAndRevisionConflictsAreTenantBound() {
        var source =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:resource_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE rpt_query_definition(query_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),code VARCHAR(64),name VARCHAR(100),definition_json"
                        + " CLOB,revision BIGINT,published_version_id BIGINT,status"
                        + " VARCHAR(20),create_dept BIGINT,create_by BIGINT,update_time TIMESTAMP"
                        + " DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,code))");
        jdbc.execute(
                "CREATE TABLE rpt_query_version(version_id BIGINT PRIMARY KEY,query_id"
                    + " BIGINT,tenant_id VARCHAR(20),version_no INT,definition_json"
                    + " CLOB,dependency_json CLOB,checksum VARCHAR(64),create_by BIGINT,create_time"
                    + " TIMESTAMP DEFAULT"
                    + " CURRENT_TIMESTAMP,UNIQUE(tenant_id,query_id,version_no))");
        var store = new ReportResourceStore(jdbc, new DataSourceTransactionManager(source));
        var actor = new ReportActor("A", 7, 10, true, List.of());
        var draft = store.create("QUERY", actor, "SAFE_QUERY", "查询", "{\"formatVersion\":1}");
        var old = store.publish("QUERY", actor, draft.id(), draft.revision(), "{}");
        var current = store.read("QUERY", "A", draft.id()).orElseThrow();
        store.save(
                "QUERY", actor, current.id(), current.revision(), "{\"formatVersion\":2}", "查询2");
        assertThrows(
                ReportFault.class,
                () -> store.save("QUERY", actor, current.id(), current.revision(), "{}", "覆盖"));
        assertTrue(store.read("QUERY", "B", draft.id()).isEmpty());
        assertEquals(
                "{\"formatVersion\":1}",
                store.version("QUERY", "A", old.id()).orElseThrow().json());
    }
}

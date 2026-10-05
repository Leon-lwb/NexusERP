package com.nexus.erp.wm;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.wm.mapper.WmCertifiedArrivalNoticeMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** 离线验证安装/增量接线，不连接数据库，不声称 SQL 已实际迁移。 */
class WmCertifiedArrivalSchemaTest {
    Path root(){Path p=Path.of("").toAbsolutePath();while(!Files.exists(p.resolve("script/sql/ry-erp.sql")))p=p.getParent();return p;}
    @Test void freshInstallContainsRealCommandAndNullableReferences() throws Exception {
        String s=Files.readString(root().resolve("script/sql/ry-erp.sql"));
        assertTrue(s.contains("CREATE TABLE `wm_certified_arrival_command`"));
        assertTrue(s.contains("`notice_line_id` bigint(20) NULL DEFAULT NULL"));
    }
    @Test void incrementIsReplayableWithoutUpgradingHistory() throws Exception {
        Path p=root().resolve("script/sql/update/certified-arrival-receipt-link.sql");assertTrue(Files.exists(p));String s=Files.readString(p);
        assertTrue(s.contains("information_schema.COLUMNS"));assertTrue(s.contains("CREATE TABLE IF NOT EXISTS `wm_certified_arrival_command`"));
        assertFalse(s.toUpperCase().contains("UPDATE WM_MATERIAL_RECEIVE"));assertFalse(s.contains("ADD COLUMN IF NOT EXISTS"));
        assertTrue(s.contains("SIGNAL SQLSTATE '45000'"));
    }
    @Test void schemaQueriesDoNotInjectBusinessTenantIntoInformationSchema() throws Exception {
        for(String n:new String[]{"tableEngines","decimalColumns"}){
            var a=WmCertifiedArrivalNoticeMapper.class.getMethod(n).getAnnotation(InterceptorIgnore.class);
            assertNotNull(a,"元数据不是业务租户表");assertEquals("true",a.tenantLine());
        }
    }
}

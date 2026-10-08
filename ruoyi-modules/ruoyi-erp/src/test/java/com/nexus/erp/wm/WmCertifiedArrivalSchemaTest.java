package com.nexus.erp.wm;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.wm.mapper.WmCertifiedArrivalNoticeMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 离线验证新装结构接线，不连接数据库，不声称 SQL 已实际迁移。 */
class WmCertifiedArrivalSchemaTest {
    Path root(){Path p=Path.of("").toAbsolutePath();while(p!=null&&!Files.isRegularFile(p.resolve("script/sql/ry-erp.sql")))p=p.getParent();assertNotNull(p);return p;}
    String definition(String table) throws Exception {
        var ddl=Pattern.compile("(?is)CREATE\\s+TABLE\\s+`"+Pattern.quote(table)+"`\\s*\\(.*?\\)\\s*ENGINE[^;]*;")
            .matcher(Files.readString(root().resolve("script/sql/ry-erp.sql")));
        assertTrue(ddl.find(),"Missing installation DDL: "+table);
        return ddl.group();
    }
    @Test void freshInstallContainsRealCommandAndNullableReferences() throws Exception {
        String command=definition("wm_certified_arrival_command");
        assertTrue(command.contains("`notice_id` bigint(20) NOT NULL"));
        assertTrue(command.contains("`notice_line_id` bigint(20) NOT NULL"));
        String head=definition("wm_material_receive");
        assertTrue(head.contains("`notice_id` bigint(20) NULL DEFAULT NULL"));
        assertTrue(Pattern.compile("(?im)^\\s*`notice_code`\\s+varchar\\(64\\)(?:\\s+CHARACTER SET\\s+\\w+)?(?:\\s+COLLATE\\s+\\w+)?\\s+NULL\\s+DEFAULT\\s+NULL").matcher(head).find());
        String line=definition("wm_material_receive_line");
        for(String column:List.of("source_doc_line_id","purchase_line_id","notice_line_id"))
            assertTrue(line.contains("`"+column+"` bigint(20) NULL DEFAULT NULL"),column);
    }
    @Test void installationKeepsPermanentTenantRequestAndNoticeIdentities() throws Exception {
        String command=definition("wm_certified_arrival_command");
        assertTrue(command.contains("`tenant_id` varchar(20) NOT NULL"));
        assertTrue(command.contains("`request_key` varchar(128) NOT NULL"));
        assertTrue(command.contains("PRIMARY KEY (`tenant_id`,`request_key`)"));
        assertTrue(command.contains("UNIQUE KEY `uk_wm_certified_arrival` (`tenant_id`,`notice_id`)"));
        assertTrue(command.contains("UNIQUE KEY `uk_wm_certified_arrival_line` (`tenant_id`,`notice_line_id`)"));
        assertTrue(command.contains("KEY `idx_wm_certified_arrival_source` (`tenant_id`,`order_id`,`purchase_line_id`)"));
        assertTrue(command.contains("`quantity` decimal(18,4) NOT NULL"));
        assertTrue(command.contains("`create_time` datetime(3) NOT NULL"));
        assertTrue(Pattern.compile("ENGINE\\s*=\\s*InnoDB").matcher(command).find());
    }
    @Test void schemaQueriesDoNotInjectBusinessTenantIntoInformationSchema() throws Exception {
        for(String n:new String[]{"tableEngines","decimalColumns"}){
            var a=WmCertifiedArrivalNoticeMapper.class.getMethod(n).getAnnotation(InterceptorIgnore.class);
            assertNotNull(a,"元数据不是业务租户表");assertEquals("true",a.tenantLine());
        }
    }
}

package com.nexus.erp.wm.trace;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ErpDocumentCodeResolverTest {
    @Test void documentCodeNeedsExistingPermissionAndTenantAndExplicitContextMustMatch(){
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:doc"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE wm_barcode(barcode_id BIGINT,tenant_id VARCHAR(20),barcode_code VARCHAR(128),primary_object_type VARCHAR(16),material_id BIGINT,batch_id BIGINT,sn_id BIGINT,stock_id BIGINT,biz_type VARCHAR(64),biz_id BIGINT,biz_line_id BIGINT,status VARCHAR(16),expire_time TIMESTAMP,del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_material_receive(receive_id BIGINT,tenant_id VARCHAR(20),receive_code VARCHAR(128),del_flag CHAR)");
        jdbc.update("INSERT INTO wm_material_receive VALUES(1,'000001','CUSTOM-RECEIPT','0'),(2,'000002','CUSTOM-RECEIPT','0')");
        var service=new ErpDocumentCodeResolver(jdbc,mock(ErpTraceContext.class));
        try(var auth=mockStatic(StpUtil.class)){
            auth.when(StpUtil::isLogin).thenReturn(true);
            var request=new CodeResolveRequest("CUSTOM-RECEIPT","RECEIVE",null,null,null,"WM_MATERIAL_RECEIVE",null,null);
            assertTrue(service.resolve("000001",request).isEmpty());
            auth.when(()->StpUtil.hasPermission("wm:receive:query")).thenReturn(true);
            var result=service.resolve("000001",request);assertEquals(1,result.size());assertEquals(1L,result.get(0).objectId());assertNull(result.get(0).identityId());
            assertTrue(service.resolve("000001",new CodeResolveRequest("CUSTOM-RECEIPT","RECEIVE",null,null,null,"WM_MATERIAL_RECEIVE",2L,null)).isEmpty());
        }
    }
}

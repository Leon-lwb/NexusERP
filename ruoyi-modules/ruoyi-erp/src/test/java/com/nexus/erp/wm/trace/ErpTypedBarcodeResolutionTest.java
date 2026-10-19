package com.nexus.erp.wm.trace;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ErpTypedBarcodeResolutionTest {
    JdbcTemplate jdbc;ErpCodeResolverService resolver;
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:typedcarrier"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        resolver=new ErpCodeResolverService(jdbc,mock(ErpTraceContext.class));
        jdbc.execute("CREATE TABLE bd_material(tenant_id VARCHAR(20),material_id BIGINT,material_code VARCHAR(64),del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_sn(tenant_id VARCHAR(20),sn_id BIGINT,sn_code VARCHAR(128),material_id BIGINT,material_code VARCHAR(64),batch_id BIGINT,batch_code VARCHAR(64),del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_batch(tenant_id VARCHAR(20),batch_id BIGINT,batch_code VARCHAR(64),material_id BIGINT,material_code VARCHAR(64),del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_barcode(tenant_id VARCHAR(20),barcode_code VARCHAR(128),primary_object_type VARCHAR(16),sn_id BIGINT,material_id BIGINT,batch_id BIGINT,status VARCHAR(16),expire_time TIMESTAMP,del_flag CHAR)");
        jdbc.update("INSERT INTO bd_material VALUES('000001',1,'M1','0')");jdbc.update("INSERT INTO wm_sn VALUES('000001',10,'SN10',1,'M1',20,'B20','0')");jdbc.update("INSERT INTO wm_batch VALUES('000001',20,'B20',1,'M1','0')");
    }
    CodeResolution carrier(String type){jdbc.update("INSERT INTO wm_barcode(tenant_id,barcode_code,primary_object_type,sn_id,material_id,batch_id,status,expire_time,del_flag) VALUES('000001','CARRIER',?,10,1,20,'ACTIVE',NULL,'0')",type);return resolver.resolve("000001",new CodeResolveRequest("CARRIER","QUERY",null,null,null,null,null,null),Set.of(CodeObjectType.SN,CodeObjectType.BATCH,CodeObjectType.MATERIAL));}
    @Test void materialPrimaryDoesNotBecomeSerialBecauseItsContextContainsOne(){var r=carrier("MATERIAL");assertEquals(CodeObjectType.MATERIAL,r.objectType());assertNull(r.identityId());}
    @Test void batchPrimaryDoesNotBecomeSerialBecauseItsContextContainsOne(){var r=carrier("BATCH");assertEquals(CodeObjectType.BATCH,r.objectType());assertNull(r.identityId());assertEquals(20L,r.batchId());}
    @Test void anUnsupportedStoredPrimaryTypeMustNotSilentlyChooseTheSerial(){assertEquals(ResolveStatus.INVALID,carrier("STOCK").status());}
    @Test void aDocumentCarrierCannotChooseItsSerialContextByPrecedence(){assertNotEquals(ResolveStatus.RESOLVED,carrier("DOCUMENT").status());}
    @Test void legacyNullPrimaryKeepsItsOriginalReaderCompatibility(){assertEquals(10L,carrier(null).identityId());}
    ErpDocumentCodeResolver documents(){
        jdbc.execute("ALTER TABLE wm_barcode ADD biz_type VARCHAR(64)");jdbc.execute("ALTER TABLE wm_barcode ADD biz_id BIGINT");jdbc.execute("ALTER TABLE wm_barcode ADD biz_line_id BIGINT");
        jdbc.execute("CREATE TABLE wm_material_receive(tenant_id VARCHAR(20),receive_id BIGINT,receive_code VARCHAR(64),del_flag CHAR)");jdbc.update("INSERT INTO wm_material_receive VALUES('000001',40,'RC40','0')");
        jdbc.update("INSERT INTO wm_barcode(tenant_id,barcode_code,primary_object_type,biz_type,biz_id,status,del_flag) VALUES('000001','DOCUMENT-CARRIER','DOCUMENT','WM_MATERIAL_RECEIVE',40,'ACTIVE','0')");
        return new ErpDocumentCodeResolver(jdbc,mock(ErpTraceContext.class));
    }
    @Test void aDocumentCarrierResolvesItsAuthorizedActualDocument(){
        var docs=documents();try(var auth=mockStatic(cn.dev33.satoken.stp.StpUtil.class)){
            auth.when(cn.dev33.satoken.stp.StpUtil::isLogin).thenReturn(true);auth.when(()->cn.dev33.satoken.stp.StpUtil.hasPermission("wm:receive:query")).thenReturn(true);
            var result=docs.resolve("000001",new CodeResolveRequest("DOCUMENT-CARRIER","QUERY",null,null,null,"WM_MATERIAL_RECEIVE",null,null));
            assertEquals(1,result.size());assertEquals(40L,result.get(0).objectId());assertEquals("RC40",result.get(0).documentCode());
        }
    }
    @Test void aDocumentCarrierNeverBypassesItsObjectPermission(){
        var docs=documents();try(var auth=mockStatic(cn.dev33.satoken.stp.StpUtil.class)){
            auth.when(cn.dev33.satoken.stp.StpUtil::isLogin).thenReturn(true);assertTrue(docs.resolve("000001",new CodeResolveRequest("DOCUMENT-CARRIER","QUERY",null,null,null,"WM_MATERIAL_RECEIVE",null,null)).isEmpty());
        }
    }

    CodeResolution gs1(String serial){return resolver.resolve("000001",new CodeResolveRequest("]C1010950600013435221"+serial,"QUERY",null,null,null,null,null,null),Set.of(CodeObjectType.SN));}
    @Test void anUnknownPrimaryCannotBecomeAGtinMaterialAssertion(){
        jdbc.update("INSERT INTO wm_barcode(tenant_id,barcode_code,primary_object_type,sn_id,material_id,batch_id,status,del_flag) VALUES('000001','09506000134352','STOCK',10,1,20,'ACTIVE','0')");
        assertEquals(ResolveStatus.INVALID,gs1("SN10").status());
    }
    @Test void aSerialPrimaryMaterialContextDoesNotRegisterThatCodeAsAGtin(){
        jdbc.update("INSERT INTO wm_barcode(tenant_id,barcode_code,primary_object_type,sn_id,material_id,batch_id,status,del_flag) VALUES('000001','09506000134352','SN',10,1,20,'ACTIVE','0')");
        assertEquals(ResolveStatus.INVALID,gs1("SN10").status());
    }
    @Test void aGtinMaterialCarrierMustValidateAllItsStoredIdentityAssertions(){
        jdbc.update("INSERT INTO bd_material VALUES('000001',2,'M2','0')");jdbc.update("INSERT INTO wm_sn VALUES('000001',11,'SN11',2,'M2',NULL,NULL,'0')");
        jdbc.update("INSERT INTO wm_barcode(tenant_id,barcode_code,primary_object_type,sn_id,material_id,batch_id,status,del_flag) VALUES('000001','09506000134352','MATERIAL',10,2,20,'ACTIVE','0')");
        assertEquals(ResolveStatus.INVALID,gs1("SN11").status());
    }
    @Test void anExplicitValidMaterialPrimaryCanBeUsedForAGtinAssertion(){
        jdbc.update("INSERT INTO wm_barcode(tenant_id,barcode_code,primary_object_type,material_id,status,del_flag) VALUES('000001','09506000134352','MATERIAL',1,'ACTIVE','0')");
        assertEquals(10L,gs1("SN10").identityId());
    }

}

package com.nexus.erp.wm.trace;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ErpCodeResolverAssertionsTest {
    JdbcTemplate jdbc;ErpCodeResolverService resolver;
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:gs1"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));resolver=new ErpCodeResolverService(jdbc,mock(ErpTraceContext.class));
        jdbc.execute("CREATE TABLE bd_material(material_id BIGINT,tenant_id VARCHAR(20),material_code VARCHAR(128),del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_sn(sn_id BIGINT,tenant_id VARCHAR(20),sn_code VARCHAR(128),material_id BIGINT,material_code VARCHAR(128),batch_id BIGINT,batch_code VARCHAR(128),del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_sn_alias(alias_id BIGINT,tenant_id VARCHAR(20),identity_id BIGINT,alias_code VARCHAR(128),material_id BIGINT,source_type VARCHAR(32),source_id BIGINT)");
        jdbc.execute("CREATE TABLE wm_barcode(barcode_id BIGINT,tenant_id VARCHAR(20),barcode_code VARCHAR(128),material_id BIGINT,batch_id BIGINT,sn_id BIGINT,status VARCHAR(16),expire_time TIMESTAMP,del_flag CHAR)");
        jdbc.update("INSERT INTO bd_material VALUES(1,'000001','M1','0'),(2,'000001','M2','0')");jdbc.update("INSERT INTO wm_sn VALUES(1,'000001','SN1',1,'M1',NULL,NULL,'0')");
    }
    CodeResolution resolve(String raw){return resolver.resolve("000001",new CodeResolveRequest(raw,"QUERY",null,null,null,null,null,null),Set.of(CodeObjectType.SN));}
    @Test void aGs1BatchAssertionCannotAttachToAnUnboundSerial(){assertEquals(ResolveStatus.INVALID,resolve("(10)B1(21)SN1").status());}
    @Test void unregisteredGtinCannotBeSilentlyIgnoredWhenTheSerialExists(){assertEquals(ResolveStatus.INVALID,resolve("(01)09506000134352(21)SN1").status());}
    @Test void ambiguousGtinMappingStillRestrictsSerialsToItsMappedMaterials(){
        jdbc.update("INSERT INTO wm_barcode VALUES(1,'000001','09506000134352',2,NULL,NULL,'ACTIVE',NULL,'0'),(2,'000001','09506000134352',3,NULL,NULL,'ACTIVE',NULL,'0')");
        assertEquals(ResolveStatus.INVALID,resolve("(01)09506000134352(21)SN1").status());
    }
    @Test void matchingGtinAndBatchAssertionsPreserveTheSameFormalIdentity(){
        jdbc.update("INSERT INTO wm_barcode VALUES(1,'000001','09506000134352',1,NULL,NULL,'ACTIVE',NULL,'0')");jdbc.update("UPDATE wm_sn SET batch_id=10,batch_code='B1'");
        assertEquals(1L,resolve("(01)09506000134352(10)B1(21)SN1").identityId());
    }
    @Test void aFormalNumericSerialStartingWithAi01IsNotLostToAnUnmarkedGs1Guess(){
        jdbc.update("UPDATE wm_sn SET sn_code='0120261019000001'");
        assertEquals(1L,resolve("0120261019000001").identityId());
    }
    @Test void unmarkedGs1StillResolvesWhenThereIsNoLiteralIdentity(){
        jdbc.update("INSERT INTO wm_barcode VALUES(1,'000001','09506000134352',1,NULL,NULL,'ACTIVE',NULL,'0')");
        assertEquals(1L,resolve("010950600013435221SN1").identityId());
    }
    @Test void validLiteralAndUnmarkedGs1InterpretationsNeverGetAPrefixPriority(){
        jdbc.update("UPDATE wm_sn SET sn_code='0109506000134352'");jdbc.update("INSERT INTO wm_barcode VALUES(1,'000001','09506000134352',1,NULL,NULL,'ACTIVE',NULL,'0')");
        var result=resolver.resolve("000001",new CodeResolveRequest("0109506000134352","QUERY",null,null,null,null,null,null),Set.of(CodeObjectType.SN,CodeObjectType.MATERIAL));
        assertEquals(ResolveStatus.AMBIGUOUS,result.status());assertNull(result.identityId());
    }
    @Test void anOverfullAliasProbeCannotBecomeAFalseUniqueResultAfterPermissionFiltering(){
        jdbc.update("INSERT INTO wm_sn_alias VALUES(1,'000001',1,'REUSED',1,'SUPPLIER',20)");
        for(long id=2;id<=102;id++){jdbc.update("INSERT INTO wm_sn VALUES(?,'000001',?,1,'M1',NULL,NULL,'0')",id,"SN"+id);jdbc.update("INSERT INTO wm_sn_alias VALUES(?,'000001',?,'REUSED',1,'SUPPLIER',20)",id,id);}
        assertEquals(ResolveStatus.INVALID,resolve("REUSED").status());
    }

    @Test void anExpiredLiteralCarrierCannotBeBypassedByAnInferredGs1Interpretation(){
        jdbc.update("INSERT INTO wm_barcode VALUES(1,'000001','09506000134352',1,NULL,NULL,'ACTIVE',NULL,'0'),(2,'000001','010950600013435221SN1',1,NULL,1,'INACTIVE',NULL,'0')");
        assertEquals(ResolveStatus.INVALID,resolve("010950600013435221SN1").status());
    }
    @Test void anOverfullGtinMappingCannotSilentlyExcludeAnAssertionCandidate(){
        for(long id=1;id<=101;id++)jdbc.update("INSERT INTO wm_barcode VALUES(?,'000001','09506000134352',?,NULL,NULL,'ACTIVE',NULL,'0')",id,id);
        assertEquals(ResolveStatus.INVALID,resolve("(01)09506000134352(21)SN1").status());
    }

}

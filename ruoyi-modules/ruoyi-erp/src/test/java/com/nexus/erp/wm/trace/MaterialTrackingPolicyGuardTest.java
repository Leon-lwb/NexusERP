package com.nexus.erp.wm.trace;
import com.nexus.common.trace.port.MaterialTrackingUsagePort;
import com.nexus.erp.bd.domain.BdMaterial;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaterialTrackingPolicyGuardTest {
    JdbcTemplate jdbc;MaterialTrackingPolicyGuard guard;MaterialTrackingUsagePort mes;
    BdMaterial material(String stage){var m=new BdMaterial();m.setMaterialId(1L);m.setTrackingVersion(1L);m.setTrackingStage(stage);m.setIsBatchManage(0L);m.setIsSerialManage(1L);m.setAllowMixedLot(1);return m;}
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:policy"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id VARCHAR(20),material_id BIGINT,sn_id BIGINT,sn_code VARCHAR(128),warehouse_id BIGINT,location_id BIGINT,batch_id BIGINT,stock_quantity DECIMAL(18,4),reserved_quantity DECIMAL(18,4),total_cost DECIMAL(18,4),tracking_version BIGINT,valuation_group_id BIGINT,del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_sn(sn_id BIGINT,tenant_id VARCHAR(20),material_id BIGINT,sn_code VARCHAR(128),stock_id BIGINT,warehouse_id BIGINT,location_id BIGINT,status VARCHAR(16),del_flag CHAR)");
        jdbc.execute("CREATE TABLE wm_valuation_group(valuation_group_id BIGINT,tenant_id VARCHAR(20),material_id BIGINT,warehouse_id BIGINT,batch_id BIGINT,quantity DECIMAL(18,4),amount DECIMAL(18,4))");
        jdbc.update("INSERT INTO wm_material_stock VALUES(10,'000001',1,20,'SN20',2,3,NULL,1,0,10,2,30,'0')");jdbc.update("INSERT INTO wm_sn VALUES(20,'000001',1,'SN20',10,2,3,'IN_STOCK','0')");jdbc.update("INSERT INTO wm_valuation_group VALUES(30,'000001',1,2,NULL,1,10)");
        jdbc.execute("ALTER TABLE wm_sn ADD batch_id BIGINT");jdbc.execute("ALTER TABLE wm_sn ADD batch_code VARCHAR(128)");jdbc.execute("ALTER TABLE wm_material_stock ADD batch_code VARCHAR(128)");
        var context=mock(ErpTraceContext.class);when(context.tenant()).thenReturn("000001");
        @SuppressWarnings("unchecked") ObjectProvider<MaterialTrackingUsagePort> provider=mock(ObjectProvider.class);mes=mock(MaterialTrackingUsagePort.class);when(provider.getIfAvailable()).thenReturn(mes);
        guard=new MaterialTrackingPolicyGuard(jdbc,context,provider);ReflectionTestUtils.setField(guard,"relayEnabled",true);
    }
    @Test void auditedUnitStockWithOriginalValuationBalanceCanEnterStrict(){var next=material("STRICT");assertDoesNotThrow(()->guard.prepare(material("AUDIT"),next,1L));assertEquals(2L,next.getTrackingVersion());verify(mes).assertReadyForStrict("000001",1L);}
    @Test void aMissingOrUnbalancedCostGroupCannotBeEnabledAsStrict(){jdbc.update("UPDATE wm_valuation_group SET amount=9");assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),1L));verifyNoInteractions(mes);}
    @Test void registrationWithoutRealStockStateCannotPretendToBeAnAuditedUnit(){jdbc.update("UPDATE wm_sn SET status='REGISTERED'");assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),1L));}
    @Test void stalePolicyCannotOverwriteAnyVersionedDecision(){assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),2L));}
    @Test void aNullIdentityPositionCannotMatchARealWarehouse(){jdbc.update("UPDATE wm_sn SET warehouse_id=NULL");assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),1L));}
    @Test void anInStockIdentityWithoutAPositiveStockRowCannotPassCutover(){
        jdbc.update("INSERT INTO wm_sn(sn_id,tenant_id,material_id,sn_code,stock_id,warehouse_id,location_id,status,del_flag) VALUES(21,'000001',1,'SN21',999,2,3,'IN_STOCK','0')");
        assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),1L));
    }

    @Test void registeredIdentitiesDoNotRequireStockAndOtherTenantsStayIsolated(){
        jdbc.update("INSERT INTO wm_sn(sn_id,tenant_id,material_id,sn_code,status,del_flag) VALUES(21,'000001',1,'REGISTERED21','REGISTERED','0'),(22,'000002',1,'OTHER22','IN_STOCK','0')");
        assertDoesNotThrow(()->guard.prepare(material("AUDIT"),material("STRICT"),1L));
    }

    @Test void aZeroStockRowCannotHideUnallocatedBookValueAtCutover(){
        jdbc.update("INSERT INTO wm_material_stock(stock_id,tenant_id,material_id,stock_quantity,reserved_quantity,total_cost,tracking_version,del_flag) VALUES(11,'000001',1,0,0,5,1,'0')");
        assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),1L));
    }
    @Test void aValuationGroupWithoutStockCannotHideOutstandingQuantityOrValue(){
        jdbc.update("INSERT INTO wm_valuation_group VALUES(31,'000001',1,2,NULL,1,5)");
        assertThrows(RuntimeException.class,()->guard.prepare(material("AUDIT"),material("STRICT"),1L));
    }

    @Test void aFullyDepletedHistoricCostGroupDoesNotPreventCutover(){
        jdbc.update("INSERT INTO wm_valuation_group VALUES(31,'000001',1,2,NULL,0,0)");
        assertDoesNotThrow(()->guard.prepare(material("AUDIT"),material("STRICT"),1L));
    }

}

package com.nexus.erp.wm.trace;

import com.nexus.erp.wm.domain.WmBarcode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WmBarcodeIdentityGuardTest {
    JdbcTemplate jdbc;
    WmBarcodeIdentityGuard guard;

    @BeforeEach
    void setup() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:carrier"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        var context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("000001");
        guard = new WmBarcodeIdentityGuard(jdbc, context);
        jdbc.execute(
                "CREATE TABLE bd_material(tenant_id VARCHAR(20),material_id BIGINT,del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_sn(tenant_id VARCHAR(20),sn_id BIGINT,material_id BIGINT,batch_id"
                        + " BIGINT,batch_code VARCHAR(64),stock_id BIGINT,del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_batch(tenant_id VARCHAR(20),batch_id BIGINT,material_id"
                        + " BIGINT,batch_code VARCHAR(64),del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_material_stock(tenant_id VARCHAR(20),stock_id BIGINT,material_id"
                    + " BIGINT,sn_id BIGINT,batch_id BIGINT,stock_quantity DECIMAL(18,4),del_flag"
                    + " CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_barcode(barcode_id BIGINT,tenant_id VARCHAR(20),barcode_code"
                    + " VARCHAR(128),primary_object_type VARCHAR(16),material_id BIGINT,batch_id"
                    + " BIGINT,batch_code VARCHAR(64),sn_id BIGINT,stock_id BIGINT,status"
                    + " VARCHAR(16),biz_type VARCHAR(64),biz_id BIGINT,biz_line_id BIGINT,config_id"
                    + " BIGINT,config_code VARCHAR(64),generate_time TIMESTAMP,bind_time"
                    + " TIMESTAMP,expire_time TIMESTAMP,remark VARCHAR(500),del_flag CHAR)");
        jdbc.execute(
                "CREATE TABLE wm_material_receive(tenant_id VARCHAR(20),receive_id"
                        + " BIGINT,receive_code VARCHAR(64),del_flag CHAR)");
        jdbc.update("INSERT INTO bd_material VALUES('000001',1,'0'),('000001',2,'0')");
        jdbc.update(
                "INSERT INTO wm_sn"
                        + " VALUES('000001',10,1,20,'B20',30,'0'),('000002',11,1,20,'B20',31,'0')");
        jdbc.update(
                "INSERT INTO wm_batch VALUES('000001',20,1,'B20','0'),('000001',21,2,'B21','0')");
        jdbc.update(
                "INSERT INTO wm_material_stock"
                        + " VALUES('000001',30,1,10,20,1,'0'),('000001',31,2,NULL,21,1,'0')");
        jdbc.update("INSERT INTO wm_material_receive VALUES('000001',40,'RC40','0')");
    }

    WmBarcode value(String type) {
        var value = new WmBarcode();
        value.setBarcodeCode("CARRIER");
        value.setPrimaryObjectType(type);
        return value;
    }

    @Test
    void aNewCarrierRequiresOneExplicitPrimaryObject() {
        var v = value(null);
        v.setSnId(10L);
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }

    @Test
    void aSerialCarrierFreezesTheActualMaterialAndBatchContext() {
        var v = value("SN");
        v.setSnId(10L);
        v.setStockId(30L);
        guard.prepare(v, false);
        assertEquals(1L, v.getMaterialId());
        assertEquals(20L, v.getBatchId());
        assertEquals("B20", v.getBatchCode());
        assertEquals("ACTIVE", v.getStatus());
    }

    @Test
    void aMismatchedStockReferenceCannotRebindThePhysicalIdentity() {
        var v = value("SN");
        v.setSnId(10L);
        v.setStockId(31L);
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }

    @Test
    void anotherTenantsSerialCannotBecomeACarrierSubject() {
        var v = value("SN");
        v.setSnId(11L);
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }

    @Test
    void materialCanBeThePrimaryObjectWhileASerialIsOnlyAnAssertion() {
        var v = value("MATERIAL");
        v.setMaterialId(1L);
        v.setSnId(10L);
        guard.prepare(v, false);
        assertEquals("MATERIAL", v.getPrimaryObjectType());
    }

    @Test
    void theBatchCodeAssertionMustMatchItsActualIdentifier() {
        var v = value("BATCH");
        v.setBatchId(20L);
        v.setBatchCode("B21");
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }

    @Test
    void aStockReferenceAloneDoesNotDefineAStableSubject() {
        var v = value(null);
        v.setStockId(30L);
        assertThrows(RuntimeException.class, () -> guard.prepare(v, true));
    }

    @Test
    void aLegacyGenerateAdapterMakesItsChoiceExplicitAtCreation() {
        var v = value(null);
        v.setSnId(10L);
        v.setMaterialId(1L);
        guard.prepare(v, true);
        assertEquals("SN", v.getPrimaryObjectType());
    }

    @Test
    void anExistingCodeCannotBeReusedAfterRevocation() {
        jdbc.update(
                "INSERT INTO wm_barcode(barcode_id,tenant_id,barcode_code,status,del_flag)"
                        + " VALUES(1,'000001','CARRIER','INACTIVE','0')");
        var v = value("MATERIAL");
        v.setMaterialId(1L);
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }

    @Test
    void documentSubjectUsesTheServerOwnedTableCatalog() {
        var v = value("DOCUMENT");
        v.setBizType("WM_MATERIAL_RECEIVE");
        v.setBizId(40L);
        guard.prepare(v, false);
        assertEquals("DOCUMENT", v.getPrimaryObjectType());
        v.setBizType("sys_user");
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }

    @Test
    void existingCarrierIdentityAndOriginalTextCannotBeEdited() {
        var old = value("SN");
        old.setBarcodeId(1L);
        old.setSnId(10L);
        var changed = value("SN");
        changed.setBarcodeId(1L);
        changed.setSnId(11L);
        assertThrows(RuntimeException.class, () -> guard.assertImmutable(old, changed));
        changed.setSnId(10L);
        changed.setBarcodeCode("NEW");
        assertThrows(RuntimeException.class, () -> guard.assertImmutable(old, changed));
    }

    @Test
    void legacyBindingIsPreservedWhileMetadataCanBeMaintained() {
        var old = value(null);
        old.setBarcodeId(1L);
        old.setMaterialId(1L);
        var update = new WmBarcode();
        update.setBarcodeId(1L);
        update.setStatus("EXPIRED");
        update.setRemark("原标签停用");
        assertDoesNotThrow(() -> guard.assertImmutable(old, update));
        update.setPrimaryObjectType("MATERIAL");
        assertThrows(RuntimeException.class, () -> guard.assertImmutable(old, update));
    }

    @Test
    void aDocumentCarrierCannotPersistAnUnverifiedLineAssertion() {
        var v = value("DOCUMENT");
        v.setBizType("WM_MATERIAL_RECEIVE");
        v.setBizId(40L);
        v.setBizLineId(999L);
        assertThrows(RuntimeException.class, () -> guard.prepare(v, false));
    }
}

package com.nexus.erp.wm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original certified receipt, permanent proof and persisted H2 facts survive a supported available-stock cutover. */
class WmCertifiedReceiptMigrationReplayTest {
    @ParameterizedTest @ValueSource(strings={"AUDIT","STRICT"})
    void convertedV1CertifiedReceiptReplaysOriginalProofWithoutReadingOrCreditingSnPieces(String stage) throws Exception {
        try(var f=new Fixture()) {
            f.convert(stage);String before=f.state();f.clear();
            var replay=f.original.service.readPostedLocked(f.original.TENANT,f.original.ID).result();
            assertTrue(replay.replay());assertEquals(f.original.posting.sourceFingerprint(),replay.sourceFingerprint());
            assertEquals(before,f.state());f.noEffects();
        }
    }
    @ParameterizedTest @ValueSource(strings={"DELETE FROM original_fact","UPDATE original_fact SET tx_json=REPLACE(tx_json,'4.0000','5.0000')","UPDATE permanent_receipt SET source_fingerprint=REPEAT('b',64)"})
    void conversionDoesNotMakeMissingOrChangedOriginalCertifiedProofAcceptable(String sql) throws Exception {
        try(var f=new Fixture()) {f.convert("STRICT");f.jdbc.update(sql);f.clear();assertThrows(RuntimeException.class,()->f.original.service.readPostedLocked(f.original.TENANT,f.original.ID));f.noEffects();}
    }
    static final class Fixture implements AutoCloseable {
        final WmCertifiedReceiptPostingServiceTest original=new WmCertifiedReceiptPostingServiceTest();
        final JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:certcutover"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        final ObjectMapper json=new ObjectMapper();
        Fixture() throws Exception {
            try {
                original.setup();jdbc.execute("CREATE TABLE bd_material(material_id BIGINT,tenant_id VARCHAR(20),status BIGINT,del_flag CHAR(1),is_batch_manage BIGINT,is_serial_manage BIGINT,allow_mixed_lot INT,tracking_version BIGINT,tracking_stage VARCHAR(16))");
                jdbc.update("INSERT INTO bd_material VALUES(201,'000000',1,'0',1,0,1,1,'AUDIT')");
                jdbc.execute("CREATE TABLE wm_trace_posting_group(tenant_id VARCHAR(20),source_doc_type VARCHAR(64),source_doc_id BIGINT,status VARCHAR(24))");
                jdbc.execute("CREATE TABLE wm_material_stock(stock_id BIGINT PRIMARY KEY,sn_id BIGINT,stock_quantity DECIMAL(18,4),reserved_quantity DECIMAL(18,4),total_cost DECIMAL(18,4),warehouse_id BIGINT,tracking_version BIGINT)");
                jdbc.execute("CREATE TABLE original_fact(tx_id BIGINT PRIMARY KEY,tx_json VARCHAR(10000))");
                jdbc.execute("CREATE TABLE permanent_receipt(source_fingerprint VARCHAR(64),quantity DECIMAL(18,4),posted_at VARCHAR(64),notification_status VARCHAR(32))");
                var context=mock(ErpTraceContext.class);when(context.tenant()).thenReturn(original.TENANT);
                var inventory=new WmSerialInventoryService(new WmSerialInventoryRepository(jdbc,mock(WmMaterialStockMapper.class),mock(WmMaterialStockTxMapper.class),context,json));
                ReflectionTestUtils.setField(original.service,"serialInventory",inventory);
                original.service.post(original.ID);for(var tx:original.transactions)jdbc.update("INSERT INTO original_fact VALUES(?,?)",tx.txId(),json.writeValueAsString(tx));
                jdbc.update("INSERT INTO permanent_receipt VALUES(?,?,?,?)",original.posting.sourceFingerprint(),original.Q,original.posting.postedAt(),original.posting.notificationStatus());
                when(original.mapper.lockPosting(original.TENANT,original.ID)).thenAnswer(a->jdbc.queryForObject("SELECT * FROM permanent_receipt",(rs,i)->new Posting(original.TENANT,original.ID,original.MATERIAL,9L,rs.getString(1),rs.getBigDecimal(2),rs.getString(3),rs.getString(4))));
                when(original.mapper.lockReceiptTransactions(original.TENANT,original.ID)).thenAnswer(a->jdbc.query("SELECT tx_json FROM original_fact ORDER BY tx_id",(rs,i)->{try{return json.readValue(rs.getString(1),PostedTx.class);}catch(Exception e){throw new IllegalStateException(e);}}));
                when(original.mapper.lockAllReceiptTransactionIds(original.TENANT,original.ID)).thenAnswer(a->jdbc.queryForList("SELECT tx_id FROM original_fact ORDER BY tx_id",Long.class));
                when(original.locks.lockExisting(original.TENANT,List.of(original.MATERIAL))).thenReturn(Map.of(original.MATERIAL,0));
            } catch(Exception|Error failure) {if(original.tenants!=null)original.close();throw failure;}
        }
        void convert(String stage) {
            var s=original.stocks.get(0);original.stocks=new ArrayList<>(List.of(new Stock(s.tenantId(),s.stockId(),s.materialId(),s.baseUomId(),s.warehouseId(),s.areaId(),s.locationId(),s.batchId(),null,BigDecimal.ZERO,BigDecimal.ZERO,s.version()+1,s.delFlag(),s.expiryAt(),s.unitCost(),BigDecimal.ZERO)));
            jdbc.update("INSERT INTO wm_material_stock VALUES(?,NULL,0,0,0,501,2)",s.stockId());
            for(int i=1;i<=4;i++)jdbc.update("INSERT INTO wm_material_stock VALUES(?,?,?,0,?,?,2)",200+i,300+i,i==1?0:1,i==1?0:10,i==2?901:501);
            for(int i=1;i<=4;i++)original.stocks.add(new Stock(s.tenantId(),200L+i,s.materialId(),s.baseUomId(),i==2?901L:501L,s.areaId(),s.locationId(),s.batchId(),300L+i,i==1?BigDecimal.ZERO:BigDecimal.ONE,BigDecimal.ZERO,1,"0",s.expiryAt(),s.unitCost(),i==1?BigDecimal.ZERO:BigDecimal.TEN));
            jdbc.update("UPDATE bd_material SET is_serial_manage=1,tracking_version=2,tracking_stage=?",stage);
        }
        String state() {return jdbc.queryForList("SELECT * FROM original_fact ORDER BY tx_id").toString()+jdbc.queryForList("SELECT * FROM permanent_receipt")+jdbc.queryForList("SELECT * FROM wm_material_stock ORDER BY stock_id");}
        void clear() {clearInvocations(original.mapper,original.notifications,original.orderWorkflow,original.iqc,original.lineage);}
        void noEffects() {
            verifyNoInteractions(original.notifications,original.lineage);
            verify(original.mapper,never()).finish(any(),any(),any(),any());
            verify(original.mapper,never()).insertStock(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
            verify(original.mapper,never()).insertTransaction(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
            verify(original.mapper,never()).recordPosting(any(),any(),any(),any(),any(),any(),any());
        }
        public void close() {original.close();}
    }
}

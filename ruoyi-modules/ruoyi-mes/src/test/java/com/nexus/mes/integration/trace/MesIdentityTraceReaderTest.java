package com.nexus.mes.integration.trace;

import com.nexus.mes.trace.MesTraceContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MesIdentityTraceReaderTest {
    JdbcTemplate jdbc;MesIdentityTraceReader reader;
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:recall"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        reader=new MesIdentityTraceReader(jdbc,mock(MesTraceContext.class),mock(com.nexus.common.trace.port.SerialIdentityPort.class));
        jdbc.execute("CREATE TABLE lt_wo_consumption(consumption_id BIGINT,tenant_id VARCHAR(20),wo_id BIGINT,material_id BIGINT,lot_id BIGINT,source_allocation_id BIGINT,consumed_qty DECIMAL(18,4),del_flag CHAR)");
        jdbc.execute("CREATE TABLE lt_sn_consumption(entry_id BIGINT,tenant_id VARCHAR(20),wo_consumption_id BIGINT,entry_type VARCHAR(20),quantity DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE lt_lot_allocation(allocation_id BIGINT,tenant_id VARCHAR(20),lot_id BIGINT,erp_batch_id BIGINT,evidence_json VARCHAR(4096),material_id BIGINT DEFAULT 8,quantity DECIMAL(18,4) DEFAULT 4,source_type VARCHAR(32),source_id BIGINT,source_line_id BIGINT,allocation_version BIGINT DEFAULT 1)");
        jdbc.execute("CREATE TABLE lt_lot(lot_id BIGINT,tenant_id VARCHAR(20),lot_code VARCHAR(128),quantity DECIMAL(18,4),consumed_qty DECIMAL(18,4),status VARCHAR(32),produced_date TIMESTAMP,expiry_date TIMESTAMP,del_flag CHAR)");
        jdbc.execute("CREATE TABLE pro_product_lot_output(output_id BIGINT,tenant_id VARCHAR(20),mes_wo_id BIGINT,source_allocation_id BIGINT)");
        jdbc.execute("CREATE TABLE pro_sn(identity_id BIGINT,tenant_id VARCHAR(20),mes_wo_id BIGINT,del_flag CHAR)");
        jdbc.update("INSERT INTO lt_wo_consumption VALUES(1,'000001',7,8,11,101,4,'0'),(2,'000001',7,8,12,102,1,'0'),(3,'000002',9,8,13,103,5,'0')");
        jdbc.update("INSERT INTO lt_sn_consumption VALUES(1,'000001',1,'CONSUME',2),(2,'000001',1,'REVERSE',1),(3,'000001',2,'CONSUME',1)");
        jdbc.update("INSERT INTO lt_lot_allocation(allocation_id,tenant_id,lot_id,erp_batch_id,evidence_json) VALUES(101,'000001',11,51,'{}'),(102,'000001',12,51,'{}'),(103,'000002',13,51,'{}'),(201,'000001',91,61,'{}'),(202,'000001',92,62,'{}')");
        jdbc.update("INSERT INTO pro_product_lot_output VALUES(401,'000001',7,201),(402,'000001',7,202),(403,'000002',7,103)");
    }
    @Test void actualUseMinusNetUnitAllocationRetainsWorkOrderScope(){
        var page=reader.unallocatedByBatch("000001",51L,0,500);assertEquals(1,page.rows().size());var impact=page.rows().get(0);
        assertEquals(1L,impact.consumptionId());assertEquals(7L,impact.mesWoId());assertEquals(0,new java.math.BigDecimal("3").compareTo(impact.unallocatedQuantity()));assertFalse(page.hasMore());
    }
    @Test void reversedAssignmentsReappearAsUnallocatedAndOtherTenantsNeverAppear(){
        jdbc.update("INSERT INTO lt_sn_consumption VALUES(4,'000001',2,'REVERSE',1)");
        assertEquals(List.of(1L,2L),reader.unallocatedByBatch("000001",51L,0,500).rows().stream().map(row->row.consumptionId()).toList());
        assertEquals(0,reader.unallocatedByBatch("000001",51L,2,500).rows().size());
    }
    @Test void batchOnlyProductsUseActualOutputSourceMappings(){assertEquals(List.of(61L,62L),reader.productBatchesByWorkOrder("000001",7L,0,500).rows());}
    @Test void workOrderProductsPageBeyondFiveHundredAndExcludeUnmappedOrDeletedUnits(){
        for(long id=1;id<=501;id++)jdbc.update("INSERT INTO pro_sn VALUES(?,'000001',7,'0')",id);
        jdbc.update("INSERT INTO pro_sn VALUES(999,'000002',7,'0'),(1000,'000001',7,'2'),(NULL,'000001',7,'0')");
        var first=reader.productIdentitiesByWorkOrder("000001",7L,0,500);assertEquals(500,first.rows().size());assertTrue(first.hasMore());assertEquals("500",first.nextCursor());
        assertEquals(List.of(501L),reader.productIdentitiesByWorkOrder("000001",7L,500,500).rows());
    }
    @Test void oneErpBatchReturnsEachEvidencedMesChildLotAndItsOwnAllocationQuota(){
        jdbc.update("INSERT INTO lt_lot(lot_id,tenant_id,lot_code,quantity,consumed_qty,status,del_flag) VALUES(11,'000001','L11',4,1,'IN_USE','0'),(12,'000001','L12',4,0,'IN_STOCK','0'),(13,'000002','L13',5,0,'IN_STOCK','0')");
        var first=reader.batch("000001",51L,"LOTS",null,1).sections().get("LOTS");assertTrue(first.hasMore());assertEquals("11",first.rows().get(0).get("lot_id"));
        var second=reader.batch("000001",51L,"LOTS",first.nextCursor(),1).sections().get("LOTS");assertEquals("12",second.rows().get(0).get("lot_id"));assertFalse(second.hasMore());
        assertThrows(RuntimeException.class,()->reader.batch("000001",52L,"LOTS",first.nextCursor(),1));
    }

}

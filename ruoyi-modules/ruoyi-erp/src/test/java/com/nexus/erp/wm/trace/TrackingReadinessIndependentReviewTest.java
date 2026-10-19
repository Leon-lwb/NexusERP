package com.nexus.erp.wm.trace;

import com.nexus.common.trace.port.MaterialTrackingUsagePort;
import com.nexus.erp.bd.domain.BdMaterial;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 直接验证就绪检查的实际查询，测试夹具和运行中业务数据保持独立。
 */
class TrackingReadinessIndependentReviewTest {
    @Test
    void fullyReconciledStockStillEntersStrict() {
        var f = new Fixture();
        assertDoesNotThrow(f::enable);
    }

    @Test
    void missingStockSerialCodeMustNotPassSqlNullComparison() {
        var f = new Fixture();
        f.jdbc.update("UPDATE wm_material_stock SET sn_code=NULL");
        assertThrows(RuntimeException.class, f::enable);
    }

    @Test
    void registeredSerialBatchMustMatchTheStockBatchBeforeStrict() {
        var f = new Fixture();
        f.jdbc.update("UPDATE wm_sn SET batch_id=6,batch_code='B6'");
        assertThrows(RuntimeException.class, f::enable);
    }

    @Test
    void zeroHistoricRowCannotRetainOneReservedPieceAtStrictCutover() {
        var f = new Fixture();
        f.jdbc.update(
                "INSERT INTO wm_material_stock"
                        + " VALUES(11,'000001',1,20,'SN20',2,3,5,'B5',0,1,0,2,30,'0')");
        assertThrows(RuntimeException.class, f::enable);
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final MaterialTrackingPolicyGuard guard;

        Fixture() {
            jdbc =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    "jdbc:h2:mem:readinessReview"
                                            + UUID.randomUUID()
                                            + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                    "sa",
                                    ""));
            jdbc.execute(
                    "CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id"
                            + " VARCHAR(20),material_id BIGINT,sn_id BIGINT,sn_code"
                            + " VARCHAR(128),warehouse_id BIGINT,location_id BIGINT,batch_id"
                            + " BIGINT,batch_code VARCHAR(128),stock_quantity"
                            + " DECIMAL(18,4),reserved_quantity DECIMAL(18,4),total_cost"
                            + " DECIMAL(18,4),tracking_version BIGINT,valuation_group_id"
                            + " BIGINT,del_flag CHAR)");
            jdbc.execute(
                    "CREATE TABLE wm_sn(sn_id BIGINT,tenant_id VARCHAR(20),material_id"
                            + " BIGINT,sn_code VARCHAR(128),stock_id BIGINT,warehouse_id"
                            + " BIGINT,location_id BIGINT,batch_id BIGINT,batch_code"
                            + " VARCHAR(128),status VARCHAR(16),del_flag CHAR)");
            jdbc.execute(
                    "CREATE TABLE wm_valuation_group(valuation_group_id BIGINT,tenant_id"
                            + " VARCHAR(20),material_id BIGINT,warehouse_id BIGINT,batch_id"
                            + " BIGINT,quantity DECIMAL(18,4),amount DECIMAL(18,4))");
            jdbc.execute(
                    "CREATE TABLE wm_batch(batch_id BIGINT,tenant_id VARCHAR(20),material_id"
                            + " BIGINT,batch_code VARCHAR(128),del_flag CHAR)");
            jdbc.update(
                    "INSERT INTO wm_material_stock"
                            + " VALUES(10,'000001',1,20,'SN20',2,3,5,'B5',1,0,10,2,30,'0')");
            jdbc.update(
                    "INSERT INTO wm_sn VALUES(20,'000001',1,'SN20',10,2,3,5,'B5','IN_STOCK','0')");
            jdbc.update("INSERT INTO wm_valuation_group VALUES(30,'000001',1,2,5,1,10)");
            jdbc.update("INSERT INTO wm_batch VALUES(5,'000001',1,'B5','0')");
            var context = mock(ErpTraceContext.class);
            when(context.tenant()).thenReturn("000001");
            @SuppressWarnings("unchecked")
            ObjectProvider<MaterialTrackingUsagePort> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(mock(MaterialTrackingUsagePort.class));
            guard = new MaterialTrackingPolicyGuard(jdbc, context, provider);
            ReflectionTestUtils.setField(guard, "relayEnabled", true);
        }

        BdMaterial material(String stage) {
            var m = new BdMaterial();
            m.setMaterialId(1L);
            m.setTrackingVersion(1L);
            m.setTrackingStage(stage);
            m.setIsBatchManage(1L);
            m.setIsSerialManage(1L);
            m.setAllowMixedLot(1);
            return m;
        }

        void enable() {
            guard.prepare(material("AUDIT"), material("STRICT"), 1L);
        }
    }
}

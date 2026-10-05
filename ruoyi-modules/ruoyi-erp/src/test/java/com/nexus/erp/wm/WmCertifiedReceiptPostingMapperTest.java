package com.nexus.erp.wm;

import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 私有 H2 MySQL 兼容语法验证，不能当作现场 MySQL/InnoDB 并发证明。 */
class WmCertifiedReceiptPostingMapperTest {
    @Test void actualInsertUsesNativeStockReferenceRequiredWarehouseAndOriginalLine() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:receipt_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection();Statement st=c.createStatement()) {
            st.execute("CREATE TABLE wm_material_stock_tx(tx_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),material_id BIGINT,material_stock_id BIGINT NOT NULL,warehouse_id BIGINT NOT NULL,area_id BIGINT,location_id BIGINT,batch_id BIGINT,base_uom_id VARCHAR(64),tx_type VARCHAR(64),tx_flag INT,tx_quantity DECIMAL(18,4),before_quantity DECIMAL(18,4),after_quantity DECIMAL(18,4),source_doc_type VARCHAR(64),source_doc_id BIGINT,source_doc_line_id BIGINT,source_doc_code VARCHAR(64),tx_date DATETIME,create_by BIGINT,create_time DATETIME,del_flag CHAR(1),unit_cost DECIMAL(14,6),tx_amount DECIMAL(14,4))");
        }
        Configuration config=new Configuration(new Environment("private",new JdbcTransactionFactory(),ds));config.setMapUnderscoreToCamelCase(true);config.addMapper(WmCertifiedReceiptPostingMapper.class);
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            WmCertifiedReceiptPostingMapper mapper=session.getMapper(WmCertifiedReceiptPostingMapper.class);
            Head head=new Head("000000",101L,"RK202610030001",301L,401L,"SHELVED",501L,601L,null,BigDecimal.ONE,BigDecimal.TEN,"0");
            Detail detail=new Detail("000000",901L,101L,701L,201L,"4",new BigDecimal("5.4292"),501L,601L,null,1001L,null,"0");
            assertEquals(1,assertDoesNotThrow(()->mapper.insertTransaction("000000",1201L,1301L,"4",head,detail,BigDecimal.ZERO,detail.quantity(),"2026-10-03 10:00:00",9L,BigDecimal.TEN,new BigDecimal("54.2920"))));
            session.commit();
        }
        try(Connection c=ds.getConnection();ResultSet r=c.createStatement().executeQuery("SELECT * FROM wm_material_stock_tx")) {
            assertTrue(r.next());assertEquals(1301L,r.getLong("material_stock_id"));assertEquals(501L,r.getLong("warehouse_id"));
            assertEquals(701L,r.getLong("source_doc_line_id"));assertEquals("RECEIVE_IN",r.getString("tx_type"));
            assertEquals(new BigDecimal("5.4292"),r.getBigDecimal("tx_quantity"));assertEquals(new BigDecimal("54.2920"),r.getBigDecimal("tx_amount"));assertFalse(r.next());
        }
    }
    @Test void physicalStockAndTransactionMustNotSilentlyDefaultToZeroValue() throws Exception {
        var stock=java.util.Arrays.stream(WmCertifiedReceiptPostingMapper.class.getMethods()).filter(m->m.getName().equals("insertStock")).findFirst().orElseThrow();
        var transaction=java.util.Arrays.stream(WmCertifiedReceiptPostingMapper.class.getMethods()).filter(m->m.getName().equals("insertTransaction")).findFirst().orElseThrow();
        String stockSql=String.join(" ",stock.getAnnotation(org.apache.ibatis.annotations.Insert.class).value());
        String txSql=String.join(" ",transaction.getAnnotation(org.apache.ibatis.annotations.Insert.class).value());
        assertTrue(stockSql.contains("unit_cost") && stockSql.contains("total_cost"),"真实采购不能写默认零价值库存");
        assertTrue(txSql.contains("unit_cost") && txSql.contains("tx_amount"),"成本流水必须归属实际批准报价");
    }
    @Test void postingMustCheckActualOrderApprovalProofNotOnlyPositivePrice() throws Exception {
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();
        while(root!=null && !java.nio.file.Files.isRegularFile(root.resolve("AGENTS.md")))root=root.getParent();
        assertNotNull(root);
        String source=java.nio.file.Files.readString(root.resolve("ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmCertifiedReceiptPostingService.java"));
        assertTrue(source.contains("orderWorkflow.requireApprovedLocked(tenant,head.orderId())"),"APPROVED 与正价格都不是实际供应确认/批准来源证明");
    }
    @Test void permanentDraftClockReadDoesNotAddReverseLocksOrUseBusinessDate() throws Exception {
        var method=WmCertifiedReceiptPostingMapper.class.getMethod("readDraftCreatedAt",String.class,Long.class);
        String sql=String.join(" ",method.getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        assertTrue(sql.contains("MIN(UNIX_TIMESTAMP(create_time)*1000)"));
        assertTrue(sql.contains("wm_certified_receive_command") && sql.contains("tenant_id=#{tenant}") && sql.contains("receive_id=#{id}"));
        assertFalse(sql.contains("FOR UPDATE"),"头锁后读取不可变命令，不得新增原键重放的反向锁序");
        assertFalse(sql.contains("receive_date") || sql.contains("NOW()"),"不使用业务日期或当前时钟替代原创建事实");
        var options=method.getAnnotation(org.apache.ibatis.annotations.Options.class);
        assertFalse(options.useCache());assertEquals(org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE,options.flushCache());
    }
    @Test void lockingReceiptReadUsesTheActualNativeStockReference() throws Exception {
        String sql=String.join(" ",WmCertifiedReceiptPostingMapper.class.getMethod("lockReceiptTransactions",String.class,Long.class).getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        assertTrue(sql.contains("material_stock_id"));assertFalse(sql.contains("CAST("));assertTrue(sql.contains("FOR UPDATE"));
    }
}

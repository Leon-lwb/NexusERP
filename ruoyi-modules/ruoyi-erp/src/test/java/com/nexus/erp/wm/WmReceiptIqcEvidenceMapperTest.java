package com.nexus.erp.wm;

import com.nexus.common.mq.iqc.ReceiptIqcSource;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class WmReceiptIqcEvidenceMapperTest {
    @Test void everyErpReadIsTenantScopedNativeLockingReadAndThereIsOnlyOneInsert() throws Exception {
        Configuration config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(WmReceiptIqcEvidenceMapper.class);
        for (String name : new String[] {"selectReceiveForUpdate", "selectMaterialLinesForUpdate", "selectMaterialForUpdate",
            "selectUnitForUpdate", "selectInspectionForUpdate", "selectReceiptMaterialForUpdate", "selectMessageForUpdate"}) {
            var statement = config.getMappedStatement(WmReceiptIqcEvidenceMapper.class.getName() + "." + name);
            String sql = statement.getBoundSql(Map.of("tenantId", "000007", "receiveId", 101L,
                "materialId", 201L, "unitId", 301L, "inspectionId", 501L, "messageId", "event-501")).getSql();
            assertThat(sql).contains("tenant_id = ?", "FOR UPDATE").doesNotContain("iqc_status", "qc_iqc", "ROUND(", "CAST(");
            assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
            assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
        }
        assertThat(config.getMappedStatement(WmReceiptIqcEvidenceMapper.class.getName() + ".insertEvidence").getSqlCommandType())
            .isEqualTo(SqlCommandType.INSERT);
        assertThat(Arrays.stream(WmReceiptIqcEvidenceMapper.class.getMethods()).map(m -> m.getName()))
            .noneMatch(n -> n.toLowerCase().contains("update") && !n.endsWith("ForUpdate"))
            .noneMatch(n -> n.toLowerCase().contains("delete"));
    }
    @Test void serviceExposesNoExternalVerifiedOrEvidenceDtoPersistenceEntrance() {
        for (var method : WmReceiptIqcEvidenceService.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            assertThat(Arrays.asList(method.getParameterTypes())).doesNotContain(ReceiptIqcSource.Inspection.class,
                WmReceiptIqcEvidenceMapper.EvidenceRow.class, WmReceiptIqcEvidenceService.EvidenceIdentity.class);
        }
    }
    @Test void incrementalDdlPreservesNativePrecisionImmutableUniqueIdentitiesAndMilliseconds() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("script/sql/update"))) root = root.getParent();
        assertThat(root).isNotNull();
        String sql = Files.readString(root.resolve("script/sql/update/2026-10-03-certified-iqc-evidence-ry-erp.sql"));
        assertThat(sql).contains("CREATE TABLE `wm_certified_iqc_evidence`", "`inspected_quantity` decimal(14,4)",
            "`tenant_id`, `inspection_id`", "`tenant_id`, `receive_id`, `material_id`", "`tenant_id`, `first_message_id`",
            "`observed_at` datetime(3)", "`certified_at` datetime(3)");
        assertThat(sql.toUpperCase()).doesNotContain("DROP TABLE", "ALTER TABLE", "INSERT INTO", "UPDATE WM_");
    }

    @Test void catalogGateUsesSameDatabaseNativeDefinitionsAndOnlyMetadataIgnoresTenantPlugin() throws Exception {
        Configuration config = new Configuration(); config.addMapper(WmReceiptIqcEvidenceMapper.class);
        for (String name : new String[] {"selectNativeColumns", "selectNativeIndexes"}) {
            String sql = config.getMappedStatement(WmReceiptIqcEvidenceMapper.class.getName() + "." + name).getBoundSql(null).getSql();
            assertThat(sql).contains("information_schema.", "DATABASE()", "TABLE_NAME IN").doesNotContain("tenant_id =");
            assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
            var ignore = WmReceiptIqcEvidenceMapper.class.getMethod(name).getAnnotation(com.baomidou.mybatisplus.annotation.InterceptorIgnore.class);
            assertThat(ignore).isNotNull(); assertThat(ignore.tenantLine()).isEqualTo("true");
        }
    }

    @Test void negativeIdentityQueryLocksOnlyTenantReceiveMaterialIdentityNotQuantityOrUnit() throws Exception {
        Configuration config = new Configuration(); config.addMapper(WmReceiptIqcEvidenceMapper.class);
        String sql = config.getMappedStatement(WmReceiptIqcEvidenceMapper.class.getName() + ".selectMaterialIdentitiesForUpdate")
            .getBoundSql(Map.of("tenantId", "000007", "receiveId", 101L, "materialId", 201L)).getSql();
        assertThat(sql).contains("tenant_id = ?", "receive_id = ?", "material_id = ?", "FOR UPDATE")
            .doesNotContain("quantity", "uom", "wm_certified_iqc_evidence");
        assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
        for (String name : new String[] {"selectIsolationColumns", "selectIsolationIndexes"}) {
            String catalog = config.getMappedStatement(WmReceiptIqcEvidenceMapper.class.getName() + "." + name).getBoundSql(null).getSql();
            assertThat(catalog).contains("DATABASE()", "wm_material_stock").doesNotContain("wm_certified_iqc_evidence");
            assertThat(CCJSqlParserUtil.parse(catalog)).isNotNull();
        }
    }

    @Test void preciseIsolationReadsRealOriginalTransactionStockIdsAndWritesOnlySafetyFlags() throws Exception {
        Configuration config = new Configuration(); config.addMapper(WmReceiptIqcEvidenceMapper.class);
        var params = new java.util.HashMap<String,Object>(); params.put("tenantId", "000007"); params.put("receiveId", 101L);
        params.put("stockId", 501L); params.put("batchId", 401L); params.put("txId", 601L);
        params.put("warehouseId", 701L); params.put("areaId", 801L); params.put("locationId", 901L);
        params.put("s", WmReceiptIqcEvidencePreciseIsolationTest.stock(501L, 401L, 601L));
        params.put("b", WmReceiptIqcEvidencePreciseIsolationTest.batch(401L));
        for (String name : new String[] {"selectIsolationDetails", "selectIsolationReceiptTransactions", "selectIsolationStock",
            "selectIsolationSourceHead", "selectIsolationBatch", "selectIsolationStorage", "freezeIsolationStock", "markIsolationBatch"}) {
            String sql = config.getMappedStatement(WmReceiptIqcEvidenceMapper.class.getName() + "." + name).getBoundSql(params).getSql();
            assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
            assertThat(sql).doesNotContain("stock_quantity=", "reserved_quantity=", "quantity_source_head_tx_id=", "tx_quantity", "base_uom");
            if (name.equals("selectIsolationReceiptTransactions")) assertThat(sql).contains("material_stock_id AS stock_id", "WM_MATERIAL_RECEIVE");
            if (name.equals("freezeIsolationStock")) assertThat(sql).contains("frozen_flag=1", "version=version+1", "frozen_flag=0", "quantity_source_head_tx_id <=>");
        }
    }
}

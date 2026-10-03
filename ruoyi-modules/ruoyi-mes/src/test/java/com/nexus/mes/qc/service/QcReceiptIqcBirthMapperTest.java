package com.nexus.mes.qc.service;

import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class QcReceiptIqcBirthMapperTest {
    Configuration configuration() { var config = new Configuration(); config.addMapper(QcReceiptIqcBirthMapper.class); return config; }
    String sql(Configuration config, String name) {
        return config.getMappedStatement(QcReceiptIqcBirthMapper.class.getName()+"."+name)
            .getBoundSql(Map.of("tenantId","000007","inspectionId",501L,"receiveId",101L,"materialId",201L)).getSql();
    }
    @Test void catalogProofMustBeRequeriedRatherThanReusingMybatisLocalOrSecondLevelCache() {
        for (Class<?> mapper : List.of(QcReceiptIqcBirthMapper.class, com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper.class)) {
            var config = new Configuration(); config.addMapper(mapper);
            for (String name : List.of("selectNativeColumns", "selectNativeIndexes")) {
                var statement = config.getMappedStatement(mapper.getName()+"."+name);
                assertThat(statement.isUseCache()).isFalse(); assertThat(statement.isFlushCacheRequired()).isTrue();
            }
        }
    }
    @Test void nativeCatalogIsBoundToCurrentDatabaseAndVerifiesDatetimePrecision() throws Exception {
        var config = configuration();
        for (String name : List.of("selectNativeColumns", "selectNativeIndexes")) {
            String sql = sql(config, name); assertThat(sql).contains("DATABASE()", "information_schema.", "qc_receipt_iqc_birth");
            assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
            var ignore = QcReceiptIqcBirthMapper.class.getMethod(name).getAnnotation(com.baomidou.mybatisplus.annotation.InterceptorIgnore.class);
            assertThat(ignore).isNotNull(); assertThat(ignore.tenantLine()).isEqualTo("true");
        }
        assertThat(sql(config,"selectNativeColumns")).contains("DATETIME_PRECISION", "t.ENGINE");
    }
    @Test void bothWriterScopeReadsAreTenantScopedPermanentAndLocked() throws Exception {
        var config = configuration();
        for (String name : List.of("selectScopeInspections", "selectScopeBindings")) {
            String sql = sql(config, name); assertThat(sql).contains("tenant_id = ?", "receive_id = ?", "material_id = ?", "FOR UPDATE");
            assertThat(sql).doesNotContain("del_flag =", "LIMIT", "wm_"); assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
        }
    }
    @Test void birthReaderDoesNotLockOrConsultFutureErpOrInspectionMeasurementUnits() throws Exception {
        var config = configuration(); String sql = sql(config,"selectBinding");
        assertThat(sql).contains("tenant_id = ?", "iqc_id = ?", "qc_receipt_iqc_birth");
        assertThat(sql).doesNotContain("FOR UPDATE", "wm_", "bd_", "qc_inspect_detail");
        assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
    }
    @Test void persistenceIsInsertOnlyAndSavesAllOriginalBirthFields() throws Exception {
        var config = configuration();
        for (String name : List.of("insertInspection", "insertBinding")) {
            var statement = config.getMappedStatement(QcReceiptIqcBirthMapper.class.getName()+"."+name);
            assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
            String sql = sql(config,name); assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
            assertThat(sql).doesNotContain("ON DUPLICATE", "REPLACE", "UPDATE", "SELECT");
        }
        String binding = sql(config,"insertBinding");
        assertThat(binding).contains("base_uom_id", "quantity_scale", "quantity", "receipt_scope_sha256", "birth_sha256",
            "captured_at", "bound_at", "binding_sha256", "receive_code", "material_code", "material_name", "vendor_id", "vendor_name");
        for (var method : QcReceiptIqcBirthMapper.class.getMethods()) {
            assertThat(method.getAnnotation(org.apache.ibatis.annotations.Update.class)).isNull();
            assertThat(method.getAnnotation(org.apache.ibatis.annotations.Delete.class)).isNull();
        }
    }
    @Test void migrationCreatesNoHistoricalBirthCertificatesAndKeepsAllThreePermanentUniques() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("script/sql/update/2026-10-03-native-iqc-birth-ry-mes.sql"))) root = root.getParent();
        assertThat(root).isNotNull(); String ddl = Files.readString(root.resolve("script/sql/update/2026-10-03-native-iqc-birth-ry-mes.sql"));
        String body = ddl.lines().filter(line -> !line.stripLeading().startsWith("--")).reduce("", (a,b)->a+"\n"+b);
        assertThat(body).contains("ENGINE=InnoDB", "`quantity` decimal(14,4)", "`bound_at` datetime(3)");
        assertThat(body).contains("(`tenant_id`,`iqc_id`)", "(`tenant_id`,`birth_id`)", "(`tenant_id`,`receive_id`,`material_id`)");
        assertThat(body).doesNotContain("IF NOT EXISTS", "DROP TABLE", "INSERT INTO", "UPDATE `", "ALTER TABLE");
        var fields = Pattern.compile("(?m)^\s*`([^`]+)`").matcher(body.substring(0,body.indexOf("PRIMARY KEY")));
        String insert = sql(configuration(),"insertBinding"); int count=0;
        while (fields.find()) { assertThat(insert).contains(fields.group(1)); count++; }
        assertThat(count).isEqualTo(18);
    }
}

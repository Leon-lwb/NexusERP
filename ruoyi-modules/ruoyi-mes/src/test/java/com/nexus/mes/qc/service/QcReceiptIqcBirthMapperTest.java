package com.nexus.mes.qc.service;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class QcReceiptIqcBirthMapperTest {
    Configuration configuration() { var config = new Configuration(); config.addMapper(QcReceiptIqcBirthMapper.class); return config; }
    String sql(Configuration config, String name) {
        return config.getMappedStatement(QcReceiptIqcBirthMapper.class.getName()+"."+name)
            .getBoundSql(Map.of("tenantId","000007","inspectionId",501L,"receiveId",101L,"materialId",201L)).getSql();
    }
    @Test void catalogProofMustBeRequeriedRatherThanReusingMybatisLocalOrSecondLevelCache() {
        for (Class<?> mapper :
                List.of(QcReceiptIqcBirthMapper.class, QcReceiptIqcEvidenceMapper.class)) {
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
            var ignore =
                    QcReceiptIqcBirthMapper.class
                            .getMethod(name)
                            .getAnnotation(InterceptorIgnore.class);
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
            assertThat(method.getAnnotation(Update.class)).isNull();
            assertThat(method.getAnnotation(Delete.class)).isNull();
        }
    }
    /** MySQL 转储会省略默认 RESTRICT，兼容等价语法但仍拒绝级联和可置空引用。 */
    @Test void restrictiveForeignKeyAcceptsExplicitOrImplicitMysqlDefaults() {
        for (String rules : List.of("", " ON DELETE RESTRICT ON UPDATE RESTRICT",
                " ON UPDATE NO ACTION ON DELETE NO ACTION", " ON DELETE RESTRICT")) {
            assertRestrictiveBirthForeignKey("  CONSTRAINT `fk_native_iqc_birth` FOREIGN KEY (`iqc_id`) REFERENCES `qc_iqc` (`iqc_id`)" + rules);
        }
    }

    /** 不能为适配转储而放宽永久出生记录的删除、更新及父表身份守卫。 */
    @Test void restrictiveForeignKeyRejectsCascadesNullingDuplicatesAndWrongParent() {
        String reference = "  CONSTRAINT `fk_native_iqc_birth` FOREIGN KEY (`iqc_id`) REFERENCES `qc_iqc` (`iqc_id`)";
        for (String rules : List.of(" ON DELETE CASCADE", " ON UPDATE CASCADE", " ON DELETE SET NULL",
                " ON UPDATE SET DEFAULT", " ON DELETE RESTRICT ON DELETE RESTRICT")) {
            Assertions.assertThrows(
                    AssertionError.class,
                    () -> assertRestrictiveBirthForeignKey(reference + rules));
        }
        Assertions.assertThrows(
                AssertionError.class,
                () ->
                        assertRestrictiveBirthForeignKey(
                                reference.replace(
                                        "REFERENCES `qc_iqc`", "REFERENCES `wrong_parent`")));
    }

    /** 只规范化外键规则，不改变数量精度、三条永久唯一键或来源列的断言。 */
    private static void assertRestrictiveBirthForeignKey(String ddl) {
        var matcher = Pattern.compile("(?im)^\\s*CONSTRAINT\\s+`fk_native_iqc_birth`\\s+FOREIGN KEY\\s*\\(`iqc_id`\\)\\s*REFERENCES\\s+`qc_iqc`\\s*\\(`iqc_id`\\)([^\\r\\n]*)$")
            .matcher(ddl);
        assertThat(matcher.find()).as("必须保留IQC永久出生外键身份").isTrue();
        String rules =
                matcher.group(1)
                        .replaceFirst(",\\s*$", "")
                        .trim()
                        .replaceAll("\\s+", " ")
                        .toUpperCase(Locale.ROOT);
        assertThat(rules.matches("(?:ON (?:DELETE|UPDATE) (?:RESTRICT|NO ACTION)(?: |$))*"))
            .as("外键仅允许显式或默认RESTRICT/NO ACTION").isTrue();
        var actions = Pattern.compile("ON (DELETE|UPDATE) (?:RESTRICT|NO ACTION)").matcher(rules);
        Set<String> seen = new HashSet<>();
        while (actions.find()) assertThat(seen.add(actions.group(1))).as("禁止重复或冲突的外键规则").isTrue();
    }

    @Test void installationKeepsAllBirthFieldsAndThreePermanentTenantUniques() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("script/sql/ry-mes.sql"))) root = root.getParent();
        assertThat(root).isNotNull();
        var ddl = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`qc_receipt_iqc_birth`\\s*\\(.*?\\)\\s*ENGINE[^;]*;")
            .matcher(Files.readString(root.resolve("script/sql/ry-mes.sql")));
        assertThat(ddl.find()).as("Missing installation DDL: qc_receipt_iqc_birth").isTrue();
        String body = ddl.group();
        assertThat(body).contains("ENGINE=InnoDB", "`quantity` decimal(14,4)", "`bound_at` datetime(3)");
        assertThat(body).contains("(`tenant_id`,`iqc_id`)", "(`tenant_id`,`birth_id`)", "(`tenant_id`,`receive_id`,`material_id`)");
        assertThat(body).contains("PRIMARY KEY (`iqc_id`)",
            "UNIQUE KEY `uk_native_iqc` (`tenant_id`,`iqc_id`)",
            "UNIQUE KEY `uk_native_birth` (`tenant_id`,`birth_id`)",
            "UNIQUE KEY `uk_native_scope` (`tenant_id`,`receive_id`,`material_id`)",
            "FOREIGN KEY (`iqc_id`) REFERENCES `qc_iqc` (`iqc_id`)");
        assertRestrictiveBirthForeignKey(body);
        var fields = Pattern.compile("(?m)^\\s*`([^`]+)`").matcher(body.substring(0,body.indexOf("PRIMARY KEY")));
        String insert = sql(configuration(),"insertBinding"); int count=0;
        while (fields.find()) { assertThat(insert).contains(fields.group(1)); count++; }
        assertThat(count).isEqualTo(18);
    }
}

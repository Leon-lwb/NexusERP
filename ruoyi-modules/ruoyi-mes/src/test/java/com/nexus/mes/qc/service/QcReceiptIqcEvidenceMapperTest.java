package com.nexus.mes.qc.service;

import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

class QcReceiptIqcEvidenceMapperTest {
    @Test void nativeMesQueriesAreReadOnlyTenantScopedAndParseUnderCurrentTenantInterceptorParser() throws Exception {
        Configuration config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(QcReceiptIqcEvidenceMapper.class);
        for (String name : new String[] {"selectInspection", "selectInspectionVersions", "selectInspectionBodies"}) {
            var statement = config.getMappedStatement(QcReceiptIqcEvidenceMapper.class.getName() + "." + name);
            String sql = statement.getBoundSql(Map.of("tenantId", "000007", "receiveId", 101L,
                "materialId", 201L, "inspectionId", 501L)).getSql();
            assertThat(sql).contains("tenant_id = ?").doesNotContain("wm_", "FOR UPDATE");
            assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
            assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
        }
    }
    @Test void actualBodyIncludesEveryColumnOfCurrentNativeHeaderAndDetailSchema() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("script/sql/ry-mes.sql"))) root = root.getParent();
        assertThat(root).isNotNull(); String ddl = Files.readString(root.resolve("script/sql/ry-mes.sql"));
        Configuration config = new Configuration(); config.addMapper(QcReceiptIqcEvidenceMapper.class);
        for (String[] pair : new String[][] {{"qc_iqc", "selectInspection"}, {"qc_inspect_detail", "selectInspectionBodies"}}) {
            int start = ddl.indexOf("CREATE TABLE `" + pair[0] + "`"); assertThat(start).isGreaterThanOrEqualTo(0);
            int end = ddl.indexOf("PRIMARY KEY", start); String table = ddl.substring(start, end);
            var fields = Pattern.compile("(?m)^\\s*`([^`]+)`").matcher(table);
            String query = config.getMappedStatement(QcReceiptIqcEvidenceMapper.class.getName() + "." + pair[1])
                .getBoundSql(Map.of("tenantId", "000007", "inspectionId", 501L)).getSql();
            while (fields.find()) assertThat(query).contains("'" + fields.group(1) + "', " + fields.group(1));
        }
    }

    @Test void catalogGateUsesSameDatabaseNativeDefinitionsAndOnlyMetadataIgnoresTenantPlugin() throws Exception {
        Configuration config = new Configuration(); config.addMapper(QcReceiptIqcEvidenceMapper.class);
        for (String name : new String[] {"selectNativeColumns", "selectNativeIndexes"}) {
            String sql = config.getMappedStatement(QcReceiptIqcEvidenceMapper.class.getName() + "." + name).getBoundSql(null).getSql();
            assertThat(sql).contains("information_schema.", "DATABASE()", "TABLE_NAME IN").doesNotContain("tenant_id =");
            assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
            var ignore = QcReceiptIqcEvidenceMapper.class.getMethod(name).getAnnotation(com.baomidou.mybatisplus.annotation.InterceptorIgnore.class);
            assertThat(ignore).isNotNull(); assertThat(ignore.tenantLine()).isEqualTo("true");
        }
    }

    @Test void negativeIdentitySqlDoesNotMapTheBareQuantityEvenThoughNativeBodyRetainsAuditFields() throws Exception {
        Configuration config = new Configuration(); config.addMapper(QcReceiptIqcEvidenceMapper.class);
        String sql = config.getMappedStatement(QcReceiptIqcEvidenceMapper.class.getName() + ".selectInspectionIdentity")
            .getBoundSql(Map.of("tenantId", "000007", "inspectionId", 501L)).getSql();
        String projection = sql.substring(0, sql.indexOf("JSON_OBJECT"));
        assertThat(projection).doesNotContain("quantity");
        assertThat(sql).contains("tenant_id = ?", "iqc_id = ?"); assertThat(CCJSqlParserUtil.parse(sql)).isNotNull();
    }
}

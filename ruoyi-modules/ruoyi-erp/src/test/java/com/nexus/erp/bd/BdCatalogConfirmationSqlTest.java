package com.nexus.erp.bd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdCatalogConfirmationMapper;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 只解析真实 Mapper SQL，不获取连接；防止空安全 CAS 或日期写法只存在于模拟桩。 */
class BdCatalogConfirmationSqlTest {
    @Test
    void realUnitStatementHasIdentityCasAndExplicitWallClockWrite() {
        var args = new HashMap<String, Object>();
        args.put("row", new BdUnitOfMeasure()); args.put("expectedVersion", null);
        args.put("verifiedAt", "2026-10-04 10:00:00");
        BoundSql sql = statement("confirmUnit", args);
        assertTrue(sql.getSql().contains("conversion_version <=> ?"));
        assertTrue(sql.getSql().contains("tenant_id = ?"));
        assertTrue(sql.getSql().contains("uom_id = ?"));
        assertTrue(sql.getSql().contains("STR_TO_DATE"));
        assertTrue(sql.getParameterMappings().stream().anyMatch(parameter -> parameter.getProperty().equals("row.updateBy")));
    }

    @Test
    void realTermsStatementUsesNullSafeVersionCasAndActualRelationIdentity() {
        var args = new HashMap<String, Object>();
        args.put("row", new BdMaterialVendor()); args.put("expectedVersion", null);
        args.put("effectiveAt", "2026-10-01 00:00:00"); args.put("expiresAt", "2026-11-01 00:00:00");
        args.put("verifiedAt", "2026-10-04 10:00:00");
        String sql = statement("confirmTerms", args).getSql();
        assertTrue(sql.contains("terms_version <=> ?"));
        assertTrue(sql.contains("material_id = ? AND vendor_id = ?"));
        assertFalse(sql.contains("min_order_qty ="), "旧起订量不属于确认字段");
        assertFalse(sql.contains("effective_date ="), "旧关系生效日期不属于确认字段");
    }

    @Test
    void allIdentityReadsHaveExplicitTenantAndRowLocks() {
        Arrays.stream(BdCatalogConfirmationMapper.class.getMethods()).filter(method -> method.getName().startsWith("lock"))
            .forEach(method -> {
                String sql = String.join(" ", method.getAnnotation(Select.class).value());
                assertTrue(sql.contains("tenant_id = #{tenantId}"), method.getName());
                assertTrue(sql.contains("FOR UPDATE"), method.getName());
            });
    }

    private static BoundSql statement(String method, Map<String, Object> args) {
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        new MapperAnnotationBuilder(configuration, BdCatalogConfirmationMapper.class).parse();
        return configuration.getMappedStatement(BdCatalogConfirmationMapper.class.getName() + "." + method).getBoundSql(args);
    }
}

package com.nexus.erp.ai.adapter;

import com.nexus.erp.support.ErpDdlTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 实际出生服务、端点和追加结构必须存在；缺类使用断言形成运行时反例。 */
class ErpCertifiedSourceBirthContractTest {
    @Test
    void actualCreationServiceExistsInsteadOfAnOfflineCertifier() {
        assertDoesNotThrow(() -> Class.forName("com.nexus.erp.ai.adapter.ErpCertifiedSourceBirthService"));
    }

    @Test
    void actualCreateAndPreviewEndpointsExist() throws Exception {
        Class<?> type = assertDoesNotThrow(() -> Class.forName("com.nexus.erp.ai.adapter.ErpCertifiedSourceController"));
        assertEquals("/erp/ai/certified-source", type.getAnnotation(RequestMapping.class).value()[0]);
        Set<String> paths = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(PostMapping.class))
            .flatMap(m -> Arrays.stream(m.getAnnotation(PostMapping.class).value())).collect(Collectors.toSet());
        assertEquals(Set.of("/materials", "/preview"), paths);
    }

    @Test
    void creationRequestCannotAcceptExistingMaterialOrSelfCertification() throws Exception {
        Class<?> type = assertDoesNotThrow(() -> Class.forName("com.nexus.erp.ai.adapter.ErpCertifiedSourceBirthService$CreateMaterial"));
        Set<String> names = Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet());
        assertFalse(names.stream().anyMatch(Set.of("materialId", "birthId", "verified", "createdAt", "actorId", "tenantId", "sourceFingerprint")::contains));
        assertTrue(names.containsAll(Set.of("materialCode", "materialName", "categoryId", "baseUomId", "warehouseId", "areaId", "locationId", "scenario")));
    }

    @Test
    void globalCoverageSqlCannotHideOtherTenantDeletedOrOtherWarehouseSources() throws Exception {
        var mapper=com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.class;
        for(String method:List.of("lockAllStocks","lockAllTransactions")){
            var query=mapper.getMethod(method,Long.class);
            String sql=String.join(" ",query.getAnnotation(org.apache.ibatis.annotations.Select.class).value()).toLowerCase();
            assertTrue(sql.contains("where material_id=#{id}"));assertTrue(sql.contains("for update"));
            for(String hidden:List.of("tenant_id","del_flag","warehouse_id","stock_quantity","tx_quantity","limit"))assertFalse(sql.contains(hidden));
            var options=query.getAnnotation(org.apache.ibatis.annotations.Options.class);
            assertFalse(options.useCache());assertEquals(org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE,options.flushCache());
        }
    }
    @Test
    void birthInstallationRequiresTenantMaterialIdentityAndPersistedProvenance() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("script/sql/ry-erp.sql"))) root = root.getParent();
        assertNotNull(root, "Installation schema must be present");
        var table = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`erp_certified_source_birth`\\s*\\(.*?\\)\\s*ENGINE[^;]*;")
            .matcher(Files.readString(root.resolve("script/sql/ry-erp.sql")));
        assertTrue(table.find(), "Persisted birth registry must exist in the installation schema");
        String text = table.group().toLowerCase(Locale.ROOT);
        ErpDdlTestSupport.assertPrimaryKey(text, "birth_id");
        ErpDdlTestSupport.assertIndex(text, true, "uk_source_birth_material", "tenant_id", "material_id");
        for (String[] column : List.of(new String[]{"tenant_id", "varchar(20)"}, new String[]{"material_id", "bigint"},
            new String[]{"born_at", "datetime"}, new String[]{"actor_id", "bigint"}, new String[]{"birth_fingerprint", "char(64)"})) {
            ErpDdlTestSupport.assertColumn(text, column[0], column[1] + " NOT NULL");
        }
        assertTrue(Pattern.compile("engine\\s*=\\s*innodb").matcher(text).find());
    }
}

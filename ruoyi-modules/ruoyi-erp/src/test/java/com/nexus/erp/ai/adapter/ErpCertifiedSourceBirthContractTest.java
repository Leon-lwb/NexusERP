package com.nexus.erp.ai.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
    void birthMigrationOnlyAddsEmptyRegistryAndNeverBackfillsOldMaterial() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("script/sql")) && root.getParent() != null) root = root.getParent();
        Path sql = root.resolve("script/sql/update/2026-10-04-certified-source-birth-ry-erp.sql");
        assertTrue(Files.exists(sql), "同事务持久出生必须有追加结构，不能仅保留内存声明");
        String text = Files.readString(sql).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "").toLowerCase();
        assertTrue(text.contains("create table `erp_certified_source_birth`"));
        assertFalse(text.matches("(?s).*\\b(insert|update|delete|drop|truncate)\\b.*"));
        assertTrue(text.contains("unique") && text.contains("`tenant_id`, `material_id`"));
        assertTrue(text.contains("engine = innodb"));
    }
}

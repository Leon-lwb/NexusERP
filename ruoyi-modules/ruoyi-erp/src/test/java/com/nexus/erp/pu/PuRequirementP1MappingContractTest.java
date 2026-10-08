package com.nexus.erp.pu;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅源码契约：绝不打开数据库，也不执行 SQL。 */
@Tag("dev")
class PuRequirementP1MappingContractTest {

    @Test
    void verifiedVendorTermsMapOnlyToNullableInstallationColumns() throws Exception {
        Map<String, Class<?>> columns = new LinkedHashMap<>();
        columns.put("order_uom_id", Long.class);
        columns.put("verified_moq_qty", BigDecimal.class);
        columns.put("moq_uom_id", Long.class);
        columns.put("order_multiple", BigDecimal.class);
        columns.put("allow_split", Boolean.class);
        columns.put("units_per_pack", BigDecimal.class);
        columns.put("terms_version", String.class);
        columns.put("terms_effective_at", Date.class);
        columns.put("terms_expires_at", Date.class);
        columns.put("terms_verified_at", Date.class);
        assertNullableColumns(BdMaterialVendor.class, "bd_material_vendor", columns);
    }

    @Test
    void verifiedUomMetadataMapsOnlyToNullableInstallationColumns() throws Exception {
        Map<String, Class<?>> columns = new LinkedHashMap<>();
        columns.put("quantity_scale", Integer.class);
        columns.put("conversion_version", String.class);
        columns.put("conversion_verified_at", Date.class);
        assertNullableColumns(BdUnitOfMeasure.class, "bd_unit_of_measure", columns);
    }

    @Test
    void ledgerHasExactlyTheDdlColumnsAndSnowflakeKey() throws Exception {
        Class<?> entity = Class.forName("com.nexus.erp.pu.domain.PuRequirementAllocationLedger");
        assertTableFields(entity, "pu_requirement_allocation_ledger",
            "allocation_id,tenant_id,material_id,source_type,detail_id,requisition_line_id,allocation_no,"
                + "order_line_id,base_uom_id,covered_demand_base_qty,occupied_base_qty,fulfilled_base_qty,"
                + "released_base_qty,allocation_status,allocation_key,decision_idempotency_key,version,"
                + "create_time,transfer_time,released_at,release_reason,update_time", "allocationId");
        assertField(entity, "tenantId", String.class);
        assertField(entity, "detailId", Long.class);
        assertField(entity, "allocationNo", Integer.class);
        assertField(entity, "occupiedBaseQty", BigDecimal.class);
        assertField(entity, "allocationStatus", String.class);
        assertField(entity, "version", Integer.class);
        assertNotNull(entity.getDeclaredField("version").getAnnotation(Version.class));
        assertTrue(ddlTable("pu_requirement_allocation_ledger").contains("UNIQUE INDEX `uk_pral_tenant_req_detail_no`"));
        assertTrue(ddlTable("pu_requirement_allocation_ledger").contains("UNIQUE INDEX `uk_pral_tenant_key`"));
    }

    @Test
    void appendOnlyEventHasExactlyTheDdlColumnsAndSnowflakeKey() throws Exception {
        Class<?> entity = Class.forName("com.nexus.erp.pu.domain.PuRequirementAllocationEvent");
        assertTableFields(entity, "pu_requirement_allocation_event",
            "event_id,tenant_id,allocation_id,event_sequence,event_key,event_type,from_status,to_status,occupied_base_before,"
                + "occupied_base_after,requisition_line_id,order_line_id,actor_id,occurred_at", "eventId");
        assertField(entity, "tenantId", String.class);
        assertField(entity, "occupiedBaseBefore", BigDecimal.class);
        assertField(entity, "eventSequence", Integer.class);
        assertField(entity, "occurredAt", Date.class);
        assertTrue(ddlTable("pu_requirement_allocation_event").contains("UNIQUE INDEX `uk_prae_tenant_event_key`"));
        assertTrue(ddlTable("pu_requirement_allocation_event").contains(
            "UNIQUE INDEX `uk_prae_tenant_allocation_sequence`(`tenant_id`, `allocation_id`, `event_sequence`)"));
    }

    @Test
    void guardUsesCompositeTenantMaterialKeyForInsertIgnoreAndRowLock() throws Exception {
        String ddl = ddlTable("pu_requirement_material_guard");
        assertTrue(ddl.contains("PRIMARY KEY (`tenant_id`, `material_id`)"));
        Class<?> mapper = mapper("PuRequirementMaterialGuardMapper");
        String insert = sql(mapper.getMethod("ensureGuard", String.class, Long.class), Insert.class);
        assertTrue(Pattern.compile("(?is)INSERT\\s+IGNORE\\s+INTO\\s+pu_requirement_material_guard\\s*"
            + "\\(tenant_id,\\s*material_id,\\s*version\\)").matcher(insert).find(), insert);
        assertTrue(insert.contains("#{tenantId}") && insert.contains("#{materialId}"), insert);
        String lock = sql(mapper.getMethod("lockVersion", String.class, Long.class), Select.class);
        assertTrue(lock.contains("tenant_id = #{tenantId}") && lock.contains("material_id = #{materialId}"), lock);
        assertTrue(Pattern.compile("(?i)\\bFOR\\s+UPDATE\\b").matcher(lock).find(), lock);
        String cas = sql(mapper.getMethod("advanceVersion", String.class, Long.class, Integer.class), Update.class);
        assertTrue(cas.contains("tenant_id = #{tenantId}") && cas.contains("material_id = #{materialId}"), cas);
        assertTrue(cas.contains("version = #{expectedVersion}") && cas.contains("version = version + 1"), cas);
        assertFalse(Arrays.stream(mapper.getInterfaces()).anyMatch(i -> i.getSimpleName().equals("BaseMapper")));
    }

    @Test
    void ledgerCasAndAuditInsertAreExplicitlyTenantScoped() throws Exception {
        Class<?> ledger = mapper("PuRequirementAllocationLedgerMapper");
        String cas = sql(ledger.getMethod("updateWithVersion", String.class,
            Class.forName("com.nexus.erp.pu.domain.PuRequirementAllocationLedger"), Integer.class), Update.class);
        assertTrue(cas.contains("tenant_id = #{tenantId}"), cas);
        assertTrue(cas.contains("allocation_id = #{row.allocationId}"), cas);
        assertTrue(cas.contains("version = #{expectedVersion}"), cas);
        assertTrue(Pattern.compile("(?i)version\\s*=\\s*version\\s*\\+\\s*1").matcher(cas).find(), cas);
        assertTrue(cas.contains("allocation_status = #{row.allocationStatus}"), cas);
        String ledgerInsert = sql(ledger.getMethod("insert", Class.forName("com.nexus.erp.pu.domain.PuRequirementAllocationLedger")), Insert.class);
        assertTrue(ledgerInsert.contains("allocation_id") && ledgerInsert.contains("tenant_id")
            && ledgerInsert.contains("allocation_key"), ledgerInsert);
        Class<?> event = mapper("PuRequirementAllocationEventMapper");
        String auditInsert = sql(event.getMethod("insert", Class.forName("com.nexus.erp.pu.domain.PuRequirementAllocationEvent")), Insert.class);
        assertTrue(auditInsert.contains("event_id") && auditInsert.contains("tenant_id")
            && auditInsert.contains("event_key"), auditInsert);
        assertFalse(Arrays.stream(event.getMethods()).anyMatch(method -> method.isAnnotationPresent(Update.class)
            || Arrays.stream(method.getAnnotations()).anyMatch(a -> a.annotationType().getSimpleName().equals("Delete"))));
    }

    private static void assertNullableColumns(Class<?> entity, String table, Map<String, Class<?>> columns)
        throws Exception {
        assertEquals(table, entity.getAnnotation(TableName.class).value());
        String ddl = ddlTable(table);
        Object blank = entity.getDeclaredConstructor().newInstance();
        for (Map.Entry<String, Class<?>> entry : columns.entrySet()) {
            String column = entry.getKey();
            assertTrue(Pattern.compile("(?im)^\\s*`" + Pattern.quote(column)
                + "`\\s+\\w+(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?"
                + "(?:\\s+unsigned)?\\s+NULL\\s+DEFAULT\\s+NULL").matcher(ddl).find(),
                "Installation column must stay nullable without a verified default: " + column);
            String fieldName = camelCase(column);
            Field field = entity.getDeclaredField(fieldName);
            assertEquals(entry.getValue(), field.getType(), column);
            assertNotNull(field.getAnnotation(TableField.class), column);
            assertEquals(column, field.getAnnotation(TableField.class).value(), column);
            field.setAccessible(true);
            assertNull(field.get(blank), "Historical " + table + "." + column + " must stay unverified");
        }
    }

    private static void assertTableFields(Class<?> entity, String table, String columns, String id) throws Exception {
        assertEquals(table, entity.getAnnotation(TableName.class).value());
        List<String> expected = List.of(columns.split(","));
        Matcher ddl = Pattern.compile("(?m)^\\s*`([a-z0-9_]+)`\\s+(bigint|int|varchar|decimal|datetime)\\b")
            .matcher(ddlTable(table));
        Map<String, Class<?>> ddlTypes = new LinkedHashMap<>();
        while (ddl.find()) {
            Class<?> type = switch (ddl.group(2)) {
                case "bigint" -> Long.class;
                case "int" -> Integer.class;
                case "decimal" -> BigDecimal.class;
                case "datetime" -> Date.class;
                case "varchar" -> String.class;
                default -> throw new IllegalArgumentException(ddl.group(2));
            };
            ddlTypes.put(ddl.group(1), type);
        }
        List<String> actualDdl = List.copyOf(ddlTypes.keySet());
        assertEquals(expected, actualDdl, "Installation DDL drift: " + table);
        List<String> mapped = Arrays.stream(entity.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(field -> {
                TableId pk = field.getAnnotation(TableId.class);
                if (pk != null) {
                    return pk.value();
                }
                TableField column = field.getAnnotation(TableField.class);
                assertNotNull(column, "Explicit mapping required for " + field.getName());
                return column.value();
            }).toList();
        assertEquals(actualDdl, mapped, "Entity mapping drift: " + table);
        for (Field field : entity.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                TableId pkField = field.getAnnotation(TableId.class);
                String column = pkField == null ? field.getAnnotation(TableField.class).value() : pkField.value();
                assertEquals(ddlTypes.get(column), field.getType(), table + "." + column);
            }
        }
        TableId pk = entity.getDeclaredField(id).getAnnotation(TableId.class);
        assertNotNull(pk, "Snowflake primary key is explicit");
        assertEquals(IdType.ASSIGN_ID, pk.type());
        assertEquals(Long.class, entity.getDeclaredField(id).getType());
    }

    private static Class<?> mapper(String name) throws Exception {
        Class<?> mapper = Class.forName("com.nexus.erp.pu.mapper." + name);
        assertNotNull(mapper.getAnnotation(Mapper.class));
        assertFalse(Arrays.stream(mapper.getAnnotations())
            .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals("DS")),
            "Data source must be selected by the calling ERP service proxy, not this mapper");
        return mapper;
    }

    private static String sql(Method method, Class<? extends java.lang.annotation.Annotation> annotation) {
        if (annotation == Insert.class) {
            assertNotNull(method.getAnnotation(Insert.class));
            return String.join(" ", method.getAnnotation(Insert.class).value());
        }
        if (annotation == Select.class) {
            assertNotNull(method.getAnnotation(Select.class));
            return String.join(" ", method.getAnnotation(Select.class).value());
        }
        assertNotNull(method.getAnnotation(Update.class));
        return String.join(" ", method.getAnnotation(Update.class).value());
    }

    private static String ddlTable(String name) throws IOException {
        Matcher table = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`" + name + "`\\s*\\((.*?)\\)\\s*ENGINE")
            .matcher(installationSql());
        assertTrue(table.find(), "Missing installation DDL: " + name);
        return table.group(1);
    }

    private static String installationSql() throws IOException {
        return Files.readString(root().resolve("script/sql/ry-erp.sql"));
    }

    private static Path root() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("script/sql/ry-erp.sql"))) {
                return dir;
            }
        }
        throw new IllegalStateException("Repository root not found");
    }

    private static String camelCase(String snake) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char character : snake.toCharArray()) {
            if (character == '_') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return name.toString();
    }

    private static void assertField(Class<?> entity, String fieldName, Class<?> type) throws Exception {
        assertEquals(type, entity.getDeclaredField(fieldName).getType(), entity.getName() + "." + fieldName);
    }
}

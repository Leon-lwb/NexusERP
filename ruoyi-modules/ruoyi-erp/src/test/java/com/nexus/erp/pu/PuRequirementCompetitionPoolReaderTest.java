package com.nexus.erp.pu;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 离线 reader 契约，不是数据库并发或完整性证明。
 * 反射仅用于契约最初拟新增的两个类，使 RED 阶段可以编译，
 * 并让实现缺失表现为普通断言失败，而非 javac 错误。
 * 测试使用模拟的物料 guard 和当前读 Mapper 来调用真实 reader 及资格校验。
 * 正例由真实 Spring ERP 本地事务与同持有者资源映射取得资格；不以活跃标记自证。
 * 该离线夹具不证明当前生产应用已有可靠的统一事务绑定入口。
 */
@Tag("dev")
class PuRequirementCompetitionPoolReaderTest {

    private static final String READER = "com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader";
    private static final String MAPPER = "com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper";
    private static final String TENANT = "t-100";
    private static final Long MATERIAL = 139L;
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 9, 30);
    private static final String DETAILS = "lockRequirementDetails";
    private static final String REQUIREMENTS = "lockRequirementHeads";
    private static final String STOCKS = "lockStocks";
    private static final String ORDER_LINES = "lockOrderLines";
    private static final String ORDERS = "lockOrderHeads";
    private static final String REQUISITION_LINES = "lockRequisitionLines";
    private static final String REQUISITIONS = "lockRequisitionHeads";
    private static final String ALLOCATIONS = "lockAllocations";
    private static final List<String> READ_ORDER = List.of(
        DETAILS, REQUIREMENTS, STOCKS, ORDER_LINES,
        ORDERS, REQUISITION_LINES, REQUISITIONS, ALLOCATIONS);
    private static final Map<String, String> TABLES = Map.of(
        DETAILS, "prd_material_requirement_detail",
        REQUIREMENTS, "prd_material_requirement",
        STOCKS, "wm_material_stock",
        ORDER_LINES, "pu_purchase_order_line",
        ORDERS, "pu_purchase_order",
        REQUISITION_LINES, "pu_purchase_requisition_line",
        REQUISITIONS, "pu_purchase_requisition",
        ALLOCATIONS, "pu_requirement_allocation_ledger");
    private static final Map<String, String> PRIMARY_KEYS = Map.of(
        DETAILS, "detail_id", REQUIREMENTS, "requirement_id",
        STOCKS, "stock_id", ORDER_LINES, "line_id", ORDERS, "order_id",
        REQUISITION_LINES, "line_id", REQUISITIONS, "requisition_id",
        ALLOCATIONS, "allocation_id");

    @AfterEach
    void clearActualTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty(), "reader 测试不得泄漏连接资源");
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(), "reader 测试不得泄漏事务同步");
    }

    @Test
    void entryIsInternalErpMandatoryTransactionAndReturnsOnlyRawLockedFacts() {
        Class<?> reader = requiredClass(READER);
        assertNotNull(reader.getAnnotation(Service.class));
        assertNull(reader.getAnnotation(RestController.class));
        assertNull(reader.getAnnotation(RequestMapping.class));
        Set<Class<?>> dependencies = Arrays.stream(reader.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(field -> field.getType()).collect(Collectors.toSet());
        assertEquals(Set.of(requiredClass(MAPPER), PuRequirementMaterialLockService.class,
            PuRequirementErpTransactionQualification.class), dependencies);
        assertEquals(List.of("materialPool"), Arrays.stream(reader.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName).sorted().toList());
        Method entry = entry();
        assertEquals("erp", entry.getAnnotation(DS.class).value());
        assertEquals(Propagation.MANDATORY, entry.getAnnotation(Transactional.class).propagation());
        assertNull(entry.getAnnotation(RequestMapping.class));
        Class<?> facts = entry.getReturnType();
        assertEquals("RawLockedFacts", facts.getSimpleName());
        for (Method method : facts.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("complete") || name.contains("verified"), name);
        }
        assertTrue(Arrays.stream(facts.getDeclaredFields()).noneMatch(field ->
            field.getName().toLowerCase(Locale.ROOT).matches(".*(complete|verified).*")));
    }

    @Test
    void noActualTransactionRejectsBeforeGuardOrAnyCurrentRead() {
        assertDenied(TENANT, MATERIAL, WINDOW_END, false, true, TENANT);
    }

    @Test
    void actualTransactionFlagWithoutConnectionBindingRejectsBeforeGuardOrAnyCurrentRead() {
        Fixture fixture = new Fixture();
        /** 仅伪造旧实现信任的标记；没有实际连接绑定，不能获得事务资格。 */
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
            assertAll("未绑定连接必须先于依赖调用拒绝",
                () -> assertThrows(ServiceException.class, () -> fixture.read(TENANT, MATERIAL, WINDOW_END)),
                fixture::assertUntouched);
        }
    }

    @Test
    void disabledTenantRejectsBeforeGuardOrAnyCurrentRead() {
        assertDenied(TENANT, MATERIAL, WINDOW_END, true, false, TENANT);
    }

    @Test
    void missingOrDifferentCurrentOrRequestedTenantRejectsBeforeAnyDependency() {
        for (String current : Arrays.asList(null, "", " ", "other")) {
            assertDenied(TENANT, MATERIAL, WINDOW_END, true, true, current);
        }
        for (String request : Arrays.asList(null, "", " ", "T-100", "t-100 ")) {
            assertDenied(request, MATERIAL, WINDOW_END, true, true, TENANT);
        }
    }

    @Test
    void invalidMaterialOrSqlDateRejectsBeforeGuardOrMapper() {
        for (Long material : Arrays.asList(null, 0L, -1L)) {
            assertDenied(TENANT, material, WINDOW_END, true, true, TENANT);
        }
        for (LocalDate date : Arrays.asList(null, LocalDate.of(999, 12, 31), LocalDate.of(10000, 1, 1))) {
            assertDenied(TENANT, MATERIAL, date, true, true, TENANT);
        }
    }

    @Test
    void singleMaterialGuardPrecedesAllEightReadsInFixedOrder() throws Exception {
        Fixture fixture = populated();
        /** 头标识也必须稳定且去重，不能取决于遇到它们的先后顺序。 */
        Object earlierHead = row(REQUIREMENTS, "requirementId", 20L, "planId", 30L);
        Object extraDetail = row(DETAILS, "detailId", 12L, "requirementId", 20L);
        fixture.rows.put(DETAILS, List.of(fixture.rows.get(DETAILS).get(0), extraDetail));
        fixture.rows.put(REQUIREMENTS, List.of(earlierHead, fixture.rows.get(REQUIREMENTS).get(0)));
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            fixture.readInErpTransaction(TENANT, MATERIAL, WINDOW_END);
            List<String> expected = new ArrayList<>(List.of("guard"));
            expected.addAll(READ_ORDER);
            assertEquals(expected, fixture.trace);
            verify(fixture.guard).lock(TENANT, List.of(MATERIAL));
            query(DETAILS).invoke(verify(fixture.mapper), TENANT, MATERIAL, WINDOW_END);
            query(REQUIREMENTS).invoke(verify(fixture.mapper), TENANT, List.of(20L, 21L));
            query(STOCKS).invoke(verify(fixture.mapper), TENANT, MATERIAL);
            query(ORDER_LINES).invoke(verify(fixture.mapper), TENANT, MATERIAL);
            query(ORDERS).invoke(verify(fixture.mapper), TENANT, List.of(61L));
            query(REQUISITION_LINES).invoke(verify(fixture.mapper), TENANT, MATERIAL);
            query(REQUISITIONS).invoke(verify(fixture.mapper), TENANT, List.of(81L));
            query(ALLOCATIONS).invoke(verify(fixture.mapper), TENANT, MATERIAL);
            verifyNoMoreInteractions(fixture.guard, fixture.mapper);
        }
    }

    @Test
    void failedGuardPreventsAllSourceReads() {
        Fixture fixture = new Fixture();
        doThrow(new ServiceException("guard unavailable")).when(fixture.guard).lock(TENANT, List.of(MATERIAL));
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(ServiceException.class, () -> fixture.readInErpTransaction(TENANT, MATERIAL, WINDOW_END));
            verify(fixture.guard).lock(TENANT, List.of(MATERIAL));
            verifyNoInteractions(fixture.mapper);
        }
    }

    @Test
    void globalMrpKeepsOverdueExternalPlanAndUndatedRowsWithoutUsingHistoricalShortage() {
        Fixture fixture = populated();
        Object overdue = row(DETAILS,
            "detailId", 12L, "requirementId", 22L,
            "requiredDate", Timestamp.valueOf("2024-01-01 10:00:00"),
            "requiredQuantity", new BigDecimal("9"), "issuedQuantity", new BigDecimal("2"),
            "shortageQuantity", new BigDecimal("777"));
        Object external = row(REQUIREMENTS, "requirementId", 22L,
            "planId", 999L, "status", "CLOSED", "delFlag", "1");
        Object undated = row(DETAILS, "detailId", 13L, "requirementId", 21L);
        List<?> details = List.of(fixture.rows.get(DETAILS).get(0), overdue, undated);
        fixture.rows.put(DETAILS, details);
        fixture.rows.put(REQUIREMENTS, List.of(fixture.rows.get(REQUIREMENTS).get(0), external));
        Object pool = readValid(fixture);
        assertEquals(details, facts(pool, "requirementDetails"));
        assertEquals(999L, property(facts(pool, "requirementHeads").get(1), "planId"));
        assertEquals("CLOSED", property(facts(pool, "requirementHeads").get(1), "status"));
        assertNull(property(facts(pool, "requirementDetails").get(2), "requiredDate"));
        assertEquals(new BigDecimal("777"), property(facts(pool, "requirementDetails").get(1), "shortageQuantity"));
        assertEquals(new BigDecimal("9"), property(facts(pool, "requirementDetails").get(1), "requiredQuantity"));
    }

    @Test
    void rawNullQuantitiesUnitsStatusesAndDatesAreNeverDefaultedToZero() {
        Object pool = readValid(populated());
        Map<String, List<String>> nullable = Map.of(
            "requirementDetails", List.of("requiredQuantity", "issuedQuantity", "unit", "requiredDate", "status"),
            "requirementHeads", List.of("status"),
            "stocks", List.of("stockQuantity", "reservedQuantity", "baseUomId", "frozenFlag", "version"),
            "orderLines", List.of("unit", "orderQuantity", "receivedQuantity", "returnedQuantity", "receiveStatus"),
            "orderHeads", List.of("status", "expectedArrivalDate"),
            "requisitionLines", List.of("unit", "applyQuantity", "approvedQuantity", "requiredDate"),
            "requisitionHeads", List.of("status", "version"));
        nullable.forEach((collection, fields) -> fields.forEach(field ->
            assertNull(property(facts(pool, collection).get(0), field), collection + "." + field)));
    }

    @Test
    void orderPoolKeepsPastLateUnknownArrivalAndEveryHeaderState() {
        Fixture fixture = populated();
        List<Object> lines = new ArrayList<>();
        List<Object> heads = new ArrayList<>();
        String[] statuses = {"DRAFT", "CANCELLED", "APPROVED", null};
        for (int i = 0; i < statuses.length; i++) {
            lines.add(row(ORDER_LINES,
                "lineId", 51L + i, "orderId", 61L + i));
            heads.add(row(ORDERS, "orderId", 61L + i,
                "status", statuses[i], "expectedArrivalDate",
                i == 0 ? Timestamp.valueOf("2024-01-01 00:00:00")
                    : i == 1 ? Timestamp.valueOf("2030-01-01 00:00:00") : null));
        }
        fixture.rows.put(ORDER_LINES, lines);
        fixture.rows.put(ORDERS, heads);
        Object pool = readValid(fixture);
        assertEquals(lines, facts(pool, "orderLines"));
        assertEquals(heads, facts(pool, "orderHeads"));
    }

    @Test
    void legacySourceTypeAndSourceIdAreNotReinterpretedAsDetailIdentity() {
        Fixture fixture = populated();
        List<Object> lines = new ArrayList<>();
        String[] types = {"AI_DECISION", "LOW_STOCK", "MRP"};
        Long[] ids = {2105159630382362626L, MATERIAL, 11L};
        for (int i = 0; i < types.length; i++) {
            lines.add(row(REQUISITION_LINES,
                "lineId", 71L + i, "requisitionId", 81L,
                "sourceType", types[i], "sourceId", ids[i]));
        }
        fixture.rows.put(REQUISITION_LINES, lines);
        List<?> returned = facts(readValid(fixture), "requisitionLines");
        assertEquals(lines, returned);
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(types[i], property(returned.get(i), "sourceType"));
            assertEquals(ids[i], property(returned.get(i), "sourceId"));
        }
        assertTrue(Arrays.stream(returned.get(0).getClass().getMethods()).noneMatch(method ->
            method.getName().equals("getDetailId") || method.getName().equals("detailId")),
            "Legacy source_id is not an explicit MRP detail_id");
    }

    @Test
    void releasedAllocationsKeepAuditIdentityVersionAndAmounts() {
        Fixture fixture = populated();
        Object released = row(ALLOCATIONS,
            "allocationId", 92L, "detailId", 11L, "requisitionLineId", 71L, "orderLineId", 51L,
            "allocationStatus", "RELEASED", "allocationKey", "do-not-reuse", "version", 7,
            "releasedBaseQty", new BigDecimal("3"), "releaseReason", "verified cancellation",
            "releasedAt", Timestamp.valueOf("2026-09-29 10:00:00"));
        fixture.rows.put(ALLOCATIONS, List.of(released));
        Object returned = facts(readValid(fixture), "allocations").get(0);
        assertEquals(released, returned);
        assertEquals("RELEASED", property(returned, "allocationStatus"));
        assertEquals("do-not-reuse", property(returned, "allocationKey"));
        assertEquals(7, property(returned, "version"));
        assertEquals(new BigDecimal("3"), property(returned, "releasedBaseQty"));
    }

    @Test
    void missingForeignOrUnknownTenantOnEveryKindOfHeadRejectsWholePool() {
        for (String query : List.of(REQUIREMENTS, ORDERS, REQUISITIONS)) {
            for (String problem : List.of("missing", "foreign", "unknown")) {
                Fixture fixture = populated();
                if (problem.equals("missing")) {
                    fixture.rows.put(query, List.of());
                } else {
                    set(fixture.rows.get(query).get(0), "tenantId", problem.equals("foreign") ? "other" : null);
                }
                try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
                    assertThrows(ServiceException.class, () -> fixture.readInErpTransaction(TENANT, MATERIAL, WINDOW_END), query + problem);
                    assertTrue(fixture.trace.contains(query), "关联头身份拒绝必须确实到达该来源当前读");
                }
            }
        }
    }

    @Test
    void everySqlTableHasExplicitTenantCurrentReadAndPrimaryKeyOrderWithNoWriteStatements() {
        Class<?> mapper = requiredClass(MAPPER);
        assertNotNull(mapper.getAnnotation(Mapper.class));
        assertEquals(Set.copyOf(READ_ORDER), Arrays.stream(mapper.getDeclaredMethods())
            .map(Method::getName).collect(Collectors.toSet()));
        for (String name : READ_ORDER) {
            Method query = query(name);
            String sql = sql(name);
            assertTrue(sql.contains("from " + TABLES.get(name)), name);
            assertTrue(sql.contains("tenant_id = #{tenantid}"), name);
            assertTrue(Pattern.compile("order by (?:\\w+\\.)?" + PRIMARY_KEYS.get(name)
                + "(?: asc)? for update").matcher(sql).find(), name);
            assertNull(query.getAnnotation(Insert.class));
            assertNull(query.getAnnotation(Update.class));
            assertNull(query.getAnnotation(Delete.class));
            assertFalse(Pattern.compile("\\bjoin\\b").matcher(sql).find(), "Heads must not erase unknown source rows");
        }
    }

    @Test
    void sqlScopeHasNoRunWindowLowerBoundStatusGuessCoalesceOrLimit() {
        for (String name : READ_ORDER) {
            String sql = sql(name);
            String predicate = sql.substring(sql.indexOf("where "));
            assertFalse(sql.contains("coalesce") || sql.contains("greatest(") || sql.contains("available_quantity")
                || Pattern.compile("\\blimit\\b").matcher(sql).find(), name);
            assertFalse(predicate.contains("windowstart") || predicate.contains("runid")
                || predicate.contains("work_order_id") || predicate.contains("plan_id"), name);
            assertFalse(Pattern.compile("\\b(?:status|receive_status|allocation_status)\\s*(?:=|in\\b|<>|!=)")
                .matcher(predicate).find(), name);
            if (!name.endsWith("Heads")) {
                assertTrue(predicate.contains("material_id = #{materialid}"), name);
            }
            assertFalse(predicate.contains("quantity") || predicate.contains("source_type")
                || predicate.contains("source_id") || predicate.contains("unit"), name);
            if (name.endsWith("Heads")) {
                assertFalse(predicate.contains("del_flag"), "Deleted/unknown heads must remain raw facts");
            } else if (!name.equals(ALLOCATIONS)) {
                assertTrue(predicate.contains("del_flag = '0'"), "Exclude deleted source rows: " + name);
                assertTrue(predicate.contains("del_flag is null"), "Unknown NULL delete marker must remain: " + name);
                assertTrue(predicate.contains("del_flag not in ('0', '1')"), "Unknown noncanonical delete marker must remain: " + name);
            }
            if (!name.equals(DETAILS)) {
                assertFalse(predicate.contains("required_date") || predicate.contains("expected_arrival_date"), name);
            }
        }
        String mrp = sql(DETAILS);
        assertTrue(mrp.contains("required_date is null"), "Undated demand must not disappear");
        assertTrue(mrp.contains("date(required_date) <= #{windowend}"), "Include the entire window-end business day");
        assertFalse(Pattern.compile("required_date\\)?\\s*(?:>=|>|between\\b)").matcher(mrp).find());
    }

    private static void assertDenied(String request, Long material, LocalDate date,
                                     boolean active, boolean enabled, String current) {
        Fixture fixture = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(enabled, current)) {
            assertThrows(ServiceException.class, () -> {
                if (active) {
                    fixture.readInErpTransaction(request, material, date);
                } else {
                    fixture.read(request, material, date);
                }
            });
            fixture.assertUntouched();
        }
    }

    private static Fixture populated() {
        Fixture fixture = new Fixture();
        fixture.rows.put(DETAILS, List.of(row(DETAILS,
            "detailId", 11L, "requirementId", 21L)));
        fixture.rows.put(REQUIREMENTS, List.of(row(REQUIREMENTS,
            "requirementId", 21L, "planId", 31L)));
        fixture.rows.put(STOCKS, List.of(row(STOCKS,
            "stockId", 41L)));
        fixture.rows.put(ORDER_LINES, List.of(row(ORDER_LINES,
            "lineId", 51L, "orderId", 61L)));
        fixture.rows.put(ORDERS, List.of(row(ORDERS,
            "orderId", 61L)));
        fixture.rows.put(REQUISITION_LINES, List.of(row(REQUISITION_LINES,
            "lineId", 71L, "requisitionId", 81L)));
        fixture.rows.put(REQUISITIONS, List.of(row(REQUISITIONS,
            "requisitionId", 81L)));
        fixture.rows.put(ALLOCATIONS, List.of(row(ALLOCATIONS,
            "allocationId", 91L, "detailId", 11L,
            "requisitionLineId", 71L, "allocationStatus", "RELEASED")));
        return fixture;
    }

    private static Object readValid(Fixture fixture) {
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            return fixture.readInErpTransaction(TENANT, MATERIAL, WINDOW_END);
        }
    }

    private static MockedStatic<TenantHelper> tenant(boolean enabled, String current) {
        MockedStatic<TenantHelper> mocked = mockStatic(TenantHelper.class);
        mocked.when(TenantHelper::isEnable).thenReturn(enabled);
        mocked.when(TenantHelper::getTenantId).thenReturn(current);
        return mocked;
    }

    private static Class<?> requiredClass(String name) {
        return assertDoesNotThrow(() -> Class.forName(name), "Missing approved raw current-read foundation: " + name);
    }

    private static Method entry() {
        return assertDoesNotThrow(() -> requiredClass(READER).getMethod("materialPool", String.class, Long.class, LocalDate.class));
    }

    private static Method query(String name) {
        Class<?> mapper = requiredClass(MAPPER);
        Class<?>[] arguments = name.equals(DETAILS)
            ? new Class<?>[]{String.class, Long.class, LocalDate.class}
            : name.endsWith("Heads") ? new Class<?>[]{String.class, List.class} : new Class<?>[]{String.class, Long.class};
        return assertDoesNotThrow(() -> mapper.getMethod(name, arguments));
    }

    private static String sql(String name) {
        Select select = query(name).getAnnotation(Select.class);
        assertNotNull(select, name);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Object row(String queryName, Object... properties) {
        Class<?> type = (Class<?>) ((ParameterizedType) query(queryName).getGenericReturnType()).getActualTypeArguments()[0];
        Object bean = assertDoesNotThrow(() -> type.getConstructor().newInstance());
        set(bean, "tenantId", TENANT);
        if (!queryName.endsWith("Heads")) {
            set(bean, "materialId", MATERIAL);
        }
        if (!queryName.equals(ALLOCATIONS)) {
            set(bean, "delFlag", "0");
        }
        for (int i = 0; i < properties.length; i += 2) {
            set(bean, (String) properties[i], properties[i + 1]);
        }
        return bean;
    }

    private static void set(Object bean, String property, Object value) {
        String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        Method method = Arrays.stream(bean.getClass().getMethods())
            .filter(candidate -> candidate.getName().equals(setter) && candidate.getParameterCount() == 1)
            .findFirst().orElse(null);
        assertNotNull(method, bean.getClass().getSimpleName() + "." + setter);
        assertDoesNotThrow(() -> method.invoke(bean, value));
    }

    private static Object property(Object bean, String property) {
        return assertDoesNotThrow(() -> {
            Method method;
            try {
                method = bean.getClass().getMethod(property);
            } catch (NoSuchMethodException ignored) {
                method = bean.getClass().getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
            }
            return method.invoke(bean);
        });
    }

    private static List<?> facts(Object pool, String property) {
        return (List<?>) property(pool, property);
    }

    private static final class Fixture {
        private final Map<String, List<?>> rows = new HashMap<>();
        private final List<String> trace = new ArrayList<>();
        private final PuRequirementMaterialLockService guard = mock(PuRequirementMaterialLockService.class);
        private final PuRequirementErpTransactionQualificationTest.Fixture transaction =
            assertDoesNotThrow(PuRequirementErpTransactionQualificationTest.Fixture::new);
        private final Object mapper;
        private final Object reader;

        private Fixture() {
            Class<?> mapperType = requiredClass(MAPPER);
            mapper = mock(mapperType, invocation -> {
                String name = invocation.getMethod().getName();
                if (!READ_ORDER.contains(name)) {
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
                }
                trace.add(name);
                return rows.getOrDefault(name, List.of());
            });
            doAnswer(invocation -> {
                trace.add("guard");
                return Map.of(MATERIAL, 0);
            }).when(guard).lock(anyString(), anyCollection());
            reader = assertDoesNotThrow(() -> requiredClass(READER)
                .getConstructor(mapperType, PuRequirementMaterialLockService.class, PuRequirementErpTransactionQualification.class)
                .newInstance(mapper, guard, new PuRequirementErpTransactionQualification(transaction.sqlSessionFactory())));
        }

        private Object readInErpTransaction(String tenant, Long material, LocalDate windowEnd) {
            Supplier<Object> body = () -> read(tenant, material, windowEnd);
            return transaction.inBoundErpTransactionWithoutTenant(false, body);
        }

        private Object read(String tenant, Long material, LocalDate windowEnd) {
            try {
                return entry().invoke(reader, tenant, material, windowEnd);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure.getCause() instanceof Error error) {
                    throw error;
                }
                throw new AssertionError("Unexpected checked reader exception", failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Reader contract reflection failed", failure);
            }
        }

        private void assertUntouched() {
            assertEquals(List.of(), trace);
            verifyNoInteractions(guard, mapper);
        }
    }
}

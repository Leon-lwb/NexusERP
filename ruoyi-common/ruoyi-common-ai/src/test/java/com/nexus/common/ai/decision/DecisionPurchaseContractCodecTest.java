package com.nexus.common.ai.decision;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2.Line;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.core.exception.ServiceException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;

@Tag("local")
@Tag("dev")
@Tag("prod")
class DecisionPurchaseContractCodecTest {
    private static final Long RUN = 9007199254740993L;
    private static final Long MATERIAL = 9007199254740995L;
    private static final Long FIRST_DETAIL = 9007199254741001L;
    private static final Long SECOND_DETAIL = 9007199254741002L;
    private static final LocalDate WINDOW = LocalDate.of(2026, 10, 31);
    private static final Set<String> FIELDS = Set.of("purchase_payload_version", "runId", "materialId", "vendorId",
        "windowEnd", "materialVersion", "vendorVersion", "supplierTermsId", "supplierTermsVersion",
        "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion", "conversionVersion",
        "requirement_detail_ids", "frozenLines", "expectedSourceFingerprint");
    private static final Set<String> LINE_FIELDS = Set.of("detailId", "baseUomId", "coveredDemandBase", "orderUomId",
        "requestedOrderQty", "requiredDate");
    private static final List<String> ID_FIELDS = List.of("runId", "materialId", "vendorId", "supplierTermsId",
        "baseUomId", "orderUomId");
    private static final List<String> VERSION_FIELDS = List.of("materialVersion", "vendorVersion",
        "supplierTermsVersion", "baseUomVersion", "orderUomVersion", "conversionVersion");
    private static final DecisionActor ACTOR = new DecisionActor("000001", 7L, "测试用户",
        Set.of("ai:decision:execute", "pu:requisition:add"));
    private static final DecisionBusinessRef TARGET = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    // 仅用于旧摘要对照；新 v2 动作统一调用公共有界键方法。
    private static final String KEY = "000001:9007199254740993:CREATE_PURCHASE_REQUISITION_DRAFT:WORK_ORDER:11"
        + ":material:9007199254740995:vendor:31";

    private static final String BOUNDED_KEY = "AI_PURCHASE_V2:bb0f603b99852633376d0798096a89bc3bf24a8d2ab67c4c8c3168b374f46fb5";

    @Test
    void roundTripsExactExistingV2WireSchema() {
        Map<String, Object> payload = DecisionPurchaseContractCodec.toPayload(RUN, contract());
        assertEquals(wire(), payload, "必须原样采用既有逐来源 v2 wire 字段");
        assertEquals(FIELDS, payload.keySet(), "顶层字段不得漂移");
        assertInstanceOf(Integer.class, payload.get("purchase_payload_version"), "版本必须为整数类型");
        for (String field : ID_FIELDS) assertInstanceOf(String.class, payload.get(field), "动态标识必须为十进制字符串：" + field);
        for (Object row : (List<?>) payload.get("frozenLines")) {
            assertEquals(LINE_FIELDS, ((Map<?, ?>) row).keySet(), "冻结行字段不得漂移");
            ((Map<?, ?>) row).values().forEach(value -> assertInstanceOf(String.class, value, "行标识、数量及日期必须为字符串"));
        }
        assertEquals(contract(), DecisionPurchaseContractCodec.parse(payload, RUN), "完整合同必须能够精确往返");
        assertEquals(contract(), DecisionPurchaseContractCodec.parse(payload, null), "没有外部运行身份时仍严格解析载荷身份");
    }

    @Test
    void defensivelyCopiesFrozenLists() {
        var ids = new ArrayList<>(contract().requirementDetailIds());
        var lines = new ArrayList<>(contract().frozenLines());
        DecisionPurchaseContractV2 frozen = withLists(ids, lines);
        ids.clear(); lines.clear();
        assertEquals(2, frozen.requirementDetailIds().size(), "调用者修改来源列表不得影响冻结合同");
        assertEquals(2, frozen.frozenLines().size(), "调用者修改行列表不得影响冻结合同");
        assertThrows(UnsupportedOperationException.class, () -> frozen.requirementDetailIds().clear(), "合同来源列表必须不可变");
        assertThrows(UnsupportedOperationException.class, () -> frozen.frozenLines().clear(), "合同行列表必须不可变");
    }

    @Test
    void detachesParsedContractFromMutableWire() {
        Map<String, Object> payload = wire();
        DecisionPurchaseContractV2 parsed = DecisionPurchaseContractCodec.parse(payload, RUN);
        row(payload, 0).put("coveredDemandBase", "99");
        ((List<?>) payload.get("requirement_detail_ids")).clear(); payload.clear();
        assertEquals(contract(), parsed, "解析结果不得引用动态载荷的可变容器");
    }

    @Test
    void producesDeeplyImmutableWire() {
        Map<String, Object> payload = DecisionPurchaseContractCodec.toPayload(RUN, contract());
        assertThrows(UnsupportedOperationException.class, () -> payload.clear(), "编码后的顶层字段必须不可变");
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) payload.get("requirement_detail_ids")).clear(), "编码后的标识集合必须不可变");
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) payload.get("frozenLines")).clear(), "编码后的行集合必须不可变");
        assertThrows(UnsupportedOperationException.class, () -> row(payload, 0).clear(), "编码后的行字段必须不可变");
    }

    @Test
    void survivesJsonAndActionPayloadWithoutNumericIds() throws Exception {
        var mapper = new ObjectMapper();
        Map<String, Object> payload = DecisionPurchaseContractCodec.toPayload(RUN, contract());
        Map<String, Object> decoded = mapper.readValue(mapper.writeValueAsString(payload), new TypeReference<>() { });
        assertEquals(contract(), DecisionPurchaseContractCodec.parse(decoded, RUN), "普通 JSON 往返不能丢失雪花 ID");
        assertEquals(payload, action(decoded).payload(), "既有动作防御复制不能改变 v2 数值类型");
    }

    @Test
    void rejectsNonIntegerVersionAndExtraLegacyFields() {
        for (Object version : Arrays.asList("2", 2L, 2.0, new BigDecimal("2"), (short) 2, true, null, 1, 3)) {
            Map<String, Object> payload = wire(); payload.put("purchase_payload_version", version);
            rejected(payload, "版本必须恰为 Integer 2，不得强制转换");
        }
        for (String extra : List.of("materialLines", "requiredDate", "verified", "signing", "certificate", "unknown")) {
            Map<String, Object> payload = wire(); payload.put(extra, true);
            rejected(payload, "不得夹带旧字段或证明旗号：" + extra);
        }
    }

    @Test
    void rejectsEveryMissingOrNullTopLevelField() {
        for (String field : FIELDS) {
            Map<String, Object> missing = wire(); missing.remove(field);
            rejected(missing, "必须拒绝缺失字段：" + field);
            Map<String, Object> absent = wire(); absent.put(field, null);
            rejected(absent, "必须拒绝空字段：" + field);
        }
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.parse(null, RUN), "空载荷必须失败关闭");
    }

    @Test
    void rejectsMissingExtraOrNonObjectLineFields() {
        for (String field : LINE_FIELDS) {
            Map<String, Object> payload = wire(); row(payload, 0).remove(field);
            rejected(payload, "必须拒绝不完整冻结行：" + field);
            payload = wire(); row(payload, 0).put(field, null);
            rejected(payload, "必须拒绝空行字段：" + field);
        }
        Map<String, Object> payload = wire(); row(payload, 0).put("amount", "1.25");
        rejected(payload, "不得把金额或客户端数量混入冻结行");
        payload = wire(); payload.put("frozenLines", List.of("非对象", "非对象"));
        rejected(payload, "冻结行必须为对象");
    }

    @Test
    void rejectsDuplicateUnsortedOrDifferentLineIds() {
        for (List<String> ids : List.of(List.of(FIRST_DETAIL.toString(), FIRST_DETAIL.toString()),
            List.of(SECOND_DETAIL.toString(), FIRST_DETAIL.toString()), List.of(FIRST_DETAIL.toString(), "9007199254741003"))) {
            Map<String, Object> payload = wire(); payload.put("requirement_detail_ids", ids);
            rejected(payload, "来源集合必须唯一、排序且逐项匹配冻结行");
        }
        Map<String, Object> payload = wire(); row(payload, 1).put("detailId", FIRST_DETAIL.toString());
        rejected(payload, "重复冻结行不能认证");
        payload = wire(); Collections.reverse((List<?>) payload.get("frozenLines"));
        rejected(payload, "冻结行不能按另一顺序解释来源");
    }

    @Test
    void rejectsEmptyOversizedOrMismatchedLists() {
        for (Object ids : List.of(List.of(), Set.of(FIRST_DETAIL), "错误集合", Collections.nCopies(1001, FIRST_DETAIL.toString()))) {
            Map<String, Object> payload = wire(); payload.put("requirement_detail_ids", ids);
            rejected(payload, "来源必须为非空且不超过一千项的有序列表");
        }
        for (Object lines : List.of(List.of(), List.of(wireLine(FIRST_DETAIL, "1.25", 7)), "错误集合")) {
            Map<String, Object> payload = wire(); payload.put("frozenLines", lines);
            rejected(payload, "冻结行数量必须与来源集合一致");
        }
    }

    @Test
    void rejectsLossyIdsAndDecimalOverflow() {
        List<Object> invalidIds = Arrays.asList(9007199254740993L, 9007199254740992d, BigInteger.valueOf(1),
            "0", "-1", "+1", "01", "1e3", " 1", "1 ", "单位名称", "9223372036854775808", "99999999999999999999");
        for (String field : ID_FIELDS) for (Object id : invalidIds) {
            Map<String, Object> payload = wire(); payload.put(field, id);
            rejected(payload, "标识必须为未丢失精度的正 Long 十进制字符串：" + field);
        }
        for (Object qty : Arrays.asList("100000000000000", "99999999999999.99999", "0", "0.0000", "-1", "+1",
            "01", "1e2", ".1", "1.", "1.00000", " 1", "金额元", 1, new BigDecimal("1"), Double.NaN)) {
            Map<String, Object> payload = wire(); row(payload, 0).put("coveredDemandBase", qty);
            rejected(payload, "数量必须为正且满足精确 DECIMAL(18,4) 字符串");
            payload = wire(); row(payload, 0).put("requestedOrderQty", qty);
            rejected(payload, "订购量不得混用金额或浮点数");
        }
    }

    @Test
    void rejectsLossyNestedIds() {
        for (Object id : Arrays.asList(1L, 1.0, "01", "0", "9223372036854775808", null)) {
            Map<String, Object> payload = wire(); payload.put("requirement_detail_ids", Arrays.asList(id, SECOND_DETAIL.toString()));
            rejected(payload, "嵌套来源标识也必须为正十进制字符串");
            for (String field : List.of("detailId", "baseUomId", "orderUomId")) {
                payload = wire(); row(payload, 0).put(field, id);
                rejected(payload, "行单位及来源标识不得绕过全局 ID 约束");
            }
        }
    }

    @Test
    void acceptsLongAndDecimalBoundaryWithoutPrecisionLoss() {
        Map<String, Object> payload = wire(); payload.put("materialId", Long.toString(Long.MAX_VALUE));
        row(payload, 0).put("coveredDemandBase", "99999999999999.9999");
        row(payload, 0).put("requestedOrderQty", "99999999999999.9999");
        DecisionPurchaseContractV2 parsed = DecisionPurchaseContractCodec.parse(payload, RUN);
        assertEquals(Long.MAX_VALUE, parsed.materialId(), "Long 边界不得转成浮点数");
        assertEquals(new BigDecimal("99999999999999.9999"), parsed.frozenLines().get(0).coveredDemandBase(), "原生数量精度必须保留");
    }

    @Test
    void normalizesExactDecimalTextWithoutRounding() {
        var lines = new ArrayList<>(contract().frozenLines());
        lines.set(0, new Line(FIRST_DETAIL, 17L, new BigDecimal("1.2500"), 17L, new BigDecimal("1.250"), LocalDate.of(2026, 10, 7)));
        Map<String, Object> payload = DecisionPurchaseContractCodec.toPayload(RUN, withLists(contract().requirementDetailIds(), lines));
        assertEquals("1.25", row(payload, 0).get("coveredDemandBase"), "编码必须消除无意义的小数尾零");
        assertEquals("1.25", row(payload, 0).get("requestedOrderQty"), "不能通过舍入改变冻结意图");
        lines.set(0, new Line(FIRST_DETAIL, 17L, new BigDecimal("1E+2"), 17L, new BigDecimal("100.0000"), LocalDate.of(2026, 10, 7)));
        assertEquals("100", row(DecisionPurchaseContractCodec.toPayload(RUN, withLists(contract().requirementDetailIds(), lines)), 0).get("coveredDemandBase"), "不能输出指数编码");
    }

    @Test
    void rejectsDifferentUnitsVersionsOrOverCoverage() {
        for (String field : List.of("orderUomId", "baseUomVersion", "orderUomVersion", "conversionVersion")) {
            Map<String, Object> payload = wire(); payload.put(field, field.endsWith("Id") ? "18" : "不同版本");
            rejected(payload, "首包仅支持同根基本单位和同换算版本");
        }
        for (String field : List.of("baseUomId", "orderUomId")) {
            Map<String, Object> payload = wire(); row(payload, 0).put(field, "18");
            rejected(payload, "行单位必须与头单位一致");
        }
        Map<String, Object> payload = wire(); row(payload, 0).put("requestedOrderQty", "1.2501");
        rejected(payload, "不得超额或在合同内凑包装数量");
        payload = wire(); row(payload, 0).put("requestedOrderQty", "1.2499");
        rejected(payload, "订购量必须与逐来源覆盖量相等");
    }

    @Test
    void rejectsMalformedBusinessDatesAndDatesOutsideWindow() {
        for (Object date : Arrays.asList("2026-2-03", "2026-02-30", "2026-10-06T00:00:00", "0999-12-31",
            "+10000-01-01", " 2026-10-07", LocalDate.of(2026, 10, 7), null)) {
            Map<String, Object> payload = wire(); payload.put("windowEnd", date);
            rejected(payload, "窗口日期必须为规范有效业务日期");
            payload = wire(); row(payload, 0).put("requiredDate", date);
            rejected(payload, "来源日期必须为规范有效业务日期");
        }
        Map<String, Object> payload = wire(); row(payload, 0).put("requiredDate", "2026-11-01");
        rejected(payload, "来源日期不能晚于冻结窗口");
    }

    @Test
    void rejectsInvalidVersionsWithoutInventingNewFlags() {
        for (String field : VERSION_FIELDS) for (Object version : Arrays.asList("", " ", " v1", "v1 ", "v\n1", "x".repeat(257), 1, null)) {
            Map<String, Object> payload = wire(); payload.put(field, version);
            rejected(payload, "真实冻结版本必须为有界无控制字符文本：" + field);
        }
    }

    @Test
    void rejectsMissingOrInvalidSourceFingerprint() {
        for (Object hash : Arrays.asList("a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), " a".repeat(32), 123, null)) {
            Map<String, Object> payload = wire(); payload.put("expectedSourceFingerprint", hash);
            rejected(payload, "来源摘要必须恰为小写 SHA-256 十六进制文本");
        }
    }

    @Test
    void bindsOptionalExpectedRunIdWithoutUpgradingOldRuns() {
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.parse(wire(), RUN + 1), "其他 run 的合同不能借用");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.parse(wire(), 0L), "外部运行身份必须为正 ID");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(null, contract()), "新编码必须指定运行身份");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(0L, contract()), "不能生成零运行身份");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(RUN, null), "不能为空合同生成版本标记");
    }

    @Test
    void rejectsInvalidTypedIntentBeforeEncoding() {
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(RUN, withLists(List.of(SECOND_DETAIL, FIRST_DETAIL), contract().frozenLines())), "编码也不能偷偷排序无效来源");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(RUN, withLists(null, contract().frozenLines())), "缺失来源不得编码");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(RUN, withLists(contract().requirementDetailIds(), null)), "缺失冻结行不得编码");
        for (BigDecimal qty : Arrays.asList(BigDecimal.ZERO, new BigDecimal("1.00001"), new BigDecimal("100000000000000"), null)) {
            var lines = new ArrayList<>(contract().frozenLines());
            lines.set(0, new Line(FIRST_DETAIL, 17L, qty, 17L, qty, LocalDate.of(2026, 10, 7)));
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.toPayload(RUN, withLists(contract().requirementDetailIds(), lines)), "编码前必须拒绝无效数量，不能舍入");
        }
    }

    @Test
    void exposesExactNineFieldWriterMetadata() {
        Map<String, String> metadata = DecisionPurchaseContractCodec.contractMetadata(contract());
        assertEquals(Map.of("materialVersion", "material-v1", "vendorVersion", "vendor-v1", "supplierTermsId", "29",
            "supplierTermsVersion", "terms-v1", "baseUomId", "17", "baseUomVersion", "uom-v1", "orderUomId", "17",
            "orderUomVersion", "uom-v1", "conversionVersion", "uom-v1"), metadata, "writer 永久绑定的九项 metadata 必须完整且同名");
        assertThrows(UnsupportedOperationException.class, () -> metadata.clear(), "metadata 不得使用可变容器");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.contractMetadata(null), "不得为空合同补造 metadata");
    }

    @Test
    void keepsExistingLengthPrefixedSha256GoldenFingerprint() {
        assertEquals("bf4036b389ff5101ac4ef23263ec9422ff5cf2aaca22c316309f8d1c9c782cf3",
            DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), ACTOR), "必须兼容旧适配器的长度前缀摘要，不能换成 JSON 哈希");
    }

    @Test
    void fingerprintIgnoresMapOrderAndDecimalTrailingZeros() {
        Map<String, Object> payload = wire();
        var fields = new ArrayList<>(payload.keySet()); Collections.reverse(fields);
        var reversed = new LinkedHashMap<String, Object>(); fields.forEach(field -> reversed.put(field, payload.get(field)));
        row(reversed, 0).put("coveredDemandBase", "1.2500"); row(reversed, 0).put("requestedOrderQty", "1.250");
        assertEquals(DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), ACTOR),
            DecisionPurchaseContractCodec.payloadFingerprint(action(reversed), ACTOR), "容器顺序和数量尾零不能改变永久摘要");
    }

    @Test
    void fingerprintBindsEveryScalarAndLineValue() {
        String expected = DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), ACTOR);
        for (String field : List.of("runId", "materialId", "vendorId", "supplierTermsId", "windowEnd", "materialVersion", "vendorVersion", "supplierTermsVersion", "expectedSourceFingerprint")) {
            Map<String, Object> payload = wire();
            payload.put(field, field.endsWith("Id") ? "901" : field.equals("windowEnd") ? "2026-11-01"
                : field.equals("expectedSourceFingerprint") ? "b".repeat(64) : "另一版本");
            assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(payload), ACTOR), "顶层合同必须参与摘要：" + field);
        }
        Map<String, Object> payload = wire(); row(payload, 0).put("coveredDemandBase", "1.26"); row(payload, 0).put("requestedOrderQty", "1.26");
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(payload), ACTOR), "逐来源数量必须参与摘要");
        payload = wire(); row(payload, 0).put("requiredDate", "2026-10-09");
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(payload), ACTOR), "逐来源日期必须参与摘要");
    }

    @Test
    void fingerprintBindsActorActionRequestAndTarget() {
        String expected = DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), ACTOR);
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), new DecisionActor("000002", 7L, "测试用户", ACTOR.permissions())), "租户身份必须参与摘要");
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), new DecisionActor("000001", 8L, "测试用户", ACTOR.permissions())), "用户身份必须参与摘要");
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 102L, TARGET, KEY), ACTOR), "动作身份必须参与摘要");
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 101L, TARGET, KEY + ":other"), ACTOR), "永久键必须参与摘要");
        assertNotEquals(expected, DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 101L, new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 12L, "WO-12"), KEY), ACTOR), "根目标必须参与摘要");
    }

    @Test
    void fingerprintValidatesWireAndIdentityBeforeHashing() {
        Map<String, Object> legacy = wire(); legacy.put("materialLines", List.of());
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(action(legacy), ACTOR), "不能给旧载荷生成新版摘要");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(null, ACTOR), "缺失动作必须失败关闭");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), null), "缺失身份不能散列");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(action(wire()), new DecisionActor("000001", 0L, "测试用户", Set.of())), "用户 ID 必须为正");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 0L, TARGET, KEY), ACTOR), "动作 ID 必须为正");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 101L, null, KEY), ACTOR), "摘要不能省略目标");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 101L, TARGET, ""), ACTOR), "摘要不能省略永久请求键");
    }

    @Test
    void fingerprintSupportsExistingNullableHorizonTargetWithoutExpandingKeyScope() {
        var target = new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null);
        assertTrue(DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 101L, target, KEY), ACTOR).matches("[0-9a-f]{64}"), "既有摘要算法的空 horizon 身份仍须稳定编码为空串");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, target, contract()), "首包采购键不能扩大到 horizon 根");
    }

    @Test
    void idempotencyKeyUsesBoundedV2HashAndBindsMaterialVendor() {
        assertEquals(BOUNDED_KEY, DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, contract()), "采购键必须使用固定前缀和完整长度前缀摘要");
        Map<String, Object> payload = wire(); payload.put("materialId", "901");
        String material = DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, DecisionPurchaseContractCodec.parse(payload, RUN));
        payload = wire(); payload.put("vendorId", "902");
        String vendor = DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, DecisionPurchaseContractCodec.parse(payload, RUN));
        assertNotEquals(BOUNDED_KEY, material, "同 run 不同物料不得共用永久键");
        assertNotEquals(BOUNDED_KEY, vendor, "同物料不同供应商不得共用永久键");
        assertNotEquals(material, vendor, "键必须同时绑定物料与供应商");
    }

    @Test
    void idempotencyKeyRejectsNonWorkOrderAndInvalidIds() {
        for (DecisionBusinessType type : DecisionBusinessType.values()) if (type != DecisionBusinessType.WORK_ORDER) {
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
                new DecisionBusinessRef(type, type == DecisionBusinessType.SHORTAGE_HORIZON ? null : 11L, null), contract()), "首包只允许工单采购键");
        }
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", id, TARGET, contract()), "运行身份必须为正 ID");
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
                new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, id, null), contract()), "目标必须有正工单 ID");
        }
        for (String tenant : Arrays.asList(null, "", "00000", "000001:other", "租户名称", "9".repeat(21))) {
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey(tenant, RUN, TARGET, contract()), "租户身份必须为有界十进制代码");
        }
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, null, contract()), "不能缺失采购根");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, null), "不能缺失采购合同身份");
    }

    @Test
    void idempotencyKeyAcceptsFullLongIdsWithoutTruncation() {
        Map<String, Object> payload = wire(); payload.put("materialId", Long.toString(Long.MAX_VALUE)); payload.put("vendorId", Long.toString(Long.MAX_VALUE));
        String key = DecisionPurchaseContractCodec.idempotencyKey("9".repeat(20), Long.MAX_VALUE,
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, Long.MAX_VALUE, null), DecisionPurchaseContractCodec.parse(payload, RUN));
        assertEquals(79, key.length(), "完整合法身份必须产生固定有界键，不扩大现有数据库列");
        assertTrue(key.length() <= 128, "极大身份也必须满足实际持久化长度约束");
        assertEquals("AI_PURCHASE_V2:73203609bb4891661d140d058fe63dd3e08b28efe272b5358492e7209efcb68d", key, "前像中的所有极大标识必须完整参与摘要，不能截断或浮点化");
        assertTrue(DecisionPurchaseContractCodec.payloadFingerprint(action(wire(), 101L, TARGET, key), ACTOR).matches("[0-9a-f]{64}"), "摘要必须接受完整的新采购永久键");
    }

    @Test
    void sourceChangedIsDistinctFromUnavailableOrUnauthorized() throws Exception {
        Class<?> type = Class.forName(DecisionPurchaseContractCodec.class.getName() + "$SourceChangedException");
        Throwable changed = (Throwable) type.getConstructor().newInstance();
        assertInstanceOf(RuntimeException.class, changed, "来源变化必须是内部运行时业务信号，不是系统错误");
        assertEquals(RuntimeException.class, type.getSuperclass(), "按批准方案直接继承运行时异常，不改动 final 业务异常基类");
        assertEquals("采购冻结来源已变化，须重新分析", changed.getMessage(), "来源变化必须使用固定安全中文消息");
        assertFalse(type.isInstance(new ServiceException("采购来源读取不可证明")), "读取失败不得伪装成已确认来源变化");
        assertFalse(type.isInstance(new ServiceException("没有采购审批权限")), "权限错误不得伪装成来源变化");
        assertEquals(1, type.getConstructors().length, "不得公开任意消息或原因的来源变化构造器");
    }

    @Test
    void newIndustrialPortMethodsFailClosedWithoutChangingExistingContracts() throws Throwable {
        assertDefaultFailure("freezePurchaseContracts", "服务端采购合同冻结端口尚未实现", DeliveryContextSnapshot.class, int.class, DecisionActor.class);
        assertDefaultFailure("requireFreshPurchaseContract", "采购合同新鲜度端口尚未实现", DecisionActionProposal.class, DeliveryContextSnapshot.class, int.class, DecisionActor.class);
        assertDefaultFailure("recoverPurchaseAction", "采购提交恢复端口尚未实现", DecisionActionProposal.class, DecisionActor.class);
        assertEquals(12, IndustrialDecisionDataPort.class.getDeclaredMethods().length, "新增默认接口不得删改九项原有契约");
    }

    @Test
    void idempotencyKeySeparatesAmbiguousFieldBoundaries() {
        assertEquals("000001" + "11", "0000011" + "1", "测试前像必须确有无长度编码时的边界歧义");
        String first = DecisionPurchaseContractCodec.idempotencyKey("000001", 11L, TARGET, contract());
        String second = DecisionPurchaseContractCodec.idempotencyKey("0000011", 1L, TARGET, contract());
        assertEquals("AI_PURCHASE_V2:efdb5d0442e0f4ad6d9b9a7b3be9218c96e109c3b3f495bae73ac02899a3a6f2", first,
            "必须按固定位置编码每个字段的 UTF-8 四字节长度前缀");
        assertEquals("AI_PURCHASE_V2:99cacee01161880c8d9cf68cf497fa1a3682d71610e2ea20249b92c0d999a73f", second,
            "不同字段边界不能产生相同幂等键");
        assertNotEquals(first, second, "不可仅拼接值或丢弃前像字段边界");
    }

    @Test
    void idempotencyKeyBindsEveryVariableBusinessIdentity() {
        var keys = List.of(DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, contract()),
            DecisionPurchaseContractCodec.idempotencyKey("000002", RUN, TARGET, contract()),
            DecisionPurchaseContractCodec.idempotencyKey("000001", RUN + 1, TARGET, contract()),
            DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
                new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 12L, null), contract()),
            DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, withIdentities(MATERIAL + 1, 31L)),
            DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, withIdentities(MATERIAL, 32L)));
        for (String key : keys) assertTrue(key.matches("AI_PURCHASE_V2:[0-9a-f]{64}") && key.length() <= 128,
            "每项业务身份变化仍必须返回有界完整 SHA-256 键");
        assertEquals(keys.size(), new HashSet<>(keys).size(), "租户、运行、业务根、物料及供应商分别变化都必须改变键");
    }

    @Test
    void idempotencyKeyKeepsFieldPositionsRatherThanSortingValues() {
        String first = DecisionPurchaseContractCodec.idempotencyKey("000001", 11L,
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 12L, null), withIdentities(13L, 14L));
        String second = DecisionPurchaseContractCodec.idempotencyKey("000001", 12L,
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), withIdentities(13L, 14L));
        String third = DecisionPurchaseContractCodec.idempotencyKey("000001", 11L,
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 12L, null), withIdentities(14L, 13L));
        assertTrue(first.matches("AI_PURCHASE_V2:[0-9a-f]{64}"), "字段位置检查必须使用新版有界键");
        assertNotEquals(first, second, "运行与根身份互换不能因值排序而碰撞");
        assertNotEquals(first, third, "物料与供应商身份互换不能因值排序而碰撞");
    }

    @Test
    void idempotencyKeyIsStableAcrossCallsCopiesAndWireOrder() {
        Map<String, Object> payload = wire();
        var fields = new ArrayList<>(payload.keySet()); Collections.reverse(fields);
        Map<String, Object> reversed = new LinkedHashMap<>(); fields.forEach(field -> reversed.put(field, payload.get(field)));
        row(reversed, 0).put("coveredDemandBase", "1.2500"); row(reversed, 0).put("requestedOrderQty", "1.2500");
        DecisionPurchaseContractV2 copied = DecisionPurchaseContractCodec.parse(reversed, RUN);
        for (int i = 0; i < 100; i++) {
            assertEquals(BOUNDED_KEY, DecisionPurchaseContractCodec.idempotencyKey("000001", RUN, TARGET, copied),
                "重复调用、wire 字段顺序和无关数量尾零不得改变采购动作身份");
        }
        assertEquals(BOUNDED_KEY, DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "另一显示名称"), contract()),
            "显示名称不属于可信业务根身份，不能参与幂等键");
    }

    @Test
    void idempotencyKeyRejectsInvalidContractIdsAndDelimiterInjection() {
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
                TARGET, withIdentities(id, 31L)), "物料身份必须为完整正 Long 标识");
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
                TARGET, withIdentities(MATERIAL, id)), "供应商身份必须为完整正 Long 标识");
        }
        for (String tenant : List.of("000001:11", "000001\u0000", "000001\n")) {
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey(tenant, RUN, TARGET, contract()),
                "长度编码不替代租户身份校验，不允许分隔符或控制字符注入");
        }
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.idempotencyKey("000001", RUN,
            new DecisionBusinessRef(null, 11L, null), contract()), "不能缺失业务根类型");
    }

    private static DecisionPurchaseContractV2 withIdentities(Long material, Long vendor) {
        DecisionPurchaseContractV2 source = contract();
        return new DecisionPurchaseContractV2(material, vendor, source.windowEnd(), source.materialVersion(),
            source.vendorVersion(), source.supplierTermsId(), source.supplierTermsVersion(), source.baseUomId(), source.baseUomVersion(),
            source.orderUomId(), source.orderUomVersion(), source.conversionVersion(), source.requirementDetailIds(),
            source.frozenLines(), source.expectedSourceFingerprint());
    }

    private static void assertDefaultFailure(String name, String message, Class<?>... types) throws Throwable {
        Method method = IndustrialDecisionDataPort.class.getMethod(name, types);
        assertTrue(method.isDefault(), "新增 Port 方法必须为默认接口：" + name);
        Object port =
                Proxy.newProxyInstance(
                        IndustrialDecisionDataPort.class.getClassLoader(),
                        new Class<?>[] {IndustrialDecisionDataPort.class},
                        (proxy, invoked, args) -> {
                            if (!invoked.isDefault()) throw new AssertionError("测试不得调用原有抽象接口");
                            return InvocationHandler.invokeDefault(proxy, invoked, args);
                        });
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) if (types[i] == int.class) args[i] = 7;
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> method.invoke(port, args), "缺少 ERP 实现时必须明确拒绝");
        assertInstanceOf(UnsupportedOperationException.class, failure.getCause(), "默认 Port 不得返回空列表或 null 冒充成功");
        assertEquals(message, failure.getCause().getMessage(), "默认失败必须使用批准的中文消息");
    }

    private static DecisionPurchaseContractV2 contract() {
        return new DecisionPurchaseContractV2(MATERIAL, 31L, WINDOW, "material-v1", "vendor-v1", 29L,
            "terms-v1", 17L, "uom-v1", 17L, "uom-v1", "uom-v1", List.of(FIRST_DETAIL, SECOND_DETAIL),
            List.of(new Line(FIRST_DETAIL, 17L, new BigDecimal("1.25"), 17L, new BigDecimal("1.25"), LocalDate.of(2026, 10, 7)),
                new Line(SECOND_DETAIL, 17L, new BigDecimal("2.5"), 17L, new BigDecimal("2.5"), LocalDate.of(2026, 10, 8))), "a".repeat(64));
    }

    private static DecisionPurchaseContractV2 withLists(List<Long> ids, List<Line> lines) {
        DecisionPurchaseContractV2 source = contract();
        return new DecisionPurchaseContractV2(source.materialId(), source.vendorId(), source.windowEnd(), source.materialVersion(),
            source.vendorVersion(), source.supplierTermsId(), source.supplierTermsVersion(), source.baseUomId(), source.baseUomVersion(),
            source.orderUomId(), source.orderUomVersion(), source.conversionVersion(), ids, lines, source.expectedSourceFingerprint());
    }

    private static Map<String, Object> wire() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchase_payload_version", 2); payload.put("runId", RUN.toString());
        payload.put("materialId", MATERIAL.toString()); payload.put("vendorId", "31"); payload.put("windowEnd", "2026-10-31");
        payload.put("materialVersion", "material-v1"); payload.put("vendorVersion", "vendor-v1"); payload.put("supplierTermsId", "29");
        payload.put("supplierTermsVersion", "terms-v1"); payload.put("baseUomId", "17"); payload.put("baseUomVersion", "uom-v1");
        payload.put("orderUomId", "17"); payload.put("orderUomVersion", "uom-v1"); payload.put("conversionVersion", "uom-v1");
        payload.put("requirement_detail_ids", new ArrayList<>(List.of(FIRST_DETAIL.toString(), SECOND_DETAIL.toString())));
        payload.put("frozenLines", new ArrayList<>(List.of(wireLine(FIRST_DETAIL, "1.25", 7), wireLine(SECOND_DETAIL, "2.5", 8))));
        payload.put("expectedSourceFingerprint", "a".repeat(64));
        return payload;
    }

    private static Map<String, Object> wireLine(Long detail, String quantity, int day) {
        return new LinkedHashMap<>(Map.of("detailId", detail.toString(), "baseUomId", "17", "coveredDemandBase", quantity,
            "orderUomId", "17", "requestedOrderQty", quantity, "requiredDate", LocalDate.of(2026, 10, day).toString()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> row(Map<String, Object> payload, int index) {
        return (Map<String, Object>) ((List<?>) payload.get("frozenLines")).get(index);
    }

    private static DecisionActionProposal action(Map<String, Object> payload) { return action(payload, 101L, TARGET, KEY); }

    private static DecisionActionProposal action(Map<String, Object> payload, Long id, DecisionBusinessRef target, String key) {
        var type = DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT;
        return new DecisionActionProposal(id, type, "采购测试", "合成逐来源合同", target, payload,
            DecisionRiskLevel.HIGH, true, type.permissionCode(), key, type.executorCode(), 1, List.of());
    }

    private static void rejected(Map<String, Object> payload, String message) {
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractCodec.parse(payload, RUN), message);
    }
}

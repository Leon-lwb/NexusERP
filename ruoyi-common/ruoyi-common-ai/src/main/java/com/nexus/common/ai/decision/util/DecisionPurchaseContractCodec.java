package com.nexus.common.ai.decision.util;

import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2.Line;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;

import java.io.Serial;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 服务端冻结采购意图的唯一 v2 wire 编解码器；不升级旧动作，不签发来源证明。
 * 仅提供纯内存结构校验及稳定摘要，权限、租户上下文、来源复核和永久提交仍由业务端口负责。
 */
public final class DecisionPurchaseContractCodec {
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
        "purchase_payload_version", "runId", "materialId", "vendorId", "windowEnd",
        "materialVersion", "vendorVersion", "supplierTermsId", "supplierTermsVersion",
        "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion", "conversionVersion",
        "requirement_detail_ids", "frozenLines", "expectedSourceFingerprint");
    private static final List<String> VERSION_FIELDS = List.of("materialVersion", "vendorVersion",
        "supplierTermsVersion", "baseUomVersion", "orderUomVersion", "conversionVersion");
    private static final List<String> CONTRACT_METADATA_FIELDS = List.of("materialVersion", "vendorVersion",
        "supplierTermsId", "supplierTermsVersion", "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion",
        "conversionVersion");
    private static final Set<String> LINE_FIELDS = Set.of("detailId", "baseUomId", "coveredDemandBase",
        "orderUomId", "requestedOrderQty", "requiredDate");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");

    private DecisionPurchaseContractCodec() { }

    /**
     * 仅表示完整来源认证后确认的变化；读取失败、未知事实或权限错误不得使用此内部信号。
     * 审批层按类型执行失效 CAS，并将固定安全消息转换为普通 API 业务异常。
     */
    public static final class SourceChangedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public SourceChangedException() {
            super("采购冻结来源已变化，须重新分析");
        }
    }

    /** 编码前严格校验完整意图；标识和数量为字符串，版本恰为整数二。 */
    public static Map<String, Object> toPayload(Long runId, DecisionPurchaseContractV2 contract) {
        require(contract != null, "冻结采购合同缺失");
        require(contract.requirementDetailIds() != null && !contract.requirementDetailIds().isEmpty()
            && contract.requirementDetailIds().size() <= 1000, "逐 MRP 冻结来源集合缺失或超限");
        require(contract.frozenLines() != null
            && contract.frozenLines().size() == contract.requirementDetailIds().size(), "冻结来源行必须与 MRP 来源集合逐项一致");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchase_payload_version", 2);
        payload.put("runId", idText(runId));
        payload.put("materialId", idText(contract.materialId()));
        payload.put("vendorId", idText(contract.vendorId()));
        payload.put("windowEnd", dateText(contract.windowEnd()));
        payload.put("materialVersion", contract.materialVersion());
        payload.put("vendorVersion", contract.vendorVersion());
        payload.put("supplierTermsId", idText(contract.supplierTermsId()));
        payload.put("supplierTermsVersion", contract.supplierTermsVersion());
        payload.put("baseUomId", idText(contract.baseUomId()));
        payload.put("baseUomVersion", contract.baseUomVersion());
        payload.put("orderUomId", idText(contract.orderUomId()));
        payload.put("orderUomVersion", contract.orderUomVersion());
        payload.put("conversionVersion", contract.conversionVersion());
        payload.put("requirement_detail_ids", contract.requirementDetailIds().stream()
            .map(DecisionPurchaseContractCodec::idText).toList());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Line line : contract.frozenLines()) {
            require(line != null, "冻结来源行缺失");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("detailId", idText(line.detailId()));
            row.put("baseUomId", idText(line.baseUomId()));
            row.put("coveredDemandBase", decimalText(line.coveredDemandBase()));
            row.put("orderUomId", idText(line.orderUomId()));
            row.put("requestedOrderQty", decimalText(line.requestedOrderQty()));
            row.put("requiredDate", dateText(line.requiredDate()));
            lines.add(Collections.unmodifiableMap(row));
        }
        payload.put("frozenLines", List.copyOf(lines));
        payload.put("expectedSourceFingerprint", contract.expectedSourceFingerprint());
        // 编码与解析共用同一完整字段及逐行校验，不能用排序或舍入修复错误意图。
        parse(payload, runId);
        return Collections.unmodifiableMap(payload);
    }

    /** 外部运行身份可为空；载荷中的运行身份始终必须为正十进制字符串。 */
    public static DecisionPurchaseContractV2 parse(Map<String, Object> payload, Long expectedRunId) {
        requireFields(payload, PAYLOAD_FIELDS, "采购 v2 载荷字段缺失或含旧版/额外字段");
        require(Integer.valueOf(2).equals(payload.get("purchase_payload_version")), "采购载荷必须为显式整数 v2，旧动作不可升级");
        Long runId = id(payload.get("runId"));
        if (expectedRunId != null) {
            require(positive(expectedRunId) && expectedRunId.equals(runId), "采购载荷运行身份与预期不一致");
        }
        Long materialId = id(payload.get("materialId"));
        Long vendorId = id(payload.get("vendorId"));
        Long supplierTermsId = id(payload.get("supplierTermsId"));
        Long baseUomId = id(payload.get("baseUomId"));
        Long orderUomId = id(payload.get("orderUomId"));
        for (String field : VERSION_FIELDS) version(payload.get(field));
        require(baseUomId.equals(orderUomId) && payload.get("baseUomVersion").equals(payload.get("orderUomVersion"))
            && payload.get("baseUomVersion").equals(payload.get("conversionVersion")),
            "当前仅支持同根基本单位、同版本、无超额的采购");
        LocalDate windowEnd = date(payload.get("windowEnd"));
        Object fingerprint = payload.get("expectedSourceFingerprint");
        require(fingerprint instanceof String hash && hash.matches("[0-9a-f]{64}"), "冻结来源指纹缺失或格式无效");
        require(payload.get("requirement_detail_ids") instanceof List<?> ids && !ids.isEmpty() && ids.size() <= 1000,
            "逐 MRP 冻结来源集合缺失或超限");
        List<Long> detailIds = new ArrayList<>();
        long previous = 0;
        for (Object rawId : (List<?>) payload.get("requirement_detail_ids")) {
            Long detailId = id(rawId);
            require(detailId > previous, "逐 MRP 来源必须唯一并按身份排序");
            detailIds.add(detailId);
            previous = detailId;
        }
        require(payload.get("frozenLines") instanceof List<?> lines && lines.size() == detailIds.size(),
            "冻结来源行必须与 MRP 来源集合逐项一致");
        List<Line> frozen = new ArrayList<>();
        int index = 0;
        for (Object rawLine : (List<?>) payload.get("frozenLines")) {
            require(rawLine instanceof Map<?, ?>, "冻结来源行必须为对象");
            Map<?, ?> line = (Map<?, ?>) rawLine;
            requireFields(line, LINE_FIELDS, "冻结来源行字段不完整或含客户端数量/其他物料");
            Long detailId = id(line.get("detailId"));
            require(detailIds.get(index++).equals(detailId) && baseUomId.equals(id(line.get("baseUomId")))
                && orderUomId.equals(id(line.get("orderUomId"))), "冻结来源行身份或基本/订购单位不一致");
            BigDecimal covered = quantity(line.get("coveredDemandBase"));
            BigDecimal ordered = quantity(line.get("requestedOrderQty"));
            LocalDate required = date(line.get("requiredDate"));
            require(covered.compareTo(ordered) == 0 && !required.isAfter(windowEnd), "冻结覆盖量/订购量有超额或来源日期超出窗口");
            frozen.add(new Line(detailId, baseUomId, covered, orderUomId, ordered, required));
        }
        return new DecisionPurchaseContractV2(materialId, vendorId, windowEnd,
            (String) payload.get("materialVersion"), (String) payload.get("vendorVersion"), supplierTermsId,
            (String) payload.get("supplierTermsVersion"), baseUomId, (String) payload.get("baseUomVersion"),
            orderUomId, (String) payload.get("orderUomVersion"), (String) payload.get("conversionVersion"),
            detailIds, frozen, (String) fingerprint);
    }

    /** 返回 writer 永久绑定的九项元数据，不引入额外证明字段。 */
    public static Map<String, String> contractMetadata(DecisionPurchaseContractV2 contract) {
        // 仅复用纯内存结构校验；该常量不创建运行，也不产生持久化写入。
        Map<String, Object> payload = toPayload(1L, contract);
        Map<String, String> metadata = new LinkedHashMap<>();
        CONTRACT_METADATA_FIELDS.forEach(field -> metadata.put(field, (String) payload.get(field)));
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * 仅供新 v2 工单采购使用的有界永久键，不回填旧运行或改写旧键。
     * 前像顺序固定为租户、运行、动作类型、目标类型、目标标识、物料、供应商。
     * 完整标识以 UTF-8 四字节长度前缀参与摘要，不排序字段值、不截断标识。
     */
    public static String idempotencyKey(String tenantId, Long runId, DecisionBusinessRef target,
                                        DecisionPurchaseContractV2 contract) {
        require(validTenant(tenantId) && positive(runId), "采购永久键的租户或运行身份无效");
        require(target != null && target.type() == DecisionBusinessType.WORK_ORDER && positive(target.id()),
            "首包采购永久键仅支持明确的工单目标");
        require(contract != null && positive(contract.materialId()) && positive(contract.vendorId()),
            "采购永久键的物料或供应商身份无效");
        List<String> values = List.of(tenantId, runId.toString(),
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.name(), target.type().name(), target.id().toString(),
            contract.materialId().toString(), contract.vendorId().toString());
        return "AI_PURCHASE_V2:" + sha256(values);
    }

    /**
     * 原样复用既有 ERP v2 稳定摘要：身份前缀、顶层字段名排序、逐来源顺序和规范十进制。
     * 每段使用 UTF-8 字节数的四字节大端长度前缀，再进行 SHA-256；不得替换为普通 JSON 哈希。
     * 此方法不授予业务权限；调用者仍须验证当前身份、目标及永久键。
     */
    public static String payloadFingerprint(DecisionActionProposal action, DecisionActor actor) {
        require(actor != null && validTenant(actor.tenantId()) && positive(actor.userId()), "采购摘要的租户或用户身份无效");
        require(action != null && positive(action.actionId()) && action.target() != null && action.target().type() != null
            && (action.target().type() == DecisionBusinessType.SHORTAGE_HORIZON ? action.target().id() == null
                : positive(action.target().id())), "采购摘要的动作或目标身份无效");
        require(action.idempotencyKey() != null && action.idempotencyKey().matches("[A-Za-z0-9:_-]{16,161}"),
            "采购永久请求键无效");
        Map<String, Object> payload = action.payload();
        DecisionPurchaseContractV2 contract = parse(payload, null);
        List<String> values = new ArrayList<>(List.of("certified-purchase:v2", actor.tenantId(), actor.userId().toString(),
            action.actionId().toString(), action.idempotencyKey(), action.target().type().name(),
            Objects.toString(action.target().id(), "")));
        PAYLOAD_FIELDS.stream().filter(field -> !field.equals("frozenLines") && !field.equals("requirement_detail_ids"))
            .sorted().forEach(field -> { values.add(field); values.add(payload.get(field).toString()); });
        for (Line line : contract.frozenLines()) {
            values.add(line.detailId().toString()); values.add(line.baseUomId().toString());
            values.add(decimalText(line.coveredDemandBase())); values.add(line.orderUomId().toString());
            values.add(decimalText(line.requestedOrderQty())); values.add(line.requiredDate().toString());
        }
        return sha256(values);
    }

    private static String sha256(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new ServiceException("可信采购结果合同摘要不可用");
        }
    }

    private static Long id(Object value) {
        require(value instanceof String text && text.matches("[1-9][0-9]{0,18}"), "标识必须为正十进制 ID 字符串，不接受单位名称");
        try {
            return Long.valueOf((String) value);
        } catch (NumberFormatException invalid) {
            throw new ServiceException("可信采购标识超出有效范围");
        }
    }

    private static String idText(Long value) {
        require(positive(value), "标识必须为正十进制 ID");
        return value.toString();
    }

    private static void version(Object value) {
        require(value instanceof String text && !text.isBlank() && text.equals(text.trim()) && text.length() <= 256
            && text.chars().noneMatch(Character::isISOControl), "物料、供应商、条款或 UOM 冻结版本缺失");
    }

    private static LocalDate date(Object value) {
        require(value instanceof String text && text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "来源日期必须为明确业务日期");
        try {
            LocalDate date = LocalDate.parse((String) value);
            require(date.getYear() >= 1000 && date.getYear() <= 9999, "来源业务日期超出范围");
            return date;
        } catch (DateTimeException invalid) {
            throw new ServiceException("可信采购来源业务日期无效");
        }
    }

    private static String dateText(LocalDate value) {
        require(value != null && value.getYear() >= 1000 && value.getYear() <= 9999, "来源业务日期缺失或超出范围");
        return value.toString();
    }

    private static BigDecimal quantity(Object value) {
        require(value instanceof String text && text.matches("(0|[1-9][0-9]{0,13})(\\.[0-9]{1,4})?"),
            "冻结数量必须为 DECIMAL(18,4) 的精确十进制字符串");
        BigDecimal quantity = new BigDecimal((String) value);
        require(validQuantity(quantity), "冻结数量必须为正且不溢出");
        return quantity;
    }

    private static boolean validQuantity(BigDecimal value) {
        return value != null && value.signum() > 0 && value.compareTo(MAX_QUANTITY) <= 0
            && value.stripTrailingZeros().scale() <= 4;
    }

    private static String decimalText(BigDecimal value) {
        require(validQuantity(value), "冻结数量必须为正且符合 DECIMAL(18,4)，不得舍入或溢出");
        return value.stripTrailingZeros().toPlainString();
    }

    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean validTenant(String tenantId) { return tenantId != null && tenantId.matches("[0-9]{6,20}"); }

    private static void requireFields(Map<?, ?> values, Set<String> expected, String message) {
        require(values != null && values.keySet().equals(expected), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ServiceException("可信采购决策拒绝：" + message);
    }
}

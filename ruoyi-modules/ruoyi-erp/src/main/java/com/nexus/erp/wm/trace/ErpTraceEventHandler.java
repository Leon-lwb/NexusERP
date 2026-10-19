package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 先读取并核验制造快照，再在库存事务内通过来源守卫入口整体过账。
 */
@Component
@RequiredArgsConstructor
public class ErpTraceEventHandler implements TraceEventHandler {
    private final ObjectMapper json;
    private final CompletionManifestPort manifests;
    private final CompletionAllocationPort allocations;

    /**
     * 目标事务开始前准备完成的外部来源事实。
     *
     * @param products 工单实际登记的产品身份或产出集合。
     * @param materials 实际产品与组件物料集合。
     */
    private record Prepared(
            CompletionManifestIntegrity.Frozen products,
            List<CompletionMaterialAllocation> materials) {}

    private final ProductionManifestPostingPort posting;
    private final ErpSerialIdentityService identities;

    @Override
    public String targetModule() {
        return "ERP";
    }

    @Override
    public boolean supports(String type) {
        return Set.of("SERIAL_RESERVATION_CONFIRM", "COMPLETION_MANIFEST_FROZEN").contains(type);
    }

    @Override
    public Object prepare(MaterialTraceEvent event) {
        require("MES".equals(event.sourceModule()), "ERP只接受MES来源事件");
        JsonNode payload = parse(event.payload());
        if ("COMPLETION_MANIFEST_FROZEN".equals(event.eventType())) {
            Long id = positive(payload, "manifestId");
            int revision = Math.toIntExact(positive(payload, "revision"));
            require(event.aggregateId().equals(id), "清单聚合身份不一致");
            var frozen =
                    CompletionManifestIntegrity.read(
                            manifests,
                            event.tenantId(),
                            id,
                            revision,
                            required(payload, "manifestHash"));
            var header = frozen.manifest();
            require(
                    Objects.equals(header.mesWoId(), positive(payload, "mesWoId"))
                            && Objects.equals(header.erpWoId(), positive(payload, "erpWoId"))
                            && Objects.equals(header.materialId(), positive(payload, "materialId"))
                            && header.lineCount() == positive(payload, "lineCount"),
                    "清单摘要与业务来源不一致");
            var materials =
                    CompletionAllocationIntegrity.read(
                            allocations,
                            event.tenantId(),
                            id,
                            revision,
                            required(payload, "materialHash"));
            require(materials.size() == nonnegative(payload, "materialLineCount"), "组件行数与冻结事件不一致");
            return new Prepared(frozen, materials);
        }
        require(event.aggregateId().equals(positive(payload, "reservationId")), "预留聚合身份不一致");
        return payload;
    }

    @Override
    public String apply(MaterialTraceEvent event, Object prepared) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceEventId", event.eventId());
        if ("SERIAL_RESERVATION_CONFIRM".equals(event.eventType())) {
            JsonNode payload = (JsonNode) prepared;
            Long id = positive(payload, "reservationId");
            String command = required(payload, "commandId"),
                    fingerprint = required(payload, "fingerprint");
            identities.confirmWithinInbox(event.tenantId(), id, command, fingerprint);
            result.put("reservationId", id);
            result.put("commandId", command);
            result.put("fingerprint", fingerprint);
        } else {
            var snapshot = (Prepared) prepared;
            var frozen = snapshot.products();
            CompletionReceipt receipt =
                    posting.post(frozen.manifest(), frozen.lines(), snapshot.materials());
            require(
                    Objects.equals(receipt.manifestId(), frozen.manifest().manifestId())
                            && receipt.revision() == frozen.manifest().revision()
                            && receipt.manifestHash().equals(frozen.manifest().contentHash()),
                    "入库证明不匹配冻结清单");
            require(
                    frozen.manifest().goodQty().signum() == 0
                            ? "NO_GOOD_OUTPUT".equals(receipt.outcome())
                            : "POSTED".equals(receipt.outcome()),
                    "完工数量与回执业务效果不一致");
            result.put("outcome", receipt.outcome());
            result.put("receiptId", receipt.receiptId());
            result.put("postingGroupId", receipt.postingGroupId());
            result.put("manifestId", receipt.manifestId());
            result.put("revision", receipt.revision());
            result.put("manifestHash", receipt.manifestHash());
        }
        try {
            return json.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("入库确认无法冻结", e);
        }
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("事件载荷无效", e);
        }
    }

    private static String required(JsonNode node, String key) {
        String value = node.path(key).asText("");
        require(!value.isBlank(), "事件字段缺失：" + key);
        return value;
    }

    private static long nonnegative(JsonNode node, String key) {
        try {
            long value = new java.math.BigDecimal(required(node, key)).longValueExact();
            require(value >= 0, "事件数量无效：" + key);
            return value;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("事件整数无效：" + key, e);
        }
    }

    private static Long positive(JsonNode node, String key) {
        try {
            long value = new java.math.BigDecimal(required(node, key)).longValueExact();
            require(value > 0, "事件ID或数量无效：" + key);
            return value;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("事件整数无效：" + key, e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.EvidenceItem;
import com.nexus.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 从独立重读的 ERP 快照重建数值事实，不使用流程或模型标记。
 */
@org.springframework.stereotype.Component
public class DecisionSourceFactVerifier {
    public List<EvidenceItem> numericFacts(Long runId, DeliveryContextSnapshot snapshot) {
        if (snapshot == null || snapshot.rootBusinessRef() == null || snapshot.snapshotHash() == null
            || snapshot.snapshotHash().isBlank() || snapshot.snapshotAt() == null)
            throw new ServiceException("业务事实快照不可核验");
        List<EvidenceItem> facts = new ArrayList<>();
        if (snapshot.stocks() != null) for (var stock : snapshot.stocks()) {
            if (stock == null || stock.materialId() == null) throw new ServiceException("库存事实不完整");
            fact(facts, runId, snapshot, "STOCK", stock.materialId(), stock.materialId().toString(),
                "stock_qty", stock.stockQty());
            fact(facts, runId, snapshot, "STOCK", stock.materialId(), stock.materialId().toString(),
                "reserved_qty", stock.reservedQty());
            fact(facts, runId, snapshot, "STOCK", stock.materialId(), stock.materialId().toString(),
                "safety_stock", stock.safetyStock());
        }
        if (snapshot.workOrders() != null) for (var work : snapshot.workOrders()) {
            if (work == null || work.id() == null) throw new ServiceException("工单事实不完整");
            fact(facts, runId, snapshot, "WORK_ORDER", work.id(), work.code(),
                "remaining_qty", work.remainingQty());
        }
        Set<String> requirementSourceKeys = new HashSet<>();
        boolean hasIdentifiedRequirement = false;
        boolean hasLegacyRequirement = false;
        if (snapshot.requirements() != null) for (var req : snapshot.requirements()) {
            if (req == null || req.materialId() == null) throw new ServiceException("需求事实不完整");
            String code = req.workOrderId() + ":" + req.materialId() + ":" + req.requiredDate();
            if (req.detailId() != null && req.detailId() <= 0)
                throw new ServiceException("物料需求明细来源 ID 无效");
            if (req.detailId() == null) hasLegacyRequirement = true;
            else hasIdentifiedRequirement = true;
            /**
             * 缺料仿真器会统计每一行；即便完全相同的重复行也会
             * 使需求翻倍；这里拒绝重复行比按数值事实键去重更安全。
             */
            String sourceKey = req.detailId() == null ? code : req.detailId().toString();
            if (!requirementSourceKeys.add(sourceKey))
                throw new ServiceException("物料需求明细重复，不能生成唯一数值证据");
            /**
             * 同一工单、物料和日期可能有不同的 BOM/MRP 明细行及数量。
             * 保留行 ID 作为证据键；编码仍是便于阅读的范围元数据。
             * 旧快照缺少行 ID 时，相互冲突的值仍会产生键冲突并拒绝放行。
             */
            fact(facts, runId, snapshot, "REQUIREMENT", req.detailId(), code, "required_qty", req.requiredQty());
        }
        if (hasLegacyRequirement && hasIdentifiedRequirement)
            throw new ServiceException("物料需求明细来源新旧混杂，不能生成可信证据");
        if (snapshot.arrivals() != null) for (var arrival : snapshot.arrivals()) {
            if (arrival == null || arrival.purchaseLineId() == null) throw new ServiceException("在途事实不完整");
            fact(facts, runId, snapshot, "ARRIVAL", arrival.purchaseLineId(),
                arrival.purchaseLineId().toString(), "remaining_qty", arrival.remainingQty());
        }
        if (snapshot.supplierMetrics() != null) for (var supplier : snapshot.supplierMetrics()) {
            if (supplier == null || supplier.vendorId() == null) throw new ServiceException("供应商事实不完整");
            if (supplier.observedAt() == null) continue; /** 来源未知不等于零风险事实。 */
            fact(facts, runId, snapshot, "SUPPLIER", supplier.vendorId(),
                supplier.vendorId().toString(), "on_time_rate", supplier.onTimeRate(), supplier.observedAt());
            fact(facts, runId, snapshot, "SUPPLIER", supplier.vendorId(),
                supplier.vendorId().toString(), "sample_count", BigDecimal.valueOf(supplier.sampleCount()),
                supplier.observedAt());
        }
        if (snapshot.capacityByDate() != null) snapshot.capacityByDate().forEach((date, resources) -> {
            if (date == null || resources == null) throw new ServiceException("产能事实不完整");
            resources.forEach((resource, hours) -> fact(facts, runId, snapshot, "CAPACITY", null,
                date + ":" + resource, "available_hours", hours));
        });
        /**
         * 这些叶子证据将已保存基线绑定至来源日历行和限定任务资源关系。
         * 相同指纹的 ERP 重读必须能复现数值及其来源范围。
         */
        if (snapshot.currentSchedule() != null && snapshot.currentSchedule().containsKey("capacityCalendars")) {
            Object calendars = snapshot.currentSchedule().get("capacityCalendars");
            Object tasks = snapshot.currentSchedule().get("tasks");
            if (!(calendars instanceof List<?> calendarRows) || !(tasks instanceof List<?> taskRows))
                throw new ServiceException("已存排程产能事实缺少关联数据");
            for (Object raw : calendarRows) {
                if (!(raw instanceof Map<?, ?> row)) throw new ServiceException("产能日历来源非法");
                Long id = sourceId(row.get("calendarId"));
                String sourceCode = row.get("calendarDate") + ":" + row.get("resourceType") + ":" + row.get("resourceId");
                fact(facts, runId, snapshot, "CALENDAR", id, sourceCode,
                    "planned_hours", sourceDecimal(row.get("plannedHours")));
                fact(facts, runId, snapshot, "CALENDAR", id, sourceCode,
                    "effective_hours", sourceDecimal(row.get("effectiveHours")));
            }
            for (Object raw : taskRows) {
                if (!(raw instanceof Map<?, ?> row)) throw new ServiceException("排程任务来源非法");
                Long id = sourceId(row.get("taskId"));
                String sourceCode = row.get("resourceType") + ":" + row.get("resourceId") + ":"
                    + row.get("startTime") + ":" + row.get("endTime");
                fact(facts, runId, snapshot, "SCHEDULE_TASK", id, sourceCode,
                    "process_hours", sourceDecimal(row.get("processHours")));
            }
        }        /** 重复事实键会使最后一项掩盖相互矛盾的来源值，必须拒绝。 */
        Map<String, EvidenceItem> byKey = new LinkedHashMap<>();
        for (EvidenceItem item : facts) {
            EvidenceItem previous = byKey.putIfAbsent(item.evidenceKey(), item);
            if (previous != null && !Objects.equals(previous.value(), item.value()))
                throw new ServiceException("同一业务事实键出现矛盾数值");
        }
        return List.copyOf(byKey.values());
    }

    public void assertProvenance(DecisionResult result, DeliveryContextSnapshot snapshot) {
        if (result == null || result.evidence() == null) throw new ServiceException("决策结果缺少证据");
        Map<String, EvidenceItem> trusted = new HashMap<>();
        numericFacts(result.runId(), snapshot).forEach(item -> trusted.put(item.evidenceKey(), item));
        Map<String, EvidenceItem> claimed = new DecisionEvidenceService().indexByKey(result.evidence());
        for (EvidenceItem item : claimed.values()) {
            if (!"NUMBER".equals(item.valueType())) continue;
            if (!item.verifiable() || item.normative() || !Objects.equals(item.dataVersion(), snapshot.snapshotHash())
                || "DERIVED".equals(item.sourceDomain()) && !Objects.equals(item.observedAt(), snapshot.snapshotAt()))
                throw new ServiceException("数值证据未绑定业务快照及数据版本");
            assertRooted(item, claimed, trusted, new HashSet<>());
        }
    }

    /**
     * 相同关键哈希的重读可具有较晚采集时间；比较实际事实，不比较采集时钟。
     */
    public void assertProvenance(DecisionResult result, DeliveryContextSnapshot stored,
                                 DeliveryContextSnapshot reread) {
        assertProvenance(result, stored);
        if (reread == null || !Objects.equals(stored.snapshotHash(), reread.snapshotHash())
            || !Objects.equals(stored.rootBusinessRef(), reread.rootBusinessRef()))
            throw new ServiceException("ERP 重读的业务范围或指纹已变化");
        Map<String, EvidenceItem> now = new HashMap<>();
        numericFacts(result.runId(), reread).forEach(item -> now.put(item.evidenceKey(), item));
        List<EvidenceItem> original = numericFacts(result.runId(), stored);
        if (original.size() != now.size() || original.stream().anyMatch(item -> {
            EvidenceItem current = now.get(item.evidenceKey());
            return current == null || !numericEquals(item.value(), current.value())
                || !Objects.equals(item.sourceCode(), current.sourceCode())
                || "SUPPLIER".equals(item.sourceType()) && !Objects.equals(item.observedAt(), current.observedAt());
        })) throw new ServiceException("重读 ERP 数值事实与已持久化快照不一致");
    }

    private void assertRooted(EvidenceItem item, Map<String, EvidenceItem> claimed,
                              Map<String, EvidenceItem> trusted, Set<String> visiting) {
        if (!visiting.add(item.evidenceKey())) throw new ServiceException("派生证据存在循环引用");
        try {
            if ("ERP".equals(item.sourceDomain())) {
                EvidenceItem source = trusted.get(item.evidenceKey());
                if (source == null || !Objects.equals(item.sourceType(), source.sourceType())
                    || !Objects.equals(item.sourceId(), source.sourceId())
                    || !Objects.equals(item.sourceCode(), source.sourceCode())
                    || !Objects.equals(item.observedAt(), source.observedAt())
                    || !Objects.equals(item.runId(), source.runId())
                    || !Objects.equals(item.valueType(), source.valueType())
                    || !numericEquals(item.value(), source.value())
                    || item.derivedFromEvidenceKeys() != null && !item.derivedFromEvidenceKeys().isEmpty())
                    throw new ServiceException("业务数值证据与租户范围内源事实不符");
                return;
            }
            if (!"DERIVED".equals(item.sourceDomain()) || item.derivedFromEvidenceKeys() == null
                || item.derivedFromEvidenceKeys().isEmpty())
                throw new ServiceException("未验证的模型或模拟数值不能作为业务源事实");
            for (String key : item.derivedFromEvidenceKeys()) {
                EvidenceItem parent = claimed.get(key);
                if (parent == null || !"NUMBER".equals(parent.valueType()) || !parent.verifiable()
                    || !Objects.equals(parent.dataVersion(), item.dataVersion())
                    || parent.observedAt() == null)
                    throw new ServiceException("派生数值缺少同版本的业务父事实");
                assertRooted(parent, claimed, trusted, visiting);
            }
        } finally { visiting.remove(item.evidenceKey()); }
    }

    private Long sourceId(Object value) {
        if (!(value instanceof String id) || !id.matches("[1-9][0-9]{0,18}"))
            throw new ServiceException("源事实ID无效");
        try { return Long.valueOf(id); }
        catch (NumberFormatException invalid) { throw new ServiceException("源事实ID超出范围"); }
    }

    private BigDecimal sourceDecimal(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof String text) || text.length() > 32) throw new ServiceException("源事实数值格式无效");
        try { return new BigDecimal(text); }
        catch (NumberFormatException invalid) { throw new ServiceException("源事实数值格式无效"); }
    }

    private boolean numericEquals(String left, String right) {
        try { return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0; }
        catch (RuntimeException invalid) { return false; }
    }

    private void fact(List<EvidenceItem> facts, Long runId, DeliveryContextSnapshot snapshot,
                      String type, Long sourceId, String sourceCode, String metric, BigDecimal value) {
        fact(facts, runId, snapshot, type, sourceId, sourceCode, metric, value, snapshot.snapshotAt());
    }

    private void fact(List<EvidenceItem> facts, Long runId, DeliveryContextSnapshot snapshot,
                      String type, Long sourceId, String sourceCode, String metric, BigDecimal value,
                      LocalDateTime observedAt) {
        if (value == null) return;
        String key = "ERP:" + type + ":" + (sourceId == null ? sourceCode : sourceId) + ":" + metric;
        facts.add(new EvidenceItem(runId, key, "ERP", type, sourceId, sourceCode, null,
            metric, null, "NUMBER", value.stripTrailingZeros().toPlainString(), null,
            observedAt, snapshot.snapshotHash(), null, true, false, List.of(), null));
    }
}

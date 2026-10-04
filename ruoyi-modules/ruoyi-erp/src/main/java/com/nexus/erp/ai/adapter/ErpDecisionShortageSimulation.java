package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionShortageBasis;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.StockFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderSalesLinkFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderFact;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.ai.decision.exception.DecisionEvidenceException;
import com.nexus.erp.prd.schedule.PrdShortageInput;
import com.nexus.erp.prd.schedule.PrdShortageResult;
import com.nexus.erp.prd.schedule.PrdShortageScenarioService;
import com.nexus.erp.prd.schedule.PurchaseArrivalLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** 从已限定范围的不可变快照事实做缺料场景推演，不查询数据库或缓存。 */
@Component
@RequiredArgsConstructor
public class ErpDecisionShortageSimulation {
    private static final String OVERRIDE_KEY = "purchaseArrivalOverrides";
    private static final String NO_REQUIREMENT_DATA = "NO_REQUIREMENT_DATA";

    private final PrdShortageScenarioService scenarioService;

    public ShortageSimulationResult simulate(DeliveryContextSnapshot snapshot, int horizonDays,
                                             Map<String, Object> overrides) {
        validateSnapshot(snapshot, horizonDays);
        LocalDate today = snapshot.snapshotAt().toLocalDate();
        LocalDate end = today.plusDays(horizonDays - 1L);
        TreeSet<String> warnings = new TreeSet<>();
        if (snapshot.warnings() != null) {
            for (String warning : snapshot.warnings()) {
                if (warning != null && !warning.isBlank()) warnings.add(warning);
            }
        }

        // 开始任何物料仿真前，必须验证全部事实、标识及覆盖值。
        TreeMap<Long, MaterialDemand> demands = collectDemands(snapshot.requirements(), today, end, warnings);
        boolean incompleteEvidence = missingRequirementScope(snapshot.workOrders(), snapshot.requirements(),
            today, end, warnings);
        Map<Long, StockTotal> stocks = collectStocks(snapshot.stocks(), demands.keySet());
        Map<Long, ArrivalFact> arrivalsById = new HashMap<>();
        Map<Long, List<PurchaseArrivalLine>> linesByMaterial =
            collectArrivals(snapshot.arrivals(), arrivalsById, today, warnings);
        Map<Long, LocalDate> arrivalOverrides = parseOverrides(overrides, arrivalsById, today);
        for (Long purchaseLineId : arrivalOverrides.keySet()) {
            Long materialId = arrivalsById.get(purchaseLineId).materialId();
            require(demands.containsKey(materialId),
                "采购行 " + purchaseLineId + " 的物料 " + materialId + " 在本窗口无需求");
            ArrivalFact fact = arrivalsById.get(purchaseLineId);
            if (fact.expectedDate().isBefore(today)) {
                linesByMaterial.computeIfAbsent(materialId, id -> new ArrayList<>())
                    .add(new PurchaseArrivalLine(purchaseLineId, fact.expectedDate(), fact.remainingQty()));
            }
        }
        linesByMaterial.values().forEach(lines ->
            lines.sort(Comparator.comparing(PurchaseArrivalLine::purchaseLineId)));
        for (Long materialId : linesByMaterial.keySet()) {
            if (!demands.containsKey(materialId)) {
                warnings.add("IGNORED_ARRIVAL_WITHOUT_DEMAND:" + materialId);
            }
        }
        // 已核需求不能借用未知或异单位的在途数量；旧纯算法来源没有单位证明，不签发新依据。
        Set<Long> unknownSupply = unverifiedArrivalMaterials(snapshot, arrivalOverrides, today, end);
        if (!unknownSupply.isEmpty()) {
            incompleteEvidence = true;
            unknownSupply.forEach(id -> warnings.add("UNKNOWN_ARRIVAL_UNIT:" + id));
        }
        Map<Long, Long> salesOrdersByWorkOrder = parseSalesOrderLinks(snapshot.workOrderSalesLinks());

        if (demands.isEmpty()) {
            if (incompleteEvidence) throw new DecisionEvidenceException(DecisionEvidenceException.Reason.SHORTAGE_SIMULATION_INCOMPLETE);
            warnings.add(NO_REQUIREMENT_DATA);
            warnings.add("缺少需求证据，不能判定无风险");
            return new ShortageSimulationResult(false, 0, null, null,
                List.of(), null, List.of(), List.copyOf(warnings));
        }

        List<ShortageSimulationResult.Item> items = new ArrayList<>();
        Map<Long, LocalDate> peakDates = new TreeMap<>();
        int failedMaterials = 0;
        for (Map.Entry<Long, MaterialDemand> entry : demands.entrySet()) {
            Long materialId = entry.getKey();
            MaterialDemand demand = entry.getValue();
            if (unknownSupply.contains(materialId)) continue; // 不把未核供给模拟出的零缺口当成事实。
            PrdShortageInput input = demand.input();
            StockTotal stock = stocks.get(materialId);
            if (stock == null) {
                warnings.add("MISSING_STOCK_FACT:" + materialId);
                incompleteEvidence = true;
                continue;
            }
            if (stock.safety == null) {
                warnings.add("MISSING_MATERIAL_MASTER:" + materialId);
                incompleteEvidence = true;
                continue;
            }
            input.setStockQty(stock.available);
            input.setSafetyStock(stock.safety);
            List<PurchaseArrivalLine> lines = linesByMaterial.getOrDefault(materialId, List.of());
            Map<Long, LocalDate> perMaterialOverrides = new TreeMap<>();
            for (PurchaseArrivalLine line : lines) {
                LocalDate effectiveDate = arrivalOverrides.getOrDefault(line.purchaseLineId(), line.originalDate());
                if (arrivalOverrides.containsKey(line.purchaseLineId())) {
                    perMaterialOverrides.put(line.purchaseLineId(), effectiveDate);
                }
                if (effectiveDate.isAfter(end)) {
                    warnings.add("物料 " + materialId + " 采购行 " + line.purchaseLineId()
                        + " 到货在推演窗口外，仅作催货假设，非已到货");
                }
            }
            try {
                PrdShortageResult result = scenarioService.simulate(input, lines, perMaterialOverrides, today);
                if (result == null) throw new IllegalStateException("推演结果为空");
                List<String> evidence = new ArrayList<>();
                evidence.add("snapshot:requirementAggregate:" + snapshot.snapshotHash() + ":" + materialId);
                evidence.add("snapshot:stockAggregate:" + snapshot.snapshotHash() + ":" + materialId);
                for (PurchaseArrivalLine line : lines) {
                    evidence.add("erp:purchaseLine:" + line.purchaseLineId());
                }
                peakDates.put(materialId, result.getMaxGapDate());
                LocalDate shortageDate = result.getEarliestShortageDate();
                if (shortageDate != null && shortageDate.isAfter(end)) shortageDate = null;
                TreeSet<Long> affectedWorkOrders = new TreeSet<>();
                for (LocalDate date : result.getShortageDemandDates()) {
                    TreeSet<Long> ids = demand.workOrderIdsByDate().get(date);
                    if (ids == null) continue;
                    affectedWorkOrders.addAll(ids);
                    if (ids.size() > 1) {
                        warnings.add("AMBIGUOUS_SAME_DAY_WORK_ORDERS:" + materialId + ":" + date);
                    }
                }
                List<Long> workOrderIds = List.copyOf(affectedWorkOrders);
                TreeSet<Long> salesOrderIds = new TreeSet<>();
                if (!result.getShortageDemandDates().isEmpty() && workOrderIds.isEmpty()
                    && snapshot.rootBusinessRef().type() == DecisionBusinessType.SALES_ORDER) {
                    salesOrderIds.add(snapshot.rootBusinessRef().id());
                }
                for (Long workOrderId : workOrderIds) {
                    Long salesOrderId = salesOrdersByWorkOrder.get(workOrderId);
                    if (salesOrderId != null) {
                        salesOrderIds.add(salesOrderId);
                    } else if (snapshot.rootBusinessRef().type() == DecisionBusinessType.SALES_ORDER) {
                        salesOrderIds.add(snapshot.rootBusinessRef().id());
                    }
                }

                items.add(new ShortageSimulationResult.Item(materialId, input.getMaterialCode(),
                    input.getMaterialName(), shortageDate,
                    shortageDate == null ? BigDecimal.ZERO : result.getMaxGap(), result.getOnOrderQty(),
                    workOrderIds, List.copyOf(salesOrderIds),
                    shortageDate == null && result.getSuggestion() == null ? "NO_RISK" : result.getSuggestion(),
                    List.copyOf(evidence)));
            } catch (IllegalArgumentException invalid) {
                // 不得将无效方案事实转换成看似安全的结果。
                throw invalid;
            } catch (MaterialSimulationException failure) {
                failedMaterials++;
                warnings.add("物料 " + materialId + " 缺料推演失败，风险未知");
            }
        }

        if (failedMaterials != 0 || incompleteEvidence) {
            warnings.add("SHORTAGE_SIMULATION_INCOMPLETE: 仅列已确认风险，完整风险未知");
            if (items.stream().noneMatch(item -> item.firstShortageDate() != null)) {
                throw new ServiceException("SHORTAGE_SIMULATION_INCOMPLETE: 推演未完成，无法判定无风险");
            }
        }
        if (items.isEmpty()) {
            return new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO,
                List.of(), null, List.of(), List.copyOf(warnings));
        }
        items.sort(Comparator.comparing(ShortageSimulationResult.Item::firstShortageDate,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ShortageSimulationResult.Item::materialId));
        int riskCount = 0;
        BigDecimal totalGap = null;
        LocalDate earliest = null;
        Set<Long> impactedOrders = new HashSet<>();
        LinkedHashSet<String> evidenceRefs = new LinkedHashSet<>();
        for (ShortageSimulationResult.Item item : items) {
            evidenceRefs.addAll(item.evidenceKeys());
            if (item.firstShortageDate() != null) {
                riskCount++;
                if (earliest == null) earliest = item.firstShortageDate();
                impactedOrders.addAll(item.affectedSalesOrderIds());
            }
        }
        DecisionKpiSet kpis = null;
        if (failedMaterials == 0 && !incompleteEvidence) {
            warnings.add("仅计算缺料/影响订单指标，其余 APS KPI 未计算（0/null 为占位）");
            var basis = shortageBasis(snapshot, today, end, items, peakDates, evidenceRefs, arrivalOverrides);
            totalGap = comparableGapTotal(items, basis);
            if (basis == null) warnings.add("SHORTAGE_GAP_RATIO_UNRANKABLE");
            kpis = new DecisionKpiSet(impactedOrders.size(), 0, 0, 0, null,
                riskCount, totalGap, earliest == null ? null : earliest.toString(), null, 0, 0, null, null, basis);
        }
        return new ShortageSimulationResult(riskCount > 0, riskCount, earliest,
            failedMaterials == 0 && !incompleteEvidence ? totalGap : null, List.copyOf(items), kpis,
            List.copyOf(evidenceRefs), List.copyOf(warnings));
    }

    private static void validateSnapshot(DeliveryContextSnapshot snapshot, int horizonDays) {
        require(snapshot != null, "缺少交付快照");
        require(snapshot.rootBusinessRef() != null && snapshot.rootBusinessRef().type() != null,
            "缺少有效根业务引用");
        require(snapshot.rootBusinessRef().type() == DecisionBusinessType.SHORTAGE_HORIZON
                ? snapshot.rootBusinessRef().id() == null : positive(snapshot.rootBusinessRef().id()),
            "根业务引用 ID 无效");
        require(snapshot.snapshotAt() != null, "快照时间不能为空");
        require(snapshot.snapshotHash() != null && !snapshot.snapshotHash().isBlank(), "快照指纹不能为空");
        require(snapshot.requirements() != null && snapshot.stocks() != null && snapshot.arrivals() != null,
            "缺少完整的需求、库存或到货事实");
        require(horizonDays >= 1 && horizonDays <= 90, "推演窗口必须在 1..90 天内");
    }

    /** 单物料旧算法可保留上下文量；多物料仅在全部已核同根单位时才生成总量。 */
    private static BigDecimal comparableGapTotal(List<ShortageSimulationResult.Item> items, DecisionShortageBasis basis) {
        if (items.size() > 1 && (basis == null || basis.gaps().stream()
            .map(DecisionShortageBasis.Gap::baseUomId).distinct().count() != 1)) return null;
        return items.stream().map(ShortageSimulationResult.Item::maxGap).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 只核参与本窗口的正供给；日期覆盖不能绕过原始供给单位身份。 */
    private static Set<Long> unverifiedArrivalMaterials(DeliveryContextSnapshot snapshot,
            Map<Long, LocalDate> overrides, LocalDate start, LocalDate end) {
        Map<Long, Long> units = new TreeMap<>();
        for (var row : snapshot.requirements()) {
            if (row.requiredQty().signum() > 0 && !row.requiredDate().isBefore(start) && !row.requiredDate().isAfter(end)
                && positive(row.baseUomId())) units.putIfAbsent(row.materialId(), row.baseUomId());
        }
        Set<Long> unknown = new TreeSet<>();
        for (var arrival : snapshot.arrivals()) {
            if (!units.containsKey(arrival.materialId()) || arrival.purchaseLineId() == null || arrival.expectedDate() == null
                || arrival.remainingQty().signum() == 0) continue;
            LocalDate date = overrides.getOrDefault(arrival.purchaseLineId(), arrival.expectedDate());
            if (date.isBefore(start) || date.isAfter(end)) continue;
            if (!units.get(arrival.materialId()).equals(arrival.baseUomId())) unknown.add(arrival.materialId());
        }
        return unknown;
    }

    /** 完整逐物料依据只保留同一冻结窗口内已核单位需求，运行身份由AI持久运行绑定。 */
    private static DecisionShortageBasis shortageBasis(DeliveryContextSnapshot snapshot, LocalDate start, LocalDate end,
            List<ShortageSimulationResult.Item> items, Map<Long, LocalDate> peaks, Set<String> evidence,
            Map<Long, LocalDate> arrivalOverrides) {
        if (!unverifiedArrivalMaterials(snapshot, arrivalOverrides, start, end).isEmpty()) return null;
        List<DecisionShortageBasis.Requirement> rows = new ArrayList<>();
        Map<Long, Long> units = new TreeMap<>();
        Set<Long> identities = new HashSet<>();
        for (var row : snapshot.requirements()) {
            if (row.requiredQty().signum() <= 0 || row.requiredDate().isBefore(start) || row.requiredDate().isAfter(end)) continue;
            if (row.detailId() == null || row.detailId() <= 0 || row.baseUomId() == null || row.baseUomId() <= 0
                || !identities.add(row.detailId())) return null;
            Long old = units.putIfAbsent(row.materialId(), row.baseUomId());
            if (old != null && !old.equals(row.baseUomId())) return null;
            rows.add(new DecisionShortageBasis.Requirement(row.detailId(), row.materialId(), row.requiredDate(), row.requiredQty(), row.baseUomId()));
            evidence.add("ERP:REQUIREMENT:" + row.detailId() + ":required_qty");
            evidence.add("ERP:REQUIREMENT:" + row.detailId() + ":base_uom_id");
        }
        if (rows.isEmpty() || items.size() != units.size()) return null;
        List<DecisionShortageBasis.Gap> gaps = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (var item : items) {
            if (!units.containsKey(item.materialId()) || !seen.add(item.materialId()) || item.maxGap() == null
                || item.maxGap().signum() < 0 || item.maxGap().signum() > 0 && peaks.get(item.materialId()) == null) return null;
            String key = "DERIVED:SHORTAGE:BASE_" + item.materialId() + ":max_gap";
            gaps.add(new DecisionShortageBasis.Gap(item.materialId(), units.get(item.materialId()), item.maxGap(), peaks.get(item.materialId()), key));
            evidence.add(key);
        }
        rows.sort(Comparator.comparing(DecisionShortageBasis.Requirement::detailId));
        gaps.sort(Comparator.comparing(DecisionShortageBasis.Gap::materialId));
        String complete = "DERIVED:SHORTAGE:BASE:complete"; evidence.add(complete);
        evidence.add("snapshot:" + snapshot.snapshotHash());
        return new DecisionShortageBasis(null, "snapshot:" + snapshot.snapshotHash(), start, end, rows, gaps, complete);
    }

    private static TreeMap<Long, MaterialDemand> collectDemands(List<RequirementFact> requirements,
                                                                  LocalDate today, LocalDate end,
                                                                  Set<String> warnings) {
        TreeMap<Long, MaterialDemand> demands = new TreeMap<>();
        for (RequirementFact fact : requirements) {
            require(fact != null && positive(fact.materialId()) && fact.requiredDate() != null
                && fact.requiredQty() != null, "需求事实不完整");
            require(fact.workOrderId() == null || positive(fact.workOrderId()), "需求工单 ID 无效");
            if (fact.requiredQty().signum() <= 0 || fact.requiredDate().isBefore(today)
                || fact.requiredDate().isAfter(end)) continue;
            MaterialDemand material = demands.computeIfAbsent(fact.materialId(), id -> {
                PrdShortageInput input = new PrdShortageInput();
                input.setMaterialId(id);
                return new MaterialDemand(input, new TreeMap<>());
            });
            setMetadata(material.input(), fact, warnings);
            material.input().addDemand(fact.requiredDate(), fact.requiredQty());
            if (fact.workOrderId() != null) {
                material.workOrderIdsByDate().computeIfAbsent(fact.requiredDate(), date -> new TreeSet<>())
                    .add(fact.workOrderId());
            }
        }
        return demands;
    }

    private static void setMetadata(PrdShortageInput input, RequirementFact fact, Set<String> warnings) {
        Long id = fact.materialId();
        if (fact.materialCode() == null || fact.materialCode().isBlank()
            || fact.materialName() == null || fact.materialName().isBlank()) {
            warnings.add("物料 " + id + " 缺少编码或名称主数据");
        }
        input.setMaterialCode(mergeMetadata(input.getMaterialCode(), fact.materialCode(), id));
        input.setMaterialName(mergeMetadata(input.getMaterialName(), fact.materialName(), id));
    }

    private static String mergeMetadata(String previous, String next, Long materialId) {
        if (next == null || next.isBlank()) return previous;
        require(previous == null || previous.equals(next), "物料 " + materialId + " 主数据冲突");
        return next;
    }

    private static boolean missingRequirementScope(List<WorkOrderFact> workOrders,
                                                   List<RequirementFact> requirements, LocalDate today,
                                                   LocalDate end, Set<String> warnings) {
        if (workOrders == null) {
            warnings.add("INCOMPLETE_REQUIREMENT_SCOPE:NO_WORK_ORDER_FACTS");
            return true;
        }
        Set<Long> linked = new HashSet<>();
        for (RequirementFact fact : requirements) {
            if (fact.workOrderId() != null && fact.requiredQty().signum() > 0
                && !fact.requiredDate().isBefore(today) && !fact.requiredDate().isAfter(end)) {
                linked.add(fact.workOrderId());
            }
        }
        Set<Long> seen = new HashSet<>();
        boolean incomplete = false;
        for (WorkOrderFact workOrder : workOrders) {
            require(workOrder != null && positive(workOrder.id()), "范围工单 ID 无效");
            if (!seen.add(workOrder.id()) || !linked.contains(workOrder.id())) {
                warnings.add("INCOMPLETE_REQUIREMENT_SCOPE:WO:" + workOrder.id());
                incomplete = true;
            }
        }
        return incomplete;
    }
    private static Map<Long, StockTotal> collectStocks(List<StockFact> stocks, Set<Long> demandedMaterials) {
        Map<Long, StockTotal> totals = new HashMap<>();
        Set<Long> unknownMaterials = new HashSet<>();
        for (StockFact fact : stocks) {
            require(fact != null && positive(fact.materialId()), "库存事实身份不完整");
            if (!demandedMaterials.contains(fact.materialId())) continue;
            if (fact.stockQty() == null || fact.reservedQty() == null) {
                unknownMaterials.add(fact.materialId());
                continue; /** 同物料任一未知事实会撤销全部局部供给，不能使用已知子集。 */
            }
            require(fact.stockQty().signum() >= 0 && fact.reservedQty().signum() >= 0
                && (fact.safetyStock() == null || fact.safetyStock().signum() >= 0), "库存事实数量无效");
            StockTotal total = totals.computeIfAbsent(fact.materialId(), id -> new StockTotal(fact.safetyStock()));
            if (total.safety == null || fact.safetyStock() == null) {
                total.safety = null;
            } else {
                require(total.safety.compareTo(fact.safetyStock()) == 0,
                    "物料 " + fact.materialId() + " 安全库存冲突");
            }
            total.available = total.available.add(fact.stockQty().subtract(fact.reservedQty()));
        }
        unknownMaterials.forEach(totals::remove);
        return totals;
    }

    private static Map<Long, List<PurchaseArrivalLine>> collectArrivals(List<ArrivalFact> arrivals,
                                                                         Map<Long, ArrivalFact> byId,
                                                                         LocalDate today, Set<String> warnings) {
        Map<Long, List<PurchaseArrivalLine>> lines = new HashMap<>();
        for (ArrivalFact fact : arrivals) {
            require(fact != null && positive(fact.materialId()) && fact.remainingQty() != null,
                "采购到货事实不完整");
            require(fact.remainingQty().signum() >= 0, "采购到货剩余数量无效");
            if (fact.purchaseLineId() != null) {
                require(positive(fact.purchaseLineId()), "采购行 ID 无效");
                require(byId.putIfAbsent(fact.purchaseLineId(), fact) == null,
                    "采购行 ID 重复: " + fact.purchaseLineId());
            }
            if (fact.purchaseLineId() == null || fact.expectedDate() == null
                || fact.remainingQty().signum() == 0) {
                warnings.add("物料 " + fact.materialId() + " 采购到货事实无效，未计入在途"
                    + (fact.purchaseLineId() == null ? "" : "，采购行 " + fact.purchaseLineId()));
                continue;
            }
            if (fact.expectedDate().isBefore(today)) {
                warnings.add("OVERDUE_ARRIVAL_UNCONFIRMED:" + fact.purchaseLineId());
                continue;
            }
            lines.computeIfAbsent(fact.materialId(), id -> new ArrayList<>())
                .add(new PurchaseArrivalLine(fact.purchaseLineId(), fact.expectedDate(), fact.remainingQty()));
        }
        lines.values().forEach(list -> list.sort(Comparator.comparing(PurchaseArrivalLine::purchaseLineId)));
        return lines;
    }

    private static Map<Long, LocalDate> parseOverrides(Map<String, Object> overrides,
                                                        Map<Long, ArrivalFact> arrivalsById, LocalDate today) {
        if (overrides == null || overrides.isEmpty()) return Map.of();
        require(overrides.size() == 1 && overrides.containsKey(OVERRIDE_KEY), "不支持的到货覆盖字段");
        require(overrides.get(OVERRIDE_KEY) instanceof Map<?, ?>, "采购到货覆盖必须为 Map");
        Map<?, ?> requested = (Map<?, ?>) overrides.get(OVERRIDE_KEY);
        Map<Long, LocalDate> parsed = new TreeMap<>();
        for (Map.Entry<?, ?> entry : requested.entrySet()) {
            require(entry.getKey() instanceof String && ((String) entry.getKey()).matches("[0-9]+"),
                "采购行键必须为正十进制字符串");
            Long id = parseId((String) entry.getKey());
            require(!parsed.containsKey(id), "采购行覆盖 ID 重复: " + id);
            require(entry.getValue() instanceof String && ((String) entry.getValue()).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "采购行 " + id + " 到货日期必须为严格 ISO LocalDate 字符串");
            LocalDate date;
            try {
                date = LocalDate.parse((String) entry.getValue());
            } catch (DateTimeException invalid) {
                throw new IllegalArgumentException("采购行 " + id + " 到货日期无效", invalid);
            }
            require(!date.isBefore(today), "采购行 " + id + " 覆盖到货日不得早于快照日");
            ArrivalFact fact = arrivalsById.get(id);
            require(fact != null, "到货覆盖包含未知采购行 ID: " + id);
            require(fact.expectedDate() != null && fact.remainingQty().signum() > 0,
                "采购行 " + id + " 缺少完整有效的原始到货事实");
            parsed.put(id, date);
        }
        return parsed;
    }

    private static Map<Long, Long> parseSalesOrderLinks(List<WorkOrderSalesLinkFact> facts) {
        Map<Long, Long> links = new HashMap<>();
        for (WorkOrderSalesLinkFact fact : facts) {
            Long previous = links.putIfAbsent(fact.workOrderId(), fact.salesOrderId());
            require(previous == null || previous.equals(fact.salesOrderId()),
                "工单销售订单关联冲突: " + fact.workOrderId());
        }
        return links;
    }
    private static Long parseId(String id) {
        require(id != null && id.matches("[0-9]+"), "ID 必须是正十进制字符串");
        try {
            long parsed = Long.parseLong(id);
            require(parsed > 0, "ID 必须为正数: " + id);
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("ID 超出 Long 范围: " + id, invalid);
        }
    }

    private static boolean positive(Long id) {
        return id != null && id > 0;
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    /**
     * 仅允许明确可恢复的单物料失败与其他仿真隔离。
     */
    static final class MaterialSimulationException extends RuntimeException {
        MaterialSimulationException(String message) {
            super(message);
        }
    }
    private record MaterialDemand(PrdShortageInput input, TreeMap<LocalDate, TreeSet<Long>> workOrderIdsByDate) {
    }

    private static final class StockTotal {
        private BigDecimal available = BigDecimal.ZERO;
        private BigDecimal safety;

        private StockTotal(BigDecimal safety) {
            this.safety = safety;
        }
    }
}

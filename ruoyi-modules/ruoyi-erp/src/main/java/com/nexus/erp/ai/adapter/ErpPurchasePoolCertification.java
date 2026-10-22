package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.*;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.*;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.*;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader.RawLockedFacts;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

/** 当前物理事务内已独立读取来源的交叉核验；包内组件不是外部核验旗号签发入口。 */
final class ErpPurchasePoolCertification {
    // 与独立认证的数据库会话 +08:00 一致，不能用地区历史夏令时改变 DATETIME 原始业务日。
    static final ZoneOffset DATABASE_ZONE = ZoneOffset.ofHours(8);
    private static final Set<String> REQUIREMENT_STATES = Set.of("DRAFT", "CONFIRMED", "PARTIAL_ISSUED", "ISSUED", "CLOSED");
    private static final Set<String> APPLICATION_STATES = Set.of("DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "ORDERED", "CLOSED", "CANCELLED", "REJECTED");
    private ErpPurchasePoolCertification() { }

    static AllocationInput certify(CatalogSnapshot catalog, LineageSnapshot lineage,
                                   RawLockedFacts raw, Stock stock, LocalDate windowEnd,
                                   Set<Long> requestedDetailIds) {
        require(catalog != null && lineage != null && raw != null && stock != null, "独立来源缺失");
        var material = catalog.material();
        require(material != null && material.verified() && catalog.baseUnit() != null && catalog.baseUnit().verified()
            && Objects.equals(material.tenantId(), catalog.tenantId())
            && Objects.equals(material.baseUomId(), catalog.baseUnit().unitId()), "目录身份未核验");
        String tenant = catalog.tenantId();
        Long materialId = canonicalId(material.materialId());
        Long baseId = canonicalId(material.baseUomId());
        require(Objects.equals(tenant, lineage.tenantId()) && Objects.equals(materialId, lineage.materialId())
            && Objects.equals(baseId, lineage.baseUomId()) && "+08:00".equals(catalog.databaseTimeZone())
            && "+08:00".equals(lineage.databaseTimeZone()), "目录与账本租户物料单位不一致");
        LocalDate today = day(catalog.snapshotAt());
        require(today.equals(day(lineage.snapshotAt())) && windowEnd != null && !windowEnd.isBefore(today)
            && windowEnd.getYear() <= 9999 && requestedDetailIds != null && !requestedDetailIds.isEmpty()
            && requestedDetailIds.stream().allMatch(ErpPurchasePoolCertification::positive), "数据库窗口或申请来源未知");
        Integer scale = catalog.baseUnit().quantityScale();
        require(scale != null && scale >= 0 && scale <= 4 && stock.verified()
            && material.baseUomId().equals(stock.baseUnitId()), "库存与目录单位未核验");
        quantity(stock.quantity(), scale); quantity(stock.reserved(), scale); quantity(stock.safety(), scale);
        require(stock.reserved().compareTo(stock.quantity()) <= 0, "预留超过已核库存");

        var headers = index(raw.requirementHeads(), RawRequirementHead::getRequirementId);
        var detailRows = index(raw.requirementDetails(), RawRequirementDetail::getDetailId);
        Set<Long> referencedHeads = new HashSet<>();
        List<Demand> demands = new ArrayList<>();
        for (var row : raw.requirementDetails()) {
            require(tenant.equals(row.getTenantId()) && materialId.equals(row.getMaterialId())
                && "0".equals(row.getDelFlag()) && Long.valueOf(1).equals(row.getStatus())
                && baseId.equals(canonicalId(row.getUnit())), "MRP明细身份状态或原始单位未认证");
            var head = headers.get(row.getRequirementId());
            require(head != null && tenant.equals(head.getTenantId()) && "0".equals(head.getDelFlag())
                && head.getStatus() != null && REQUIREMENT_STATES.contains(head.getStatus()), "MRP关联头缺失或状态未知");
            referencedHeads.add(head.getRequirementId());
            quantity(row.getRequiredQuantity(), scale); quantity(row.getIssuedQuantity(), scale);
            require(row.getIssuedQuantity().compareTo(row.getRequiredQuantity()) <= 0, "MRP实发超过已核需求");
            BigDecimal net = row.getRequiredQuantity().subtract(row.getIssuedQuantity());
            if (Set.of("ISSUED", "CLOSED").contains(head.getStatus())) {
                require(net.signum() == 0, "终止MRP仍有未闭合需求");
            }
            LocalDate due = day(row.getRequiredDate());
            require(due.getYear() >= 1000 && due.getYear() <= 9999 && !due.isAfter(windowEnd), "MRP日期未知或超出完整窗口");
            demands.add(new Demand(row.getDetailId().toString(), due, net, baseId.toString(), true));
        }
        require(headers.keySet().equals(referencedHeads) && detailRows.keySet().containsAll(requestedDetailIds), "竞争池头或请求来源不完整");
        demands.sort(Comparator.comparing(Demand::dueDate).thenComparing(d -> Long.parseLong(d.detailId())));

        var applications = index(raw.requisitionLines(), RawRequisitionLine::getLineId);
        var applicationHeads = index(raw.requisitionHeads(), RawRequisitionHead::getRequisitionId);
        for (var row : raw.requisitionLines()) {
            materialRow(tenant, materialId, row.getTenantId(), row.getMaterialId(), row.getDelFlag());
            require(baseId.equals(canonicalId(row.getUnit())), "历史申请单位没有可靠基本单位归属");
            quantity(row.getApplyQuantity(), scale);
            require(row.getApplyQuantity().signum() > 0, "历史申请数量无效");
            var head = applicationHeads.get(row.getRequisitionId());
            require(head != null && tenant.equals(head.getTenantId()) && "0".equals(head.getDelFlag())
                && APPLICATION_STATES.contains(head.getStatus()) && head.getVersion() != null && head.getVersion() >= 0,
                "历史申请头状态或版本未知");
            if (row.getApprovedQuantity() != null) quantity(row.getApprovedQuantity(), scale);
        }
        var orders = index(raw.orderLines(), RawOrderLine::getLineId);
        var orderHeads = index(raw.orderHeads(), RawOrderHead::getOrderId);
        for (var row : raw.orderLines()) {
            materialRow(tenant, materialId, row.getTenantId(), row.getMaterialId(), row.getDelFlag());
            require(baseId.equals(canonicalId(row.getUnit())), "历史订单换算没有冻结基本单位归属");
            quantity(row.getOrderQuantity(), scale); quantity(row.getReceivedQuantity(), scale);
            quantity(row.getReturnedQuantity(), scale); quantity(row.getInvoicedQty(), scale);
            require(row.getOrderQuantity().signum() > 0 && row.getReceivedQuantity().compareTo(row.getOrderQuantity()) <= 0
                && row.getReturnedQuantity().signum() == 0 && row.getInvoicedQty().signum() == 0,
                "订单退货、发票或超收生命周期尚未完整认证");
            var head = orderHeads.get(row.getOrderId());
            require(head != null && tenant.equals(head.getTenantId()) && "0".equals(head.getDelFlag()), "订单头来源不完整");
        }
        var allocations = index(raw.allocations(), PuRequirementAllocationLedger::getAllocationId);
        Set<Long> boundAllocations = new HashSet<>(), boundApplications = new HashSet<>(), boundOrders = new HashSet<>();
        List<Commitment> commitments = new ArrayList<>();
        List<OrderLine> orderFacts = new ArrayList<>();
        for (var binding : lineage.allocations()) {
            require(binding != null && binding.ledger() != null, "独立占用绑定缺失");
            var ledger = binding.ledger();
            var locked = allocations.get(ledger.allocationId());
            require(locked != null && boundAllocations.add(ledger.allocationId())
                && tenant.equals(ledger.tenantId()) && materialId.equals(ledger.materialId())
                && baseId.equals(ledger.baseUomId()) && "MRP_DETAIL".equals(ledger.sourceType())
                && Objects.equals(locked.getVersion(), ledger.version())
                && Objects.equals(locked.getAllocationStatus(), ledger.allocationStatus())
                && Objects.equals(locked.getDetailId(), ledger.detailId())
                && Objects.equals(locked.getRequisitionLineId(), ledger.requisitionLineId())
                && Objects.equals(locked.getOrderLineId(), ledger.orderLineId())
                && Objects.equals(locked.getAllocationKey(), ledger.allocationKey()), "锁定占用与独立账本归属不一致");
            quantity(ledger.coveredDemandBaseQty(), scale); quantity(ledger.occupiedBaseQty(), scale);
            quantity(ledger.fulfilledBaseQty(), scale); quantity(ledger.releasedBaseQty(), scale);
            require(ledger.fulfilledBaseQty().signum() > 0 || binding.receiptFulfillment() == null,
                "无履约账本不能携带原收货证明");
            require(equal(locked.getCoveredDemandBaseQty(), ledger.coveredDemandBaseQty())
                && equal(locked.getOccupiedBaseQty(), ledger.occupiedBaseQty())
                && equal(locked.getFulfilledBaseQty(), ledger.fulfilledBaseQty())
                && equal(locked.getReleasedBaseQty(), ledger.releasedBaseQty())
                && ledger.coveredDemandBaseQty().signum() > 0
                && ledger.coveredDemandBaseQty().compareTo(ledger.occupiedBaseQty().add(ledger.fulfilledBaseQty()).add(ledger.releasedBaseQty())) == 0,
                "占用四量与当前读不守恒");
            var detail = detailRows.get(ledger.detailId());
            var application = applications.get(ledger.requisitionLineId());
            require(detail != null && binding.requirementDetail() != null && application != null
                && boundApplications.add(application.getLineId())
                && Objects.equals(binding.requirementDetail().detailId(), detail.getDetailId())
                && baseId.equals(canonicalId(binding.requirementDetail().unit()))
                && equal(detail.getRequiredQuantity(), binding.requirementDetail().requiredQuantity())
                && equal(detail.getIssuedQuantity(), binding.requirementDetail().issuedQuantity())
                && day(detail.getRequiredDate()).equals(day(binding.requirementDetail().requiredDate()))
                && binding.requisitionLine() != null && application.getLineId().equals(binding.requisitionLine().lineId())
                && equal(application.getApplyQuantity(), ledger.coveredDemandBaseQty())
                && equal(application.getApplyQuantity(), binding.requisitionLine().applyQuantity())
                && baseId.equals(canonicalId(binding.requisitionLine().unit())), "申请数量、MRP来源或历史单位未闭合");
            var head = applicationHeads.get(application.getRequisitionId());
            require(binding.requisitionHead() != null && Objects.equals(head.getRequisitionId(), binding.requisitionHead().requisitionId())
                && Objects.equals(head.getStatus(), binding.requisitionHead().status())
                && Objects.equals(head.getVersion(), binding.requisitionHead().version()), "申请头重读不一致");
            if ("REQUEST_INTENT".equals(ledger.allocationStatus())) {
                require(Set.of("DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "REJECTED").contains(head.getStatus())
                    && ledger.orderLineId() == null && ledger.fulfilledBaseQty().signum() == 0 && ledger.releasedBaseQty().signum() == 0,
                    "未转单意向生命周期未知");
                commitments.add(new Commitment(ledger.allocationKey(), ledger.detailId().toString(), CommitmentKind.PENDING,
                    ledger.occupiedBaseQty(), baseId.toString(), true));
            } else if ("ORDER_COMMITTED_NOT_TIMELY_SUPPLY".equals(ledger.allocationStatus())) {
                var order = orders.get(ledger.orderLineId());
                require(order != null && boundOrders.add(order.getLineId()) && binding.orderLine() != null
                    && binding.orderHead() != null && order.getLineId().equals(binding.orderLine().lineId())
                    && order.getRequisitionLineId().equals(application.getLineId())
                    && equal(order.getOrderQuantity(), ledger.coveredDemandBaseQty())
                    && equal(order.getOrderQuantity(), binding.orderLine().orderQuantity())
                    && equal(order.getReceivedQuantity(), binding.orderLine().receivedQuantity())
                    && equal(order.getReturnedQuantity(), binding.orderLine().returnedQuantity())
                    && equal(order.getInvoicedQty(), binding.orderLine().invoicedQty())
                    && equal(order.getReceivedQuantity(), ledger.fulfilledBaseQty())
                    && equal(order.getOrderQuantity().subtract(order.getReceivedQuantity()), ledger.occupiedBaseQty())
                    && ledger.releasedBaseQty().signum() == 0
                    && baseId.equals(canonicalId(binding.orderLine().unit())), "订单承接、已收履约或冻结单位未闭合");
                var orderHead = orderHeads.get(order.getOrderId());
                require(orderHead.getOrderId().equals(binding.orderHead().orderId())
                    && Objects.equals(orderHead.getStatus(), binding.orderHead().status())
                    && (("DRAFT".equals(orderHead.getStatus())&&orderHead.getExpectedArrivalDate()==null
                        &&binding.orderHead().expectedArrivalDate()==null)
                        ||(orderHead.getExpectedArrivalDate()!=null&&binding.orderHead().expectedArrivalDate()!=null
                        &&day(orderHead.getExpectedArrivalDate()).equals(day(binding.orderHead().expectedArrivalDate())))), "订单供应日重读不一致");
                OrderStatus status;
                if ("DRAFT".equals(orderHead.getStatus()) || "APPROVED".equals(orderHead.getStatus())) {
                    require(order.getReceivedQuantity().signum() == 0 && binding.receiptFulfillment() == null,
                        "未收货订单含未知履约来源");
                    status = "DRAFT".equals(orderHead.getStatus()) ? OrderStatus.DRAFT : OrderStatus.APPROVED;
                } else if (Set.of("PARTIALLY_RECEIVED", "PARTIAL_RECEIVED").contains(orderHead.getStatus())) {
                    // 头表示整单进度，不能要求同头每条行都严格部分收货；原行按实收独立分类。
                    if (order.getReceivedQuantity().signum() == 0) {
                        require(binding.receiptFulfillment() == null
                            && "PENDING".equals(order.getReceiveStatus())
                            && Objects.equals(order.getReceiveStatus(), binding.orderLine().receiveStatus()),
                            "部分收货头下待收原行含未知履约或错误行状态");
                        status = OrderStatus.APPROVED;
                    } else {
                        requireReceiptFulfillment(lineage, binding, order, orderHead, materialId, baseId, scale);
                        status = equal(order.getReceivedQuantity(), order.getOrderQuantity()) ? OrderStatus.RECEIVED : OrderStatus.PARTIALLY_RECEIVED;
                    }
                } else if ("RECEIVED".equals(orderHead.getStatus())) {
                    require(equal(order.getReceivedQuantity(), order.getOrderQuantity()), "全收货状态与原行数量不一致");
                    requireReceiptFulfillment(lineage, binding, order, orderHead, materialId, baseId, scale);
                    status = OrderStatus.RECEIVED;
                } else throw failed("订单状态未覆盖完整承接生命周期");
                orderFacts.add(new OrderLine(order.getLineId().toString(), ledger.allocationKey(), ledger.detailId().toString(), status,
                    (orderHead.getExpectedArrivalDate()==null?null:day(orderHead.getExpectedArrivalDate())), order.getOrderQuantity(), order.getReceivedQuantity(), baseId.toString(), true));
            } else if ("RELEASED".equals(ledger.allocationStatus())) {
                require(Set.of("CLOSED", "CANCELLED").contains(head.getStatus()) && ledger.occupiedBaseQty().signum() == 0
                    && ledger.fulfilledBaseQty().signum() == 0 && ledger.orderLineId() == null, "释放来源未完整闭合");
            } else throw failed("未知占用生命周期状态");
        }
        require(boundAllocations.equals(allocations.keySet()) && boundOrders.equals(orders.keySet()), "原始账本或订单来源覆盖不完整");
        for (var application : applications.values()) {
            String status = applicationHeads.get(application.getRequisitionId()).getStatus();
            require(Set.of("CLOSED", "CANCELLED").contains(status) || boundApplications.contains(application.getLineId()),
                "开放申请缺少可靠逐明细占用账");
        }
        Set<String> requested = new TreeSet<>(); requestedDetailIds.forEach(id -> requested.add(id.toString()));
        return new AllocationInput(new Scope(tenant, materialId.toString(), baseId.toString(), today, windowEnd, true, requested),
            stock, demands, orderFacts, commitments);
    }

    /** 原收货只能抵减同一原行的未收量，当前库存另计一次，不能将履约再次变成未来供给。 */
    private static void requireReceiptFulfillment(LineageSnapshot lineage, AllocationBinding binding,
            RawOrderLine order, RawOrderHead head, Long material, Long unit, int scale) {
        var proof = binding.receiptFulfillment();
        var ledger = binding.ledger();
        require(proof != null && Objects.equals(proof.allocationId(), ledger.allocationId())
            && Objects.equals(proof.orderId(), head.getOrderId()) && Objects.equals(proof.orderLineId(), order.getLineId())
            && Objects.equals(proof.materialId(), material) && Objects.equals(proof.baseUomId(), unit)
            && Objects.equals(head.getVendorId(), binding.orderHead().vendorId())
            && Objects.equals(head.getRequisitionId(), binding.orderHead().requisitionId())
            && Objects.equals(order.getReceiveStatus(), binding.orderLine().receiveStatus())
            && (equal(order.getReceivedQuantity(), order.getOrderQuantity())
                ? "FULL_RECEIVED".equals(order.getReceiveStatus()) : "PARTIAL_RECEIVED".equals(order.getReceiveStatus()))
            && equal(proof.fulfilledBaseQty(), ledger.fulfilledBaseQty())
            && equal(proof.fulfilledBaseQty(), order.getReceivedQuantity()) && proof.fulfilledBaseQty().signum() > 0
            && fingerprint(proof.sourceFingerprint())
            && Objects.equals(lineage.sourceVersions().get("receipt-fulfillment:" + ledger.allocationId()), proof.sourceFingerprint())
            && proof.receipts() != null && !proof.receipts().isEmpty(), "原收货履约证明身份、数量或来源版本不完整");
        Set<Long> receiptLines = new HashSet<>(); Set<String> receiptIdentities = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (var receipt : proof.receipts()) {
            require(receipt != null && positive(receipt.receiveId()) && positive(receipt.receiveLineId())
                && receiptLines.add(receipt.receiveLineId())
                && receiptIdentities.add(receipt.receiveId() + "|" + receipt.receiveLineId())
                && fingerprint(receipt.sourceFingerprint())
                && Objects.equals(lineage.sourceVersions().get("receipt-posting:" + receipt.receiveId()), receipt.sourceFingerprint()),
                "履约原收货头行重复、缺失或永久过账摘要不一致");
            quantity(receipt.quantity(), scale); require(receipt.quantity().signum() > 0, "原收货数量不是有效正数量");
            require(receipt.postedAt() != null && receipt.postedAt().matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"),
                "原过账时点不符合数据库秒精度合同");
            try {
                Instant at = LocalDateTime.parse(receipt.postedAt().replace(' ', 'T')).toInstant(DATABASE_ZONE);
                require(at.isAfter(Instant.EPOCH) && !at.isAfter(lineage.snapshotAt()), "原过账时点未知或在来源快照之后");
            } catch (DateTimeParseException invalid) {
                throw failed("原过账时点不是有效日期");
            }
            sum = sum.add(receipt.quantity());
        }
        require(equal(sum, proof.fulfilledBaseQty()), "逐原收货数量与账本履约净额不守恒");
        require(proof.sourceFingerprint().equals(ErpPurchaseAllocationReceiptSourceReader.fulfillmentFingerprint(proof)),
            "履约证明字段与独立复算摘要不一致");
    }

    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static <T> Map<Long, T> index(List<T> rows, Function<T, Long> identity) {
        Map<Long, T> result = new LinkedHashMap<>();
        require(rows != null, "原始来源集合未知");
        for (T row : rows) require(row != null && positive(identity.apply(row))
            && result.putIfAbsent(identity.apply(row), row) == null, "来源身份缺失或重复");
        return result;
    }
    private static void materialRow(String tenant, Long material, String actualTenant, Long actualMaterial, String deleted) {
        require(tenant.equals(actualTenant) && material.equals(actualMaterial) && "0".equals(deleted), "材料来源租户或删除状态未认证");
    }
    private static void quantity(BigDecimal value, int scale) {
        require(value != null && value.signum() >= 0 && value.stripTrailingZeros().scale() <= scale
            && (long) value.stripTrailingZeros().precision() - value.stripTrailingZeros().scale() <= 14, "数量精度、范围或原始NULL未认证");
    }
    private static Long canonicalId(String value) {
        require(value != null && value.matches("[1-9][0-9]{0,18}"), "来源单位或身份不是规范主键");
        try { long result = Long.parseLong(value); require(result > 0, "来源主键无效"); return result; }
        catch (NumberFormatException invalid) { throw failed("来源主键超限"); }
    }
    private static LocalDate day(Date value) { require(value != null, "来源日期未知"); return day(value.toInstant()); }
    private static LocalDate day(Instant value) { require(value != null, "来源时钟或日期未知"); return value.atZone(DATABASE_ZONE).toLocalDate(); }
    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean equal(BigDecimal left, BigDecimal right) { return left != null && right != null && left.compareTo(right) == 0; }
    private static void require(boolean value, String reason) { if (!value) throw failed(reason); }
    private static ServiceException failed(String reason) { return new ServiceException("采购完整来源未认证：" + reason); }
}

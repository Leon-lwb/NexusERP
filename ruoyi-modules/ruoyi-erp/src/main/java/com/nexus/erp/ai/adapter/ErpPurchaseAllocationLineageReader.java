package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.domain.enums.PurchaseOrderLineReceiveStatus;
import com.nexus.erp.pu.domain.enums.PurchaseOrderStatusEnum;
import com.nexus.erp.pu.mapper.ErpPurchaseAllocationLineageMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseAllocationLineageMapper.DatabaseClock;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderLine;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementDetail;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_APPROVED;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_APPROVING;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_CANCELLED;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_CLOSED;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_DRAFT;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_ORDERED;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_REJECTED;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_SUBMITTED;

/**
 * read 在实际 ERP 只读、可重复读新事务代理内冻结单物料采购占用的有界归属事实。
 * readLocked 仅在调用方已核验的同连接可写 ERP 事务内当前读，不自行补绑或开启事务。
 * 只接租户与物料身份，不接受调用方事实、核验布尔或时钟；不从旧 source_id 推断
 * MRP 明细，不将物料汇总或空结果当作可靠来源、库存零值或可采购额度。
 * read仅支持未转单意图、未履约承接及完整未转单释放；readLocked的部分/全收履约
 * 必须有永久过账完整重放与同账本全事件证明。退货、发票及订单关闭释放仍拒绝。MRP头物料是成品。
 * 日期沿既有明确 +08:00 墙上 DATETIME 合同立即复制为 Instant，不变更任何时区。
 * 快照只描述绑定、原始数量/单位和实际版本，不认证旧量纲、供应条款、全池库存、
 * 净需求或权限，不生成 allocator 可信 DTO，不接 controller 或新版业务写入门禁。
 * 实际执行仍须由统一 ERP 写事务锁定重读；本快照及代理上下文不授予写入资格。
 */
@Service
@DS("erp")
public class ErpPurchaseAllocationLineageReader {
    private static final String REQUEST_INTENT = "REQUEST_INTENT";
    private static final String ORDER_COMMITTED = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    private static final String RELEASED = "RELEASED";
    private static final Set<String> REQUISITION_STATES = Set.of(STATUS_DRAFT, STATUS_SUBMITTED,
        STATUS_APPROVING, STATUS_APPROVED, STATUS_ORDERED, STATUS_CLOSED, STATUS_CANCELLED, STATUS_REJECTED);
    private static final Set<String> INTENT_REQUISITION_STATES = Set.of(STATUS_DRAFT, STATUS_SUBMITTED,
        STATUS_APPROVING, STATUS_APPROVED, STATUS_REJECTED);
    private static final Set<String> PRE_RECEIPT_ORDER_STATES = Set.of(PurchaseOrderStatusEnum.DRAFT.getCode(),
        PurchaseOrderStatusEnum.SUBMITTED.getCode(), PurchaseOrderStatusEnum.APPROVING.getCode(),
        PurchaseOrderStatusEnum.APPROVED.getCode(), PurchaseOrderStatusEnum.REJECTED.getCode());
    /** 对应现行 MRP 字典，仅辨识头状态，不认证该状态下仍有净需求或可用数量。 */
    private static final Set<String> REQUIREMENT_STATES = Set.of("DRAFT", "CONFIRMED", "PARTIAL_ISSUED", "ISSUED", "CLOSED");

    private final ErpPurchaseAllocationLineageMapper mapper;
    private final PuRequirementErpTransactionQualification transactionQualification;
    /** 可选内部来源；旧Spring组合缺少该Bean时仍不能签发任何正履约证明。 */
    @Autowired(required = false)
    private ErpPurchaseAllocationReceiptSourceReader receiptSources;

    /** 构造只保存固定 SELECT Mapper，不读取数据库或自动补全业务事实。 */
    public ErpPurchaseAllocationLineageReader(ErpPurchaseAllocationLineageMapper mapper) {
        this(mapper, null);
    }

    /** 仅保存固定来源 Mapper 和事务资格校验；不借连接、补绑或授予业务资格。 */
    @Autowired
    public ErpPurchaseAllocationLineageReader(ErpPurchaseAllocationLineageMapper mapper,
        PuRequirementErpTransactionQualification transactionQualification) {
        this(mapper, transactionQualification, null);
    }

    /** 显式离线组合入口；旧两参构造保持Spring兼容，可选来源绝非外部授权旗号。 */
    public ErpPurchaseAllocationLineageReader(ErpPurchaseAllocationLineageMapper mapper,
        PuRequirementErpTransactionQualification transactionQualification,
        ErpPurchaseAllocationReceiptSourceReader receiptSources) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transactionQualification = transactionQualification;
        this.receiptSources = receiptSources;
    }

    /**
     * 身份及真实代理新事务前置；每次查询返回后立即冻结，再核对全部主键与引用。
     * 全部开放申请/订单行须有可靠账本链；被引用的 MRP 明细按 ID 补读而不套窗口。
     * 申请关闭不能释放已由订单承接的占用，数量及单位仅保留原始事实而不认证额度。
     *
     * @param tenantId 必须与启用的当前租户一致的身份
     * @param materialId 本次单物料的真实正数主键
     * @return 无任何授权、额度或可执行旗号的不可变归属快照
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public LineageSnapshot read(String tenantId, Long materialId) {
        checkTenant(tenantId);
        require(positive(materialId), "物料身份必须为正数");
        checkReadTransaction();
        return readSources(tenantId, materialId, false);
    }

    /** 共用完整归属认证，锁定模式不扩大履约、历史量纲或来源类型的支持范围。 */
    private LineageSnapshot readSources(String tenantId, Long materialId, boolean locked) {
        DatabaseClock clock = selectMapper(locked ? mapper::lockDatabaseClock : mapper::selectDatabaseClock);
        Instant snapshotAt = locked ? lockedDatabaseInstant(clock) : databaseInstant(clock);
        MaterialSource material = freezeMaterial(selectMapper(() -> locked ? mapper.lockMaterial(tenantId, materialId) : mapper.selectMaterial(tenantId, materialId)));
        require(tenantId.equals(material.tenantId()) && materialId.equals(material.materialId())
            && Long.valueOf(1L).equals(material.status()) && "0".equals(material.delFlag())
            && positive(material.baseUomId()), "真实物料或基本单位身份未核验");

        List<AllocationSource> allocationRows = freezeRows(selectMapper(() -> locked ? mapper.lockAllocations(tenantId, materialId) : mapper.selectAllocations(tenantId, materialId)),
            ErpPurchaseAllocationLineageReader::freezeAllocation);
        Map<Long, AllocationSource> allocations = index(allocationRows, AllocationSource::allocationId);
        Set<String> allocationKeys = new HashSet<>();
        Set<AllocationIdentity> identities = new HashSet<>();
        Set<Long> inheritedOrderIds = new HashSet<>();
        for (AllocationSource allocation : allocationRows) {
            checkAllocation(allocation, tenantId, materialId, material.baseUomId(), locked && receiptSources != null);
            require(allocationKeys.add(allocation.allocationKey()), "占用来源键重复");
            require(identities.add(new AllocationIdentity(allocation.requisitionLineId(), allocation.detailId(), allocation.allocationNo())),
                "同申请行和需求明细的占用序号重复");
            if (allocation.orderLineId() != null) {
                require(inheritedOrderIds.add(allocation.orderLineId()), "同订单行存在重复承接");
            }
        }

        List<RequisitionLineSource> requisitionRows = freezeRows(
            selectMapper(() -> locked ? mapper.lockRequisitionLines(tenantId, materialId) : mapper.selectRequisitionLines(tenantId, materialId)), ErpPurchaseAllocationLineageReader::freezeRequisitionLine);
        Map<Long, RequisitionLineSource> requisitionLines = index(requisitionRows, RequisitionLineSource::lineId);
        for (RequisitionLineSource line : requisitionRows) {
            checkMaterialRow(line.tenantId(), line.materialId(), line.delFlag(), tenantId, materialId);
            require(positive(line.requisitionId()), "申请行父键未核验");
        }

        List<OrderLineSource> orderRows = freezeRows(selectMapper(() -> locked ? mapper.lockOrderLines(tenantId, materialId) : mapper.selectOrderLines(tenantId, materialId)),
            ErpPurchaseAllocationLineageReader::freezeOrderLine);
        Map<Long, OrderLineSource> orderLines = index(orderRows, OrderLineSource::lineId);
        Map<Long, Long> orderByRequisitionLine = new LinkedHashMap<>();
        for (OrderLineSource line : orderRows) {
            checkMaterialRow(line.tenantId(), line.materialId(), line.delFlag(), tenantId, materialId);
            require(positive(line.orderId()) && positive(line.requisitionLineId()), "订单行父键或申请来源键未核验");
            require(zero(line.returnedQuantity()) && zero(line.invoicedQty()), "订单行收货、退货或下游履约事实未认证");
            boolean pending = PurchaseOrderLineReceiveStatus.PENDING.getCode().equals(line.receiveStatus()) && zero(line.receivedQuantity());
            boolean received = locked && receiptSources != null && decimal184(line.receivedQuantity())
                && line.receivedQuantity().signum() > 0 && decimal184(line.orderQuantity()) && line.orderQuantity().signum() > 0
                && line.receivedQuantity().compareTo(line.orderQuantity()) <= 0
                && (line.receivedQuantity().compareTo(line.orderQuantity()) == 0
                    ? PurchaseOrderLineReceiveStatus.FULL_RECEIVED.getCode().equals(line.receiveStatus())
                    : PurchaseOrderLineReceiveStatus.PARTIAL_RECEIVED.getCode().equals(line.receiveStatus()));
            require(pending || received, "订单行收货、退货或下游履约事实未认证");
            require(orderByRequisitionLine.putIfAbsent(line.requisitionLineId(), line.lineId()) == null,
                "同申请行被多个订单行承接");
        }

        List<Long> requisitionIds = ids(requisitionRows, RequisitionLineSource::requisitionId);
        List<RequisitionHeadSource> requisitionHeadRows = requisitionIds.isEmpty() ? List.of() : freezeRows(
            selectMapper(() -> locked ? mapper.lockRequisitionHeads(tenantId, requisitionIds) : mapper.selectRequisitionHeads(tenantId, requisitionIds)), ErpPurchaseAllocationLineageReader::freezeRequisitionHead);
        Map<Long, RequisitionHeadSource> requisitionHeads = index(requisitionHeadRows, RequisitionHeadSource::requisitionId);
        requireReferences(requisitionHeads, requisitionIds);
        for (RequisitionHeadSource head : requisitionHeadRows) {
            checkHead(head.tenantId(), head.delFlag(), tenantId);
            require(head.status() != null && REQUISITION_STATES.contains(head.status())
                && head.version() != null && head.version() >= 0, "申请头状态或版本未核验");
        }

        List<Long> orderIds = ids(orderRows, OrderLineSource::orderId);
        List<OrderHeadSource> orderHeadRows = orderIds.isEmpty() ? List.of() : freezeRows(
            selectMapper(() -> locked ? mapper.lockOrderHeads(tenantId, orderIds) : mapper.selectOrderHeads(tenantId, orderIds)), ErpPurchaseAllocationLineageReader::freezeOrderHead);
        Map<Long, OrderHeadSource> orderHeads = index(orderHeadRows, OrderHeadSource::orderId);
        requireReferences(orderHeads, orderIds);
        for (OrderHeadSource head : orderHeadRows) {
            checkHead(head.tenantId(), head.delFlag(), tenantId);
            boolean receiving = locked && receiptSources != null && head.status() != null
                && Set.of("PARTIALLY_RECEIVED", "PARTIAL_RECEIVED", "RECEIVED").contains(head.status());
            require(positive(head.requisitionId()) && head.status() != null
                && (PRE_RECEIPT_ORDER_STATES.contains(head.status()) || receiving), "订单来源或下游生命周期未认证");
        }

        List<Long> detailIds = ids(allocationRows, AllocationSource::detailId);
        List<RequirementDetailSource> detailRows = detailIds.isEmpty() ? List.of() : freezeRows(
            selectMapper(() -> locked ? mapper.lockRequirementDetails(tenantId, detailIds) : mapper.selectRequirementDetails(tenantId, detailIds)), ErpPurchaseAllocationLineageReader::freezeRequirementDetail);
        Map<Long, RequirementDetailSource> details = index(detailRows, RequirementDetailSource::detailId);
        requireReferences(details, detailIds);
        for (RequirementDetailSource detail : detailRows) {
            checkMaterialRow(detail.tenantId(), detail.materialId(), detail.delFlag(), tenantId, materialId);
            require(positive(detail.requirementId()) && Long.valueOf(1L).equals(detail.status()), "MRP明细父键或状态未核验");
        }

        List<Long> requirementIds = ids(detailRows, RequirementDetailSource::requirementId);
        List<RequirementHeadSource> requirementHeadRows = requirementIds.isEmpty() ? List.of() : freezeRows(
            selectMapper(() -> locked ? mapper.lockRequirementHeads(tenantId, requirementIds) : mapper.selectRequirementHeads(tenantId, requirementIds)), ErpPurchaseAllocationLineageReader::freezeRequirementHead);
        Map<Long, RequirementHeadSource> requirementHeads = index(requirementHeadRows, RequirementHeadSource::requirementId);
        requireReferences(requirementHeads, requirementIds);
        for (RequirementHeadSource head : requirementHeadRows) {
            checkHead(head.tenantId(), head.delFlag(), tenantId);
            require(head.status() != null && REQUIREMENT_STATES.contains(head.status()), "MRP头状态未核验");
        }

        List<AllocationBinding> bindings = new ArrayList<>();
        Map<String, String> versions = new LinkedHashMap<>();
        Set<Long> boundRequisitionLines = new HashSet<>();
        Set<Long> boundOrderLines = new HashSet<>();
        for (AllocationSource allocation : allocations.values().stream().sorted(Comparator.comparing(AllocationSource::allocationId)).toList()) {
            RequirementDetailSource detail = details.get(allocation.detailId());
            require(detail != null, "账本引用的真实MRP明细缺失");
            RequirementHeadSource requirementHead = requirementHeads.get(detail.requirementId());
            require(requirementHead != null, "MRP明细的真实父头缺失");
            RequisitionLineSource requisitionLine = requisitionLines.get(allocation.requisitionLineId());
            require(requisitionLine != null, "账本引用的真实申请行缺失");
            RequisitionHeadSource requisitionHead = requisitionHeads.get(requisitionLine.requisitionId());
            require(requisitionHead != null, "申请行的真实父头缺失");
            OrderLineSource orderLine = null;
            OrderHeadSource orderHead = null;
            ReceiptFulfillmentProof receiptFulfillment = null;
            if (ORDER_COMMITTED.equals(allocation.allocationStatus())) {
                orderLine = orderLines.get(allocation.orderLineId());
                require(orderLine != null && requisitionLine.lineId().equals(orderLine.requisitionLineId()), "订单行与申请行归属不一致");
                orderHead = orderHeads.get(orderLine.orderId());
                require(orderHead != null && requisitionHead.requisitionId().equals(orderHead.requisitionId()), "订单头与申请头归属不一致");
                require(STATUS_ORDERED.equals(requisitionHead.status()) || STATUS_CLOSED.equals(requisitionHead.status()),
                    "申请状态与实际订单承接不一致");
                require(boundOrderLines.add(orderLine.lineId()), "同订单行重复绑定");
                if (allocation.fulfilledBaseQty().signum() > 0) {
                    require(locked && receiptSources != null, "正履约缺少同ERP永久过账来源提供者");
                    transactionQualification.requireSourceChainTransaction();
                    var receipt = receiptSources.readLocked(tenantId, allocation, orderLine, orderHead);
                    require(receipt != null && receipt.proof() != null, "完整履约认证没有返回逐原行证明");
                    receiptFulfillment = receipt.proof();
                    require(allocation.allocationId().equals(receiptFulfillment.allocationId())
                        && orderHead.orderId().equals(receiptFulfillment.orderId()) && orderLine.lineId().equals(receiptFulfillment.orderLineId())
                        && materialId.equals(receiptFulfillment.materialId()) && material.baseUomId().equals(receiptFulfillment.baseUomId())
                        && allocation.fulfilledBaseQty().compareTo(receiptFulfillment.fulfilledBaseQty()) == 0
                        && receiptFulfillment.sourceFingerprint().equals(receipt.sourceVersions().get("receipt-fulfillment:" + allocation.allocationId())),
                        "履约证明与当前账本/订单/单位或来源版本不同源");
                    for (var version : receipt.sourceVersions().entrySet()) {
                        String old = versions.putIfAbsent(version.getKey(), version.getValue());
                        require(old == null || old.equals(version.getValue()), "共享收货来源在同认证链中发生摘要漂移");
                    }
                } else {
                    boolean partialSiblingHead = locked && receiptSources != null
                        && Set.of("PARTIALLY_RECEIVED", "PARTIAL_RECEIVED").contains(orderHead.status());
                    require(zero(orderLine.receivedQuantity()) && (PRE_RECEIPT_ORDER_STATES.contains(orderHead.status()) || partialSiblingHead),
                        "零履约账本不能解释真实已收或已收货订单头");
                }
            } else {
                require(!orderByRequisitionLine.containsKey(requisitionLine.lineId()), "未转单意图或释放存在真实订单承接");
                if (REQUEST_INTENT.equals(allocation.allocationStatus())) {
                    require(INTENT_REQUISITION_STATES.contains(requisitionHead.status()), "申请状态与未转单意图不一致");
                } else {
                    require(STATUS_CLOSED.equals(requisitionHead.status()) || STATUS_CANCELLED.equals(requisitionHead.status()),
                        "完整意图释放与申请终态不一致");
                }
            }
            boundRequisitionLines.add(requisitionLine.lineId());
            bindings.add(new AllocationBinding(allocation, detail, requirementHead, requisitionLine, requisitionHead, orderLine, orderHead, receiptFulfillment));
            versions.put("allocation:" + allocation.allocationId(), allocation.version().toString());
            versions.put("requisition:" + requisitionHead.requisitionId(), requisitionHead.version().toString());
        }
        // partial头允许合法待收兄弟，但必须由本同材全集内另一原行的完整履约证明解释；不能仅信头状态。
        for (AllocationBinding binding : bindings) {
            OrderHeadSource head = binding.orderHead();
            if (head != null && Set.of("PARTIALLY_RECEIVED", "PARTIAL_RECEIVED").contains(head.status())) {
                require(bindings.stream().anyMatch(other -> other.orderHead() != null
                    && head.orderId().equals(other.orderHead().orderId()) && other.receiptFulfillment() != null),
                    "部分收货头缺少同材全池内已证明的真实已收兄弟");
            }
        }
        for (RequisitionLineSource line : requisitionRows) {
            RequisitionHeadSource head = requisitionHeads.get(line.requisitionId());
            require(STATUS_CLOSED.equals(head.status()) || STATUS_CANCELLED.equals(head.status()) || boundRequisitionLines.contains(line.lineId()),
                "开放申请行存在未绑定来源，不能按同物料或旧source_id推断");
        }
        for (OrderLineSource line : orderRows) {
            require(boundOrderLines.contains(line.lineId()), "开放订单行存在未绑定来源");
        }
        if (locked) {
            // 所有实际来源锁定后复核同连接业务时钟及 +08:00 合同，不采用应用时钟。
            clock = selectMapper(mapper::lockDatabaseClock);
            snapshotAt = lockedDatabaseInstant(clock);
        }
        checkTenant(tenantId);
        return new LineageSnapshot(tenantId, materialId, material.baseUomId(), snapshotAt, clock.databaseTimeZone(), bindings, versions);
    }

    /**
     * 只加入经参与式资格校验的同连接可写 ERP 事务；所有来源和引用均用固定当前读。
     * 上层 guard/竞争池须先于本入口；未知单位仍为原始事实，不颁发可信换算或采购额度。
     */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public LineageSnapshot readLocked(String tenantId, Long materialId) {
        checkTenant(tenantId);
        require(positive(materialId), "物料身份必须为正数");
        require(transactionQualification != null, "锁定读取缺少真实 ERP 事务资格校验依赖");
        transactionQualification.requireParticipatingWritableErpTransaction();
        return readSources(tenantId, materialId, true);
    }

    /** 快照只含实际绑定和已有版本，复制集合，不颁发库存、采购量或可执行资格。 */
    public record LineageSnapshot(String tenantId, Long materialId, Long baseUomId, Instant snapshotAt,
                                  String databaseTimeZone, List<AllocationBinding> allocations, Map<String, String> sourceVersions) {
        public LineageSnapshot {
            allocations = List.copyOf(allocations);
            sourceVersions = Map.copyOf(sourceVersions);
        }
    }

    /** 一个账本到真实头行的引用；未转单或已完整释放的意图没有订单头行。 */
    public record AllocationBinding(AllocationSource ledger, RequirementDetailSource requirementDetail,
                                    RequirementHeadSource requirementHead, RequisitionLineSource requisitionLine,
                                    RequisitionHeadSource requisitionHead, OrderLineSource orderLine, OrderHeadSource orderHead,
                                    ReceiptFulfillmentProof receiptFulfillment) {
        public AllocationBinding(AllocationSource ledger, RequirementDetailSource requirementDetail,
                RequirementHeadSource requirementHead, RequisitionLineSource requisitionLine,
                RequisitionHeadSource requisitionHead, OrderLineSource orderLine, OrderHeadSource orderHead) {
            this(ledger, requirementDetail, requirementHead, requisitionLine, requisitionHead, orderLine, orderHead, null);
        }
    }

    /** 同事务原过账重放及全部履约事件的逐账本证明，不接受外部核验旗号。 */
    public record ReceiptFulfillmentProof(Long allocationId, Long orderId, Long orderLineId, Long materialId,
            Long baseUomId, BigDecimal fulfilledBaseQty, List<ReceiptProof> receipts, String sourceFingerprint) {
        public ReceiptFulfillmentProof { receipts = List.copyOf(receipts); }
    }

    /** 数量来自真实收货原行，摘要与时点来自永久过账的完整当前重放。 */
    public record ReceiptProof(Long receiveId, Long receiveLineId, BigDecimal quantity,
            String sourceFingerprint, String postedAt) { }

    /** 四量只校验账本局部守恒，不证明历史数量的单位换算、需求覆盖资格或剩余额度。 */
    public record AllocationSource(Long allocationId, String tenantId, Long materialId, String sourceType,
                                   Long detailId, Long requisitionLineId, Integer allocationNo, Long orderLineId,
                                   Long baseUomId, BigDecimal coveredDemandBaseQty, BigDecimal occupiedBaseQty,
                                   BigDecimal fulfilledBaseQty, BigDecimal releasedBaseQty, String allocationStatus,
                                   String allocationKey, String decisionIdempotencyKey, Integer version,
                                   Instant createTime, Instant transferTime, Instant releasedAt, String releaseReason, Instant updateTime) { }

    /** MRP 明细原始数量和日期，不计算净需求，不将旧库存字段认作本次完整库存。 */
    public record RequirementDetailSource(Long detailId, String tenantId, Long requirementId, Long materialId,
                                          String unit, String sourceType, BigDecimal requiredQuantity, BigDecimal issuedQuantity,
                                          BigDecimal stockQuantity, BigDecimal shortageQuantity, Instant requiredDate,
                                          Long status, String delFlag, Integer backflushFlag, BigDecimal actualConsumedQty,
                                          Instant createTime, Instant updateTime) { }

    /** MRP 头只核引用身份、租户、删除和明确状态；成品物料及其他原字段原样冻结。 */
    public record RequirementHeadSource(Long requirementId, String tenantId, String requirementNo, Long parentRequirementId,
                                        Long planId, String planNo, Long bomId, Long materialId, Instant requirementDate,
                                        String sourceType, String status, Instant confirmedTime, Instant issuedTime,
                                        Instant closedTime, String delFlag, Instant createTime, Instant updateTime) { }

    /** sourceId 不解释为 detailId；原始单位和申请数量不得升级为 allocator 可信输入。 */
    public record RequisitionLineSource(Long lineId, String tenantId, Long requisitionId, Long materialId,
                                       String unit, BigDecimal applyQuantity, BigDecimal approvedQuantity, BigDecimal shortageQuantity,
                                       Instant requiredDate, String sourceType, Long sourceId, String attr1, String attr2,
                                       Long attr3, Long attr4, String delFlag, Instant createTime, Instant updateTime) { }

    /** 申请状态采用真实服务常量；保留数据库版本和全部原始决策审计字段。 */
    public record RequisitionHeadSource(Long requisitionId, String tenantId, String requisitionNo, String sourceType,
                                       String status, Instant requisitionDate, Instant requiredDate, Long decisionRunId,
                                       Long decisionActionId, String decisionIdempotencyKey, Long selectFlowId, Integer version,
                                       String delFlag, Instant createTime, Instant updateTime) { }

    /** 下游量只用于识别本片段无法认证的生命周期，不认证订购量或历史计量单位。 */
    public record OrderLineSource(Long lineId, String tenantId, Long orderId, Long requisitionLineId, Long materialId,
                                 String unit, BigDecimal orderQuantity, BigDecimal receivedQuantity, BigDecimal returnedQuantity,
                                 BigDecimal invoicedQty, String receiveStatus, String delFlag, Instant createTime, Instant updateTime) { }

    /** 预计到货日期只是原始事实，不把需求日、提前期或已批准状态转成及时供应许可。 */
    public record OrderHeadSource(Long orderId, String tenantId, String orderNo, Long requisitionId, Long vendorId,
                                 String status, Instant orderDate, Instant expectedArrivalDate, String delFlag,
                                 Instant createTime, Instant updateTime) { }

    private record MaterialSource(String tenantId, Long materialId, Long baseUomId, Long status, String delFlag) { }
    private record AllocationIdentity(Long requisitionLineId, Long detailId, Integer allocationNo) { }

    /**
     * 仅包围 Mapper 调用；所有 Runtime（包括 ServiceException）均创建固定新诊断，
     * 不复制原始 message、cause、detail 或 suppressed。查询外的受控核验保持原诊断；
     * Error 不拦截、不重试、不改包装，保留 VMError、ThreadDeath 等原对象身份。
     */
    private static <T> T selectMapper(Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException failure) {
            throw new ServiceException("采购占用归属来源读取失败");
        }
    }

    private static void checkTenant(String tenantId) {
        require(TenantHelper.isEnable() && tenantId != null && !tenantId.isEmpty() && tenantId.length() <= 20
            && tenantId.chars().allMatch(character -> character >= '0' && character <= '9')
            && tenantId.equals(TenantHelper.getTenantId()), "当前租户未启用、缺失或与请求不一致");
    }

    /**
     * 要求本入口的真实事务切面新事务与 ERP 路由；模板事务或手工 ThreadLocal 不代替代理。
     * 该拒绝条件不证明后续写事务的物理连接归属或权限，也不修补调用方事务上下文。
     */
    private static void checkReadTransaction() {
        TransactionStatus status;
        try {
            status = TransactionAspectSupport.currentTransactionStatus();
        } catch (NoTransactionException missingAdvice) {
            throw rejected("必须通过实际只读新事务服务代理调用");
        }
        require(status.isNewTransaction() && !status.isCompleted()
            && (ErpPurchaseAllocationLineageReader.class.getName() + ".read")
                .equals(TransactionSynchronizationManager.getCurrentTransactionName())
            && "erp".equals(DynamicDataSourceContextHolder.peek())
            && TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            && Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()),
            "必须通过ERP只读可重复读新事务服务代理调用");
    }

    /** 先核原始会话 +08:00 合同，再精确解码十进制秒；不借用 JVM 日期或舍入。 */
    /** 当前读的固定 SQL 产生至多六位小数秒；不把更高精度的未知来源升级为真实数据库时钟。 */
    private static Instant lockedDatabaseInstant(DatabaseClock clock) {
        require(clock != null && clock.epochSeconds() != null && clock.epochSeconds().stripTrailingZeros().scale() <= 6,
            "数据库时钟精度超出固定六位小数秒合同");
        return databaseInstant(clock);
    }

    private static Instant databaseInstant(DatabaseClock clock) {
        require(clock != null && "+08:00".equals(clock.databaseTimeZone())
            && clock.epochSeconds() != null && clock.epochSeconds().signum() > 0, "数据库时钟或墙上日期存储合同未核验");
        try {
            long seconds = clock.epochSeconds().setScale(0, RoundingMode.DOWN).longValueExact();
            int nanos = clock.epochSeconds().subtract(BigDecimal.valueOf(seconds)).movePointRight(9).intValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (ArithmeticException | DateTimeException invalidClock) {
            throw rejected("数据库时钟范围或精度未核验");
        }
    }

    private static void checkAllocation(AllocationSource row, String tenantId, Long materialId, Long baseUomId,
            boolean lockedReceiptSources) {
        require(tenantId.equals(row.tenantId()) && materialId.equals(row.materialId()) && "MRP_DETAIL".equals(row.sourceType())
            && positive(row.detailId()) && positive(row.requisitionLineId()) && positive(row.baseUomId())
            && baseUomId.equals(row.baseUomId()) && row.allocationNo() != null && row.allocationNo() > 0
            && row.version() != null && row.version() >= 0 && identityText(row.allocationKey(), 128)
            && identityText(row.decisionIdempotencyKey(), 128) && row.createTime() != null, "账本身份、来源键或版本未核验");
        require(decimal184(row.coveredDemandBaseQty()) && decimal184(row.occupiedBaseQty())
            && decimal184(row.fulfilledBaseQty()) && decimal184(row.releasedBaseQty())
            && row.coveredDemandBaseQty().signum() > 0
            && row.coveredDemandBaseQty().compareTo(row.occupiedBaseQty().add(row.fulfilledBaseQty()).add(row.releasedBaseQty())) == 0,
            "账本四量不符合DECIMAL(18,4)非负及覆盖守恒");
        require(zero(row.fulfilledBaseQty()) || (lockedReceiptSources && ORDER_COMMITTED.equals(row.allocationStatus())),
            "账本履约事实未认证");
        if (REQUEST_INTENT.equals(row.allocationStatus())) {
            require(noOrderOrRelease(row) && zero(row.releasedBaseQty())
                && row.coveredDemandBaseQty().compareTo(row.occupiedBaseQty()) == 0, "申请意图含订单、释放或非完整占用");
        } else if (ORDER_COMMITTED.equals(row.allocationStatus())) {
            require(positive(row.orderLineId()) && row.transferTime() != null && row.releasedAt() == null && row.releaseReason() == null
                && zero(row.releasedBaseQty()) && row.coveredDemandBaseQty().compareTo(row.occupiedBaseQty().add(row.fulfilledBaseQty())) == 0,
                "订单承接身份或占用/真实履约事实不完整");
        } else if (RELEASED.equals(row.allocationStatus())) {
            require(row.orderLineId() == null && row.transferTime() == null && row.releasedAt() != null
                && text(row.releaseReason(), 255) && zero(row.occupiedBaseQty())
                && row.coveredDemandBaseQty().compareTo(row.releasedBaseQty()) == 0, "未转单意图的完整释放事实未核验");
        } else {
            throw rejected("账本生命周期状态未支持");
        }
    }

    private static boolean noOrderOrRelease(AllocationSource row) {
        return row.orderLineId() == null && row.transferTime() == null && row.releasedAt() == null && row.releaseReason() == null;
    }

    private static void checkMaterialRow(String rowTenant, Long rowMaterial, String delFlag, String tenantId, Long materialId) {
        require(tenantId.equals(rowTenant) && materialId.equals(rowMaterial) && "0".equals(delFlag), "材料来源跨租户、跨物料或删除状态未知");
    }

    private static void checkHead(String rowTenant, String delFlag, String tenantId) {
        require(tenantId.equals(rowTenant) && "0".equals(delFlag), "引用头跨租户、已删除或删除状态未知");
    }

    /** 不归一化空查询或 NULL 行；在后续 Mapper 调用前完成日期及实体字段复制。 */
    private static <R, T> List<T> freezeRows(List<R> rows, Function<R, T> freeze) {
        require(rows != null, "来源查询结果未知，不能视为空来源");
        List<T> result = new ArrayList<>(rows.size());
        for (R row : rows) {
            require(row != null, "来源集合含未知实体");
            result.add(freeze.apply(row));
        }
        return List.copyOf(result);
    }

    /** 主键必须为真实正数且唯一，不能用 Map 覆盖隐藏重复来源行。 */
    private static <T> Map<Long, T> index(List<T> rows, Function<T, Long> key) {
        Map<Long, T> indexed = new LinkedHashMap<>();
        for (T row : rows) {
            Long id = key.apply(row);
            require(positive(id), "来源主键未核验");
            require(indexed.putIfAbsent(id, row) == null, "来源主键重复");
        }
        return indexed;
    }

    private static <T> List<Long> ids(Collection<T> rows, Function<T, Long> key) {
        List<Long> values = new ArrayList<>();
        for (T row : rows) {
            Long id = key.apply(row);
            require(positive(id), "来源引用主键未核验");
            values.add(id);
        }
        return values.stream().distinct().sorted().toList();
    }

    private static void requireReferences(Map<Long, ?> indexed, List<Long> requested) {
        require(indexed.keySet().equals(Set.copyOf(requested)), "引用实体缺失、重复或挂错父键");
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean zero(BigDecimal value) {
        return value != null && value.signum() == 0;
    }

    /** 只允许精确适配 DECIMAL(18,4)，保留原值与尾随零，不舍入或强制转换量纲。 */
    private static boolean decimal184(BigDecimal value) {
        if (value == null || value.signum() < 0) return false;
        try {
            BigDecimal normalized = value.stripTrailingZeros();
            return normalized.scale() <= 4 && (long) normalized.precision() - normalized.scale() <= 14;
        } catch (ArithmeticException invalidScale) {
            return false;
        }
    }

    private static boolean text(String value, int limit) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= limit;
    }

    private static boolean identityText(String value, int limit) {
        return text(value, limit) && value.equals(value.strip());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw rejected(message);
    }

    private static ServiceException rejected(String message) {
        return new ServiceException("采购占用归属来源未核验：" + message);
    }

    /** Date 是 Mapper 原始可变值，只在复制瞬间取毫秒，不保留实体或日期别名。 */
    private static Instant instant(Date value) {
        return value == null ? null : Instant.ofEpochMilli(value.getTime());
    }

    private static MaterialSource freezeMaterial(BdMaterial row) {
        require(row != null, "真实物料不存在");
        return new MaterialSource(row.getTenantId(), row.getMaterialId(), row.getBaseUomId(), row.getStatus(), row.getDelFlag());
    }

    private static AllocationSource freezeAllocation(PuRequirementAllocationLedger row) {
        return new AllocationSource(row.getAllocationId(), row.getTenantId(), row.getMaterialId(), row.getSourceType(),
            row.getDetailId(), row.getRequisitionLineId(), row.getAllocationNo(), row.getOrderLineId(), row.getBaseUomId(),
            row.getCoveredDemandBaseQty(), row.getOccupiedBaseQty(), row.getFulfilledBaseQty(), row.getReleasedBaseQty(),
            row.getAllocationStatus(), row.getAllocationKey(), row.getDecisionIdempotencyKey(), row.getVersion(),
            instant(row.getCreateTime()), instant(row.getTransferTime()), instant(row.getReleasedAt()), row.getReleaseReason(), instant(row.getUpdateTime()));
    }

    private static RequirementDetailSource freezeRequirementDetail(RawRequirementDetail row) {
        return new RequirementDetailSource(row.getDetailId(), row.getTenantId(), row.getRequirementId(), row.getMaterialId(),
            row.getUnit(), row.getSourceType(), row.getRequiredQuantity(), row.getIssuedQuantity(), row.getStockQuantity(), row.getShortageQuantity(),
            instant(row.getRequiredDate()), row.getStatus(), row.getDelFlag(), row.getBackflushFlag(), row.getActualConsumedQty(),
            instant(row.getCreateTime()), instant(row.getUpdateTime()));
    }

    private static RequirementHeadSource freezeRequirementHead(RawRequirementHead row) {
        return new RequirementHeadSource(row.getRequirementId(), row.getTenantId(), row.getRequirementNo(), row.getParentRequirementId(),
            row.getPlanId(), row.getPlanNo(), row.getBomId(), row.getMaterialId(), instant(row.getRequirementDate()), row.getSourceType(), row.getStatus(),
            instant(row.getConfirmedTime()), instant(row.getIssuedTime()), instant(row.getClosedTime()), row.getDelFlag(),
            instant(row.getCreateTime()), instant(row.getUpdateTime()));
    }

    private static RequisitionLineSource freezeRequisitionLine(RawRequisitionLine row) {
        return new RequisitionLineSource(row.getLineId(), row.getTenantId(), row.getRequisitionId(), row.getMaterialId(), row.getUnit(),
            row.getApplyQuantity(), row.getApprovedQuantity(), row.getShortageQuantity(), instant(row.getRequiredDate()), row.getSourceType(),
            row.getSourceId(), row.getAttr1(), row.getAttr2(), row.getAttr3(), row.getAttr4(), row.getDelFlag(),
            instant(row.getCreateTime()), instant(row.getUpdateTime()));
    }

    private static RequisitionHeadSource freezeRequisitionHead(RawRequisitionHead row) {
        return new RequisitionHeadSource(row.getRequisitionId(), row.getTenantId(), row.getRequisitionNo(), row.getSourceType(), row.getStatus(),
            instant(row.getRequisitionDate()), instant(row.getRequiredDate()), row.getDecisionRunId(), row.getDecisionActionId(),
            row.getDecisionIdempotencyKey(), row.getSelectFlowId(), row.getVersion(), row.getDelFlag(),
            instant(row.getCreateTime()), instant(row.getUpdateTime()));
    }

    private static OrderLineSource freezeOrderLine(RawOrderLine row) {
        return new OrderLineSource(row.getLineId(), row.getTenantId(), row.getOrderId(), row.getRequisitionLineId(), row.getMaterialId(), row.getUnit(),
            row.getOrderQuantity(), row.getReceivedQuantity(), row.getReturnedQuantity(), row.getInvoicedQty(), row.getReceiveStatus(), row.getDelFlag(),
            instant(row.getCreateTime()), instant(row.getUpdateTime()));
    }

    private static OrderHeadSource freezeOrderHead(RawOrderHead row) {
        return new OrderHeadSource(row.getOrderId(), row.getTenantId(), row.getOrderNo(), row.getRequisitionId(), row.getVendorId(), row.getStatus(),
            instant(row.getOrderDate()), instant(row.getExpectedArrivalDate()), row.getDelFlag(), instant(row.getCreateTime()), instant(row.getUpdateTime()));
    }
}

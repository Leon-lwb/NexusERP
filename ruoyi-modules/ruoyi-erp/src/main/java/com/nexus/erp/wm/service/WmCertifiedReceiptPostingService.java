package com.nexus.erp.wm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseStockLineageVerifier;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import com.nexus.erp.wm.service.impl.WmMqSendService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 真实精确收货：支持单物料、根单位及非 SN 批次；原生余额与来源行流水处于同一 ERP 独占可重复读事务。
 * 无库存只能在真实合法入库时建立明确零起点；不作为采购来源可信零。不造虚拟库存、不猜有效期。
 * 永久过账与待通知先提交；跨库 MQ 接受单独完成，宕机通过同 receiveId 重放恢复，不声称跨库 ACID。
 */
@Service
public class WmCertifiedReceiptPostingService {
    @org.springframework.beans.factory.annotation.Autowired
    private WmSerialInventoryService serialInventory;

    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal SECOND_MILLIS = new BigDecimal("1000");
    private static final BigDecimal MAX = new BigDecimal("99999999999999.9999");
    private static final Set<String> TABLES =
            Set.of(
                    "wm_certified_receipt_posting",
                    "wm_material_stock",
                    "wm_material_stock_tx",
                    "wm_material_receive",
                    "wm_material_receive_line",
                    "wm_material_receive_detail",
                    "wm_batch",
                    "bd_material",
                    "bd_unit_of_measure",
                    "pu_purchase_order",
                    "pu_purchase_order_line",
                    "wm_warehouse",
                    "wm_storage_area",
                    "wm_storage_location");
    private static final Set<String> QUANTITIES =
            Set.of(
                    "wm_material_stock.stock_quantity",
                    "wm_material_stock.reserved_quantity",
                    "wm_material_stock_tx.tx_quantity",
                    "wm_material_stock_tx.before_quantity",
                    "wm_material_stock_tx.after_quantity",
                    "wm_material_receive_line.quantity_received",
                    "wm_material_receive_detail.quantity",
                    "wm_batch.quantity",
                    "wm_batch.received_qty",
                    "wm_batch.issued_qty",
                    "bd_unit_of_measure.conversion_rate",
                    "pu_purchase_order_line.order_quantity",
                    "pu_purchase_order_line.received_quantity",
                    "pu_purchase_order_line.returned_quantity",
                    "pu_purchase_order_line.unit_price",
                    "wm_certified_receipt_posting.total_quantity",
                    "wm_material_stock.unit_cost",
                    "wm_material_stock.total_cost",
                    "wm_material_stock_tx.unit_cost",
                    "wm_material_stock_tx.tx_amount",
                    "pu_purchase_order_line.unit_price_excluding_tax",
                    "pu_purchase_order.exchange_rate");
    private final WmCertifiedReceiptPostingAuthorizer auth;
    private final PuRequirementErpTransactionExecutor tx;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final WmCertifiedReceiptPostingMapper mapper;
    private final ErpPurchaseStockLineageVerifier lineage;
    private final WmReceiptIqcEvidenceService iqc;
    private final WmMqSendService notifications;
    private final PuCertifiedPurchaseOrderWorkflowService orderWorkflow;

    @org.springframework.beans.factory.annotation.Autowired
    private WmCertifiedArrivalNoticeService arrivals;

    public WmCertifiedReceiptPostingService(
            WmCertifiedReceiptPostingAuthorizer auth,
            PuRequirementErpTransactionExecutor tx,
            PuRequirementErpTransactionQualification qualification,
            PuRequirementMaterialLockService locks,
            WmCertifiedReceiptPostingMapper mapper,
            ErpPurchaseStockLineageVerifier lineage,
            WmReceiptIqcEvidenceService iqc,
            WmMqSendService notifications,
            PuCertifiedPurchaseOrderWorkflowService orderWorkflow) {
        this.auth = Objects.requireNonNull(auth);
        this.tx = Objects.requireNonNull(tx);
        this.qualification = Objects.requireNonNull(qualification);
        this.locks = Objects.requireNonNull(locks);
        this.mapper = Objects.requireNonNull(mapper);
        this.lineage = Objects.requireNonNull(lineage);
        this.iqc = Objects.requireNonNull(iqc);
        this.notifications = Objects.requireNonNull(notifications);
        this.orderWorkflow = Objects.requireNonNull(orderWorkflow);
    }

    /**
     * 本次业务执行的稳定结果，重放时返回原已确认事实。
     *
     * @param receiveId 原收货单主键。
     * @param materialId 当前业务行实际物料标识。
     * @param quantity 实际业务数量，不能以物料清单估算替代实际身份或用量。
     * @param sourceFingerprint 原来源业务意图摘要。
     * @param replay 当前结果是否来自原命令重放。
     * @param notificationPending 本地业务已提交但跨模块确认尚未完成。
     */
    public record Result(
            Long receiveId,
            Long materialId,
            BigDecimal quantity,
            String sourceFingerprint,
            boolean replay,
            boolean notificationPending) {}

    /**
     * 旧业务 API 只做路由，最终写入仍在新的独占可重复读事务中重读全部来源；此处不返回写入授权。
     */
    public boolean handles(Long receiveId) {
        return handlesFor(auth.requireUser(), receiveId);
    }

    /**
     * 上架路由只要求上架权限，不能额外要求库存执行权限。
     */
    public boolean handlesShelf(Long receiveId) {
        return handlesFor(auth.requireShelf(), receiveId);
    }

    private boolean handlesFor(DecisionActor actor, Long receiveId) {
        need(actor != null && positive(receiveId), "路由身份无效");
        String tenant = actor.tenantId();
        need(
                TenantHelper.isEnable()
                        && tenant != null
                        && tenant.equals(TenantHelper.getTenantId()),
                "路由租户不符");
        return tx.executeRepeatableRead(
                tenant,
                () -> {
                    qualification.requireSourceChainTransaction();
                    Head head = read(() -> mapper.lockHead(tenant, receiveId));
                    need(
                            head != null
                                    && tenant.equals(head.tenantId())
                                    && receiveId.equals(head.receiveId())
                                    && "0".equals(head.delFlag()),
                            "路由原收货未知");
                    if (read(() -> mapper.lockPosting(tenant, receiveId)) != null) {
                        return Boolean.TRUE;
                    }
                    if (head.orderId() == null) {
                        return Boolean.FALSE;
                    }
                    var ids =
                            read(() -> mapper.lockCertifiedOrderIdentities(tenant, head.orderId()));
                    need(ids != null, "路由原采购来源未知");
                    return !ids.isEmpty();
                });
    }

    /**
     * 身份每次重放重新核验；请求没有 qty、verified、库存或有效期字段。
     */
    public Result post(Long receiveId) {
        DecisionActor actor = auth.requireUser();
        need(actor != null && positive(receiveId), "入库身份无效");
        String tenant = actor.tenantId();
        need(
                TenantHelper.isEnable()
                        && tenant != null
                        && tenant.equals(TenantHelper.getTenantId())
                        && positive(actor.userId()),
                "租户无效");
        Result result =
                tx.executeRepeatableRead(
                        tenant, () -> postLocked(tenant, actor.userId(), receiveId, true));
        if (!result.notificationPending()) {
            return result;
        }
        try {
            // 真实已提交；此时没有 ERP 事务/资源，主库消息记录不参与过账事务。
            notifications.sendCertifiedPurchaseReceiveWriteback(receiveId, tenant);
            tx.executeRepeatableRead(
                    tenant,
                    () -> {
                        qualification.requireSourceChainTransaction();
                        requireSchema();
                        Posting saved = read(() -> mapper.lockPosting(tenant, receiveId));
                        need(
                                saved != null
                                        && result.sourceFingerprint()
                                                .equals(saved.sourceFingerprint()),
                                "通知恢复原结果不符");
                        if ("PENDING".equals(saved.notificationStatus())) {
                            one(mapper.acknowledgeEnqueue(tenant, receiveId));
                        } else {
                            need("ENQUEUED".equals(saved.notificationStatus()), "通知状态未知");
                        }
                        return Boolean.TRUE;
                    });
            return new Result(
                    result.receiveId(),
                    result.materialId(),
                    result.quantity(),
                    result.sourceFingerprint(),
                    result.replay(),
                    false);
        } catch (RuntimeException failure) {
            // 提交成功但主库/消息接受失败：不回滚库存、不重复入库，保留永久 PENDING 并明确返回。
            return result;
        }
    }

    /**
     * 永久物理结果读回，不重新入库、上架、确认通知或创建缺失来源守卫。
     *
     * @param orderId 原业务订单主键。
     * @param result 本次业务执行的稳定结果。
     * @param postedAt 实际过账提交时间。
     * @param notificationStatus 跨模块确认进度，不能与本地提交状态混同。
     */
    public record PostedSnapshot(
            Long orderId, Result result, String postedAt, String notificationStatus) {}

    public PostedSnapshot readPostedLocked(String tenant, Long receiveId) {
        qualification.requireSourceChainTransaction();
        need(
                TenantHelper.isEnable()
                        && tenant != null
                        && tenant.equals(TenantHelper.getTenantId())
                        && positive(receiveId),
                "恢复读回租户/原单据无效");
        requireSchema();
        List<Long> materials = read(() -> mapper.discoverMaterials(tenant, receiveId));
        need(materials != null && materials.size() == 1 && positive(materials.get(0)), "恢复物料范围未知");
        Long material = materials.get(0);
        Map<Long, Integer> guards = read(() -> locks.lockExisting(tenant, List.of(material)));
        need(
                guards != null
                        && guards.size() == 1
                        && guards.get(material) != null
                        && guards.get(material) >= 0
                        && guards.get(material) < Integer.MAX_VALUE,
                "恢复既有物料guard未知");
        // 与既有Facade保持头→posting锁序，避免不持物料guard的路由读与恢复读形成反向等待。
        Head firstHead = read(() -> mapper.lockHead(tenant, receiveId));
        need(
                firstHead != null
                        && tenant.equals(firstHead.tenantId())
                        && receiveId.equals(firstHead.receiveId())
                        && positive(firstHead.orderId())
                        && "FINISHED".equals(firstHead.status()),
                "缺少原已完物理单据");
        Posting original = read(() -> mapper.lockPosting(tenant, receiveId));
        need(
                original != null
                        && tenant.equals(original.tenantId())
                        && receiveId.equals(original.receiveId())
                        && material.equals(original.materialId())
                        && positive(original.actorId()),
                "缺少真正永久过账，查询不补建");
        // 在已锁定posting的前提下只允许现有完整replay分支；绝不调用公开post及其提交后MQ。
        Result checked = postLocked(tenant, original.actorId(), receiveId, false);
        need(
                checked.replay()
                        && original.sourceFingerprint().equals(checked.sourceFingerprint()),
                "恢复不是原已提交结果");
        Head head = read(() -> mapper.lockHead(tenant, receiveId));
        need(
                head != null
                        && tenant.equals(head.tenantId())
                        && receiveId.equals(head.receiveId())
                        && firstHead.equals(head)
                        && positive(head.orderId())
                        && "FINISHED".equals(head.status()),
                "恢复原父单据漂移");
        qualification.requireSourceChainTransaction();
        return new PostedSnapshot(
                head.orderId(), checked, original.postedAt(), original.notificationStatus());
    }

    /**
     * 内部整批收货只读边界；调用方已持有物料守卫。不能用 MQ 消息载荷生成或补写过账及通知。
     */
    public void validatePostedLocked(String tenant, Long receiveId) {
        qualification.requireSourceChainTransaction();
        need(
                TenantHelper.isEnable()
                        && tenant != null
                        && tenant.equals(TenantHelper.getTenantId())
                        && positive(receiveId),
                "内部过账认证租户/原单据无效");
        requireSchema();
        Posting original = read(() -> mapper.lockPosting(tenant, receiveId));
        need(
                original != null
                        && tenant.equals(original.tenantId())
                        && receiveId.equals(original.receiveId())
                        && positive(original.actorId()),
                "不存在真正永久过账，不能从消息补造");
        Result checked = postLocked(tenant, original.actorId(), receiveId, false);
        need(
                checked.replay()
                        && original.sourceFingerprint().equals(checked.sourceFingerprint()),
                "内部过账认证不是原已提交结果");
    }

    /**
     * 上架和物理过账复用全部原生来源预检，但上架仅写状态/真实操作时间，不写库存或通知。
     */
    public boolean shelf(Long receiveId) {
        DecisionActor actor = auth.requireShelf();
        need(positive(receiveId), "原收货身份缺失");
        tx.executeRepeatableRead(
                actor.tenantId(),
                () -> postLocked(actor.tenantId(), actor.userId(), receiveId, true, true));
        return true;
    }

    private Result postLocked(
            String tenant, Long actor, Long receiveId, boolean acquireMaterialGuard) {
        return postLocked(tenant, actor, receiveId, acquireMaterialGuard, false);
    }

    private Result postLocked(
            String tenant,
            Long actor,
            Long receiveId,
            boolean acquireMaterialGuard,
            boolean shelving) {
        qualification.requireSourceChainTransaction();
        requireSchema();
        List<Long> discovered = read(() -> mapper.discoverMaterials(tenant, receiveId));
        need(
                discovered != null && discovered.size() == 1 && positive(discovered.get(0)),
                "只支持完整单物料原始收货");
        Long materialId = discovered.get(0);
        if (acquireMaterialGuard) {
            locks.lock(tenant, List.of(materialId));
        }
        Head head = read(() -> mapper.lockHead(tenant, receiveId));
        Posting prior = read(() -> mapper.lockPosting(tenant, receiveId));
        boolean replay = prior != null;
        need(
                head != null
                        && tenant.equals(head.tenantId())
                        && receiveId.equals(head.receiveId())
                        && text(head.receiveCode())
                        && "0".equals(head.delFlag())
                        && positive(head.orderId())
                        && positive(head.vendorId())
                        && (replay
                                ? "FINISHED".equals(head.status())
                                : shelving
                                        ? Set.of("APPROVED", "SHELVED").contains(head.status())
                                        : "SHELVED".equals(head.status())),
                "原始收货身份或状态不符");
        Clock clock = read(mapper::clock);
        need(
                clock != null
                        && text(clock.databaseAt())
                        && clock.epochMillis() != null
                        && "+08:00".equals(clock.timeZone()),
                "数据库时钟来源不符");
        BigDecimal originalAt = replay ? postedAtMillis(prior.postedAt()) : clock.epochMillis();
        need(originalAt.compareTo(clock.epochMillis()) <= 0, "永久过账时点在未来");
        BigDecimal shelfAt =
                shelving && "APPROVED".equals(head.status()) ? clock.epochMillis() : head.shelfAt();
        need(
                head.receiveAt() != null
                        && shelfAt != null
                        && head.receiveAt().compareTo(shelfAt) <= 0
                        && shelfAt.compareTo(originalAt) <= 0,
                "真实收货/上架时点缺失或未来");
        need(
                positive(head.warehouseId())
                        && positive(head.areaId())
                        && nullablePositive(head.locationId()),
                "原始仓储身份缺失");
        // 业务收货日期可能只表示当天；单位必须在原不可变草稿创建前已经认证，不能借当前时间追认。
        BigDecimal draftCreatedAt = read(() -> mapper.readDraftCreatedAt(tenant, receiveId));
        // 既有数据库时钟及永久posting只表达秒，原命令保留毫秒；同秒不是可证明的未来。
        // 重放仍以原posting代表的秒为边界，不能扩大到当前时钟，也不改变下方单位认证的精确时序。
        BigDecimal originalSecondEnd =
                originalAt
                        .divideToIntegralValue(SECOND_MILLIS)
                        .add(BigDecimal.ONE)
                        .multiply(SECOND_MILLIS);
        need(
                draftCreatedAt != null
                        && draftCreatedAt.signum() > 0
                        && draftCreatedAt.compareTo(originalSecondEnd) < 0,
                "原永久草稿创建时点缺失或晚于原过账所代表秒");
        Material material = read(() -> mapper.lockMaterial(tenant, materialId));
        need(
                material != null
                        && tenant.equals(material.tenantId())
                        && materialId.equals(material.materialId())
                        && Long.valueOf(1).equals(material.batchManaged())
                        && Long.valueOf(1).equals(material.status())
                        && "0".equals(material.delFlag())
                        && tenant.equals(material.unitTenant())
                        && positive(material.baseUomId())
                        && Long.valueOf(1).equals(material.isBase())
                        && (material.primaryId() == null
                                || material.baseUomId().equals(material.primaryId()))
                        && BigDecimal.ONE.equals(
                                material.conversionRate() == null
                                        ? null
                                        : material.conversionRate().stripTrailingZeros())
                        && Long.valueOf(1).equals(material.unitStatus())
                        && "0".equals(material.unitDelFlag())
                        && text(material.conversionVersion())
                        && material.verifiedAt() != null
                        && material.verifiedAt().compareTo(draftCreatedAt) <= 0
                        && material.quantityScale() != null
                        && material.quantityScale() >= 0
                        && material.quantityScale() <= 4,
                "根单位/批次物料来源不完整");
        boolean serialV2 =
                serialInventory != null
                        && (replay
                                ? serialInventory.hasV2Receipt(receiveId)
                                : serialInventory.isStrictSerial(materialId));
        String unit = material.baseUomId().toString();
        int scale = material.quantityScale();
        List<Line> lines = read(() -> mapper.lockLines(tenant, receiveId));
        List<Detail> details = read(() -> mapper.lockDetails(tenant, receiveId));
        need(
                lines != null && !lines.isEmpty() && details != null && !details.isEmpty(),
                "原始入库行及分配明细不完整");
        Order order = read(() -> mapper.lockOrder(tenant, head.orderId()));
        need(
                order != null
                        && tenant.equals(order.tenantId())
                        && head.orderId().equals(order.orderId())
                        && head.vendorId().equals(order.vendorId())
                        && "CNY".equals(order.currency())
                        && order.exchangeRate() != null
                        && BigDecimal.ONE.compareTo(order.exchangeRate()) == 0
                        && "0".equals(order.delFlag())
                        && (replay
                                        ? Set.of(
                                                "APPROVED",
                                                "PARTIALLY_RECEIVED",
                                                "RECEIVED",
                                                "CLOSED",
                                                "CANCELLED")
                                        : Set.of("APPROVED", "PARTIALLY_RECEIVED", "RECEIVED"))
                                .contains(order.status()),
                "采购未批准或父来源未知");
        if (replay) {
            orderWorkflow.requireApprovedReplayLocked(tenant, head.orderId());
        } else {
            orderWorkflow.requireApprovedLocked(tenant, head.orderId());
        }
        Map<Long, Line> byLine = new TreeMap<>();
        Map<Long, BigDecimal> demandByOrder = new TreeMap<>();
        BigDecimal total = ZERO;
        for (Line line : lines) {
            need(
                    line != null
                            && positive(line.lineId())
                            && byLine.put(line.lineId(), line) == null
                            && tenant.equals(line.tenantId())
                            && receiveId.equals(line.receiveId())
                            && materialId.equals(line.materialId())
                            && unit.equals(line.baseUomId())
                            && positive(line.purchaseLineId())
                            && "0".equals(line.delFlag()),
                    "入库行身份/单位/采购关联未知");
            BigDecimal qty = quantity(line.quantity(), scale, true);
            total = quantity(total.add(qty), scale, true);
            demandByOrder.merge(line.purchaseLineId(), qty, BigDecimal::add);
        }
        Map<Long, BigDecimal> costsByOrderLine = new TreeMap<>(),
                costsByReceiveLine = new TreeMap<>();
        StringBuilder source =
                new StringBuilder("precise-receipt-v1\n")
                        .append(tenant)
                        .append('|')
                        .append(receiveId)
                        .append('|')
                        .append(head.receiveCode())
                        .append('|')
                        .append(head.orderId())
                        .append('|')
                        .append(head.vendorId())
                        .append('|')
                        .append(head.warehouseId())
                        .append('|')
                        .append(head.areaId())
                        .append('|')
                        .append(head.locationId())
                        .append('|')
                        .append(head.receiveAt())
                        .append('|')
                        .append(head.shelfAt())
                        .append('|')
                        .append(material)
                        .append('\n');
        for (Map.Entry<Long, BigDecimal> entry : demandByOrder.entrySet()) {
            OrderLine po = read(() -> mapper.lockOrderLine(tenant, entry.getKey()));
            need(
                    po != null
                            && tenant.equals(po.tenantId())
                            && entry.getKey().equals(po.lineId())
                            && head.orderId().equals(po.orderId())
                            && materialId.equals(po.materialId())
                            && unit.equals(po.unit())
                            && "0".equals(po.delFlag()),
                    "采购行归属或单位不符");
            BigDecimal ordered = quantity(po.quantity(), scale, true);
            quantity(po.received(), scale, false);
            quantity(po.returned(), scale, false);
            need(po.unitPrice() != null && po.unitPrice().signum() > 0, "采购价格缺真实来源");
            BigDecimal cost = money(po.netUnitPrice(), 6, true);
            costsByOrderLine.put(po.lineId(), cost);
            List<BigDecimal> posted =
                    read(() -> mapper.lockPostedQuantities(tenant, entry.getKey()));
            need(posted != null, "已收货来源全集缺失");
            BigDecimal gross = ZERO;
            for (BigDecimal amount : posted) {
                gross = quantity(gross.add(quantity(amount, scale, true)), scale, false);
            }
            need(
                    (replay ? gross : gross.add(entry.getValue())).compareTo(ordered) <= 0,
                    "真实已入库及本次合计超过订单量");
            source.append(po.lineId())
                    .append('|')
                    .append(po.orderId())
                    .append('|')
                    .append(po.materialId())
                    .append('|')
                    .append(po.unit())
                    .append('|')
                    .append(ordered.toPlainString())
                    .append('|')
                    .append(po.unitPrice().stripTrailingZeros().toPlainString())
                    .append('|')
                    .append(cost.toPlainString())
                    .append('\n');
        }
        for (Line line : byLine.values()) {
            costsByReceiveLine.put(line.lineId(), costsByOrderLine.get(line.purchaseLineId()));
        }
        Map<Long, BigDecimal> byLineQuantity = new TreeMap<>(), byBatchQuantity = new TreeMap<>();
        Set<Long> detailIds = new HashSet<>();
        Map<Long, Batch> batches = new TreeMap<>();
        for (Detail detail : details) {
            need(
                    detail != null
                            && positive(detail.detailId())
                            && detailIds.add(detail.detailId())
                            && tenant.equals(detail.tenantId())
                            && receiveId.equals(detail.receiveId())
                            && byLine.containsKey(detail.lineId())
                            && materialId.equals(detail.materialId())
                            && unit.equals(detail.baseUomId())
                            && positive(detail.batchId())
                            && (detail.snId() == null
                                    || serialV2 && BigDecimal.ONE.compareTo(detail.quantity()) == 0)
                            && "0".equals(detail.delFlag())
                            && head.warehouseId().equals(detail.warehouseId())
                            && positive(detail.areaId())
                            && nullablePositive(detail.locationId()),
                    "批次分配身份不完整或SN未支持");
            quantity(detail.quantity(), scale, true);
            money(detail.quantity().multiply(costsByReceiveLine.get(detail.lineId())), 4, true);
            byLineQuantity.merge(detail.lineId(), detail.quantity(), BigDecimal::add);
            byBatchQuantity.merge(detail.batchId(), detail.quantity(), BigDecimal::add);
            if (!replay) {
                storage(tenant, detail.warehouseId(), detail.areaId(), detail.locationId());
            }
            source.append(detail).append('\n');
        }
        if (!replay) {
            storage(tenant, head.warehouseId(), head.areaId(), head.locationId());
        }
        for (Line line : byLine.values()) {
            need(
                    byLineQuantity.containsKey(line.lineId())
                            && line.quantity().compareTo(byLineQuantity.get(line.lineId())) == 0,
                    "行数量与全部实际分配不守恒");
            source.append(line).append('\n');
        }
        for (Map.Entry<Long, BigDecimal> entry : byBatchQuantity.entrySet()) {
            Batch batch = read(() -> mapper.lockBatch(tenant, entry.getKey()));
            need(
                    batch != null
                            && tenant.equals(batch.tenantId())
                            && entry.getKey().equals(batch.batchId())
                            && materialId.equals(batch.materialId())
                            && head.vendorId().equals(batch.vendorId())
                            && head.warehouseId().equals(batch.warehouseId())
                            && text(batch.batchCode())
                            && "ACTIVE".equals(batch.status())
                            && "WM_MATERIAL_RECEIVE".equals(batch.sourceType())
                            && receiveId.equals(batch.sourceId())
                            && "0".equals(batch.delFlag())
                            && batch.expiryAt() != null
                            && batch.expiryAt().compareTo(originalAt) > 0
                            && quantity(batch.quantity(), scale, true).compareTo(entry.getValue())
                                    == 0
                            && (replay
                                    ? quantity(batch.issued(), scale, false)
                                                    .compareTo(
                                                            quantity(
                                                                    batch.received(), scale, false))
                                            <= 0
                                    : quantity(batch.issued(), scale, false).signum() == 0)
                            && (serialV2 && replay
                                    ? quantity(batch.received(), scale, false)
                                                    .compareTo(entry.getValue())
                                            >= 0
                                    : quantity(batch.received(), scale, false)
                                                    .compareTo(replay ? entry.getValue() : ZERO)
                                            == 0),
                    "真实批次来源/有效期/收货余额不能证明");
            batches.put(batch.batchId(), batch);
            source.append(batch.batchId())
                    .append('|')
                    .append(batch.batchCode())
                    .append('|')
                    .append(batch.sourceId())
                    .append('|')
                    .append(batch.expiryAt())
                    .append('|')
                    .append(batch.quantity().toPlainString())
                    .append('\n');
        }
        iqc.requireLocked(tenant, receiveId, materialId, material.baseUomId(), total);
        if (head.noticeId() != null) {
            need(arrivals != null, "缺少认证通知累计接线");
            source.append(arrivals.receiptFingerprintLocked(tenant, head, lines, replay));
        } else {
            need(lines.stream().allMatch(v -> v.noticeLineId() == null), "通知头/行引用不成对");
        }
        String fingerprint = sha256(source.toString());
        List<Stock> stocks = read(() -> mapper.lockStocks(tenant, materialId));
        List<Long> txIds = read(() -> mapper.lockTransactionIds(tenant, materialId));
        need(stocks != null && txIds != null, "原库存及流水范围不完整");
        if (replay) {
            need(
                    tenant.equals(prior.tenantId())
                            && receiveId.equals(prior.receiveId())
                            && materialId.equals(prior.materialId())
                            && positive(prior.actorId())
                            && fingerprint.equals(prior.sourceFingerprint())
                            && total.compareTo(prior.totalQuantity()) == 0
                            && text(prior.postedAt())
                            && Set.of("PENDING", "ENQUEUED").contains(prior.notificationStatus()),
                    "永久过账与当前原单据不符");
            // 历史通知恢复不是新供给授权：不受当前余额、预留、效期/冻结变化阻断，也不再写库存。
            // 专属批准恢复门禁 + 冻结源摘要 + 原物理RX全集/零起点/成本/时点均必须仍可证明。
            if (serialV2) {
                serialInventory.verifyCertifiedReceive(receiveId, costsByReceiveLine);
            } else {
                verifyPosted(
                        tenant,
                        receiveId,
                        details,
                        unit,
                        scale,
                        costsByReceiveLine,
                        prior.postedAt());
            }
            return new Result(
                    receiveId,
                    materialId,
                    total,
                    fingerprint,
                    true,
                    "PENDING".equals(prior.notificationStatus()));
        }
        for (Stock old : stocks) {
            need(
                    old != null && !byBatchQuantity.containsKey(old.batchId()),
                    "本次原始批次已有库存，不能覆盖旧来源或重新授予零起点");
        }
        if (stocks.isEmpty()) {
            need(txIds.isEmpty(), "孤立历史流水不能作为真实零起点");
        } else if (serialV2) {
            serialInventory.verifyMaterialPools(materialId);
        } else {
            verifyLineage(tenant, materialId);
        }
        if (shelving) {
            // 出生范围不变：这里只改变上架状态/操作者/实际时点，不走旧头或行的任意更新。
            if ("APPROVED".equals(head.status())) {
                one(mapper.markShelved(tenant, receiveId, clock.databaseAt(), actor));
            }
            qualification.requireSourceChainTransaction();
            return new Result(
                    receiveId,
                    materialId,
                    total,
                    fingerprint,
                    "SHELVED".equals(head.status()),
                    false);
        }
        // 同一来源门禁全部通过后，v2使用冻结SN证明；旧证明的分支及校验保持原样。
        if (serialV2) {
            serialInventory.postCertifiedReceive(receiveId, costsByReceiveLine);
            String at = clock.databaseAt();
            one(mapper.finish(tenant, receiveId, at, actor));
            serialInventory.verifyCertifiedReceive(receiveId, costsByReceiveLine);
            serialInventory.verifyMaterialPools(materialId);
            one(mapper.recordPosting(tenant, receiveId, materialId, actor, fingerprint, total, at));
            if (head.noticeId() != null) {
                for (Line line : lines) {
                    arrivals.receiveLocked(
                            tenant, head.noticeId(), line.noticeLineId(), line.quantity());
                }
            }
            return new Result(receiveId, materialId, total, fingerprint, false, true);
        }
        // 完整来源全部核验后才开始过账；相同批次不能覆盖旧库存维度/历史。
        Map<String, Stock> dimensions = new HashMap<>();
        for (Stock stock : stocks) {
            need(
                    stock != null
                            && dimensions.put(
                                            dimension(
                                                    stock.warehouseId(),
                                                    stock.areaId(),
                                                    stock.locationId(),
                                                    stock.batchId()),
                                            stock)
                                    == null,
                    "库存维度重复");
        }
        String at = clock.databaseAt();
        for (Detail detail : details) {
            String key =
                    dimension(
                            detail.warehouseId(),
                            detail.areaId(),
                            detail.locationId(),
                            detail.batchId());
            Stock stock = dimensions.get(key);
            BigDecimal before = stock == null ? ZERO : quantity(stock.quantity(), scale, false);
            BigDecimal after = quantity(before.add(detail.quantity()), scale, true);
            Long stockId = stock == null ? IdWorker.getId() : stock.stockId();
            Batch batch = batches.get(detail.batchId());
            BigDecimal cost = costsByReceiveLine.get(detail.lineId()),
                    amount = money(detail.quantity().multiply(cost), 4, true);
            BigDecimal totalCost =
                    stock == null ? amount : money(stock.totalCost().add(amount), 4, true);
            if (stock == null) {
                one(
                        mapper.insertStock(
                                tenant,
                                stockId,
                                unit,
                                detail,
                                batch,
                                detail.quantity(),
                                at,
                                actor,
                                cost,
                                amount));
            } else {
                need(
                        tenant.equals(stock.tenantId())
                                && materialId.equals(stock.materialId())
                                && unit.equals(stock.baseUomId())
                                && stock.snId() == null
                                && "0".equals(stock.delFlag())
                                && stock.version() != null
                                && stock.version() >= 0
                                && stock.unitCost() != null
                                && cost.compareTo(stock.unitCost()) == 0
                                && stock.totalCost() != null
                                && money(stock.quantity().multiply(cost), 4, false)
                                                .compareTo(stock.totalCost())
                                        == 0
                                && batch.expiryAt().compareTo(stock.expiryAt()) == 0,
                        "库存归属/成本或批次有效期漂移");
                one(mapper.addStock(tenant, stock, after, at, actor, totalCost));
            }
            one(
                    mapper.insertTransaction(
                            tenant,
                            IdWorker.getId(),
                            stockId,
                            unit,
                            head,
                            detail,
                            before,
                            after,
                            at,
                            actor,
                            cost,
                            amount));
            dimensions.put(
                    key,
                    new Stock(
                            tenant,
                            stockId,
                            materialId,
                            unit,
                            detail.warehouseId(),
                            detail.areaId(),
                            detail.locationId(),
                            detail.batchId(),
                            null,
                            after,
                            stock == null ? ZERO : stock.reserved(),
                            stock == null ? 0 : stock.version() + 1,
                            "0",
                            batch.expiryAt(),
                            cost,
                            totalCost));
        }
        for (var entry : byBatchQuantity.entrySet()) {
            one(
                    mapper.receiveBatch(
                            tenant,
                            receiveId,
                            batches.get(entry.getKey()),
                            entry.getValue(),
                            at,
                            actor));
        }
        one(mapper.finish(tenant, receiveId, at, actor));
        verifyLineage(tenant, materialId); // 写后独立完整来源复算；失败整个 ERP 事务回滚。
        one(mapper.recordPosting(tenant, receiveId, materialId, actor, fingerprint, total, at));
        // 重放已在上方返回；首次实际过账的库存/批次/通知与永久结果同事务。
        if (head.noticeId() != null) {
            for (Line line : lines) {
                arrivals.receiveLocked(
                        tenant, head.noticeId(), line.noticeLineId(), line.quantity());
            }
        }
        return new Result(receiveId, materialId, total, fingerprint, false, true);
    }

    private void verifyLineage(String tenant, Long materialId) {
        var result = lineage.verifyLocked(tenant, materialId);
        need(
                result != null
                        && result.inventory() != null
                        && tenant.equals(result.inventory().tenantId())
                        && materialId.equals(result.inventory().materialId()),
                "独立库存全链认证未返回同范围结果");
    }

    private void verifyPosted(
            String tenant,
            Long receiveId,
            List<Detail> details,
            String unit,
            int scale,
            Map<Long, BigDecimal> costs,
            String originalAt) {
        Map<String, BigDecimal> expected = new TreeMap<>(), actual = new TreeMap<>();
        for (Detail d : details) {
            expected.merge(
                    d.lineId()
                            + "|"
                            + dimension(d.warehouseId(), d.areaId(), d.locationId(), d.batchId()),
                    d.quantity(),
                    BigDecimal::add);
        }
        List<PostedTx> posted = read(() -> mapper.lockReceiptTransactions(tenant, receiveId));
        List<Long> allIds = read(() -> mapper.lockAllReceiptTransactionIds(tenant, receiveId));
        need(posted != null && !posted.isEmpty() && allIds != null, "永久过账无完整物理流水");
        Set<Long> ids = new HashSet<>();
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (PostedTx row : posted) {
            need(
                    row != null
                            && positive(row.txId())
                            && positive(row.stockId())
                            && ids.add(row.txId())
                            && unit.equals(row.baseUomId())
                            && positive(row.lineId())
                            && positive(row.warehouseId())
                            && positive(row.areaId())
                            && nullablePositive(row.locationId())
                            && positive(row.batchId())
                            && originalAt.equals(row.postedAt()),
                    "恢复物理流水身份/原始时点不符");
            BigDecimal qty = quantity(row.quantity(), scale, true),
                    before = quantity(row.beforeQuantity(), scale, false),
                    after = quantity(row.afterQuantity(), scale, false);
            need(
                    before.compareTo(balances.getOrDefault(row.stockId(), ZERO)) == 0
                            && before.add(qty).compareTo(after) == 0,
                    "恢复物理流水零起点或逐笔守恒不符");
            balances.put(row.stockId(), after);
            BigDecimal cost = costs.get(row.lineId());
            need(
                    cost != null
                            && row.unitCost() != null
                            && cost.compareTo(row.unitCost()) == 0
                            && money(qty.multiply(cost), 4, true)
                                            .compareTo(money(row.txAmount(), 4, true))
                                    == 0,
                    "恢复实际成本流水与原供应报价不符");
            actual.merge(
                    row.lineId()
                            + "|"
                            + dimension(
                                    row.warehouseId(),
                                    row.areaId(),
                                    row.locationId(),
                                    row.batchId()),
                    qty,
                    BigDecimal::add);
        }
        need(
                allIds.size() == ids.size() && new HashSet<>(allIds).equals(ids),
                "恢复原单据存在额外/删除/异类型流水");
        need(expected.keySet().equals(actual.keySet()), "恢复分配全集不符");
        for (var entry : expected.entrySet()) {
            need(entry.getValue().compareTo(actual.get(entry.getKey())) == 0, "恢复分配数量不符");
        }
    }

    private static BigDecimal postedAtMillis(String at) {
        need(text(at), "永久过账时点缺失");
        try {
            return BigDecimal.valueOf(
                    java.time.LocalDateTime.parse(
                                    at,
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT))
                            .toInstant(java.time.ZoneOffset.ofHours(8))
                            .toEpochMilli());
        } catch (java.time.DateTimeException failure) {
            throw error("永久过账时点不符合原数据库时区合同");
        }
    }

    private void storage(String tenant, Long warehouse, Long area, Long location) {
        Storage s = read(() -> mapper.lockStorage(tenant, warehouse, area, location));
        need(
                s != null
                        && tenant.equals(s.warehouseTenant())
                        && warehouse.equals(s.warehouseId())
                        && Long.valueOf(1).equals(s.warehouseStatus())
                        && "0".equals(s.warehouseDelFlag())
                        && tenant.equals(s.areaTenant())
                        && area.equals(s.areaId())
                        && warehouse.equals(s.areaWarehouseId())
                        && Long.valueOf(1).equals(s.areaEnabled())
                        && Long.valueOf(0).equals(s.areaFrozen())
                        && "0".equals(s.areaDelFlag())
                        && (Long.valueOf(0).equals(s.locationFlag())
                                || Long.valueOf(1).equals(s.locationFlag()) && location != null),
                "仓库/库区完整父链或冻结未知");
        if (location != null) {
            need(
                    tenant.equals(s.locationTenant())
                            && location.equals(s.locationId())
                            && warehouse.equals(s.locationWarehouseId())
                            && area.equals(s.locationAreaId())
                            && Long.valueOf(1).equals(s.locationEnabled())
                            && Long.valueOf(0).equals(s.locationFrozen())
                            && "0".equals(s.locationDelFlag()),
                    "储位完整父链或冻结不符");
        }
    }

    private void requireSchema() {
        List<Long> pins = read(mapper::pinDefinitions);
        need(pins != null && pins.isEmpty(), "实际表定义不能固定");
        List<TableDefinition> rows = read(mapper::tableDefinitions);
        Set<String> seen = new HashSet<>();
        need(rows != null, "表引擎缺失");
        for (TableDefinition row : rows) {
            need(
                    row != null
                            && TABLES.contains(row.tableName())
                            && seen.add(row.tableName())
                            && "INNODB".equalsIgnoreCase(row.engine()),
                    "来源表非完整InnoDB");
        }
        need(seen.equals(TABLES), "来源表全集缺失");
        seen = new HashSet<>();
        List<QuantityDefinition> quantities = read(mapper::quantityDefinitions);
        need(quantities != null, "原生数量精度缺失");
        for (QuantityDefinition row : quantities) {
            need(row != null, "数量列未知");
            String key = row.tableName() + "." + row.columnName();
            need(
                    QUANTITIES.contains(key)
                            && seen.add(key)
                            && "decimal".equalsIgnoreCase(row.dataType())
                            && row.numericPrecision() != null
                            && row.numericPrecision() >= 14
                            && row.numericPrecision() <= 24
                            && row.numericScale() != null
                            && row.numericScale() >= 4
                            && row.numericScale() <= 8,
                    "原数量非原生精确DECIMAL");
            if (!Set.of(
                            "wm_batch.quantity",
                            "wm_batch.received_qty",
                            "wm_batch.issued_qty",
                            "bd_unit_of_measure.conversion_rate",
                            "pu_purchase_order_line.unit_price",
                            "pu_purchase_order_line.unit_price_excluding_tax",
                            "pu_purchase_order.exchange_rate",
                            "wm_material_stock.unit_cost",
                            "wm_material_stock.total_cost",
                            "wm_material_stock_tx.unit_cost",
                            "wm_material_stock_tx.tx_amount")
                    .contains(key)) {
                need(
                        row.numericPrecision() == 18 && row.numericScale() == 4,
                        "原生业务数量必须DECIMAL18,4");
            }
        }
        need(seen.equals(QUANTITIES), "原生数量列全集缺失");
        List<KeyDefinition> keys = read(mapper::uniqueDefinitions);
        need(keys != null, "永久键缺失");
        Map<String, TreeMap<Integer, String>> groups = new HashMap<>();
        for (KeyDefinition key : keys) {
            need(
                    key != null
                            && text(key.indexName())
                            && key.sequenceNumber() != null
                            && key.sequenceNumber() > 0
                            && key.prefixLength() == null,
                    "永久键未知/前缀");
            need(
                    groups.computeIfAbsent(key.indexName(), k -> new TreeMap<>())
                                    .put(key.sequenceNumber(), key.columnName())
                            == null,
                    "永久键重复");
        }
        need(
                groups.values().stream()
                        .anyMatch(
                                g ->
                                        g.size() == 2
                                                && "tenant_id".equals(g.get(1))
                                                && "receive_id".equals(g.get(2))),
                "永久租户入库唯一键缺失");
    }

    private static String dimension(Long w, Long a, Long l, Long b) {
        return w + "|" + a + "|" + l + "|" + b;
    }

    private static BigDecimal quantity(BigDecimal v, int scale, boolean positive) {
        need(v != null && v.signum() >= (positive ? 1 : 0) && v.compareTo(MAX) <= 0, "数量未知/非正/溢出");
        try {
            v.setScale(scale, RoundingMode.UNNECESSARY);
            return v.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException failure) {
            throw error("数量不能精确表达，禁止取整");
        }
    }

    /**
     * 当前支持 CNY/汇率1，原不含税价与金额须原生精确表达；未实施舍入合同不自动取整。
     */
    private static BigDecimal money(BigDecimal value, int scale, boolean positive) {
        need(value != null && value.signum() >= (positive ? 1 : 0), "实际采购成本未知/非正");
        BigDecimal max =
                scale == 6 ? new BigDecimal("99999999.999999") : new BigDecimal("9999999999.9999");
        need(value.compareTo(max) <= 0, "实际采购成本超原生精度");
        try {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException failure) {
            throw error("实际采购成本需要未批准舍入，禁止默认零价值或取整");
        }
    }

    private static boolean positive(Long id) {
        return id != null && id > 0;
    }

    private static boolean nullablePositive(Long id) {
        return id == null || id > 0;
    }

    private static boolean text(String s) {
        return s != null && !s.isBlank();
    }

    private static void need(boolean value, String reason) {
        if (!value) {
            throw error(reason);
        }
    }

    private static void one(int count) {
        need(count == 1, "真实写入CAS冲突，需整个事务回滚");
    }

    private static ServiceException error(String reason) {
        return new ServiceException("可信精确入库拒绝：" + reason);
    }

    private static <T> T read(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException failure) {
            throw error("当前来源读取失败");
        }
    }

    private static String sha256(String body) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw error("来源摘要不可用");
        }
    }
}

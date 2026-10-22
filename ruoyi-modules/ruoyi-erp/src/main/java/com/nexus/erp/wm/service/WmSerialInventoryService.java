package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.BeanUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.math.*;
import java.util.*;

/**
 * 仅在ERP本地事务中冻结并执行单件事实；AUDIT由调用方显式进入原版本适配器。
 */
@Service
@DS("erp")
public class WmSerialInventoryService {
    private final WmSerialInventoryRepository store;

    public WmSerialInventoryService(WmSerialInventoryRepository store) {
        this.store = store;
    }

    /**
     * 先按稳定身份排序再计算选择摘要，避免同一集合因客户端顺序不同而产生另一业务意图。
     *
     * @param items 业务行已确认的实际明细集合。
     * @return 按稳定顺序及原请求计算的完整内容摘要。
     */
    public String selectionHash(List<SnItem> items) {
        var ordered = items.stream().sorted(Comparator.comparing(SnItem::identityId)).toList();
        return TraceHashes.fingerprint(ordered);
    }

    /** 交接单的目标由原单行指定，SN选择不能改写该物理去向。 */
    private static void validateHandoverTargets(WmSerialPostingRequest request, List<SnItem> items) {
        if (!"WM_MATERIAL_HANDOVER".equals(request.sourceType())) return;
        need(request.targetLocationId() != null, "交接单缺少真实目标库位");
        for (SnItem item : items) {
            need(item.targetLocationId() == null || request.targetLocationId().equals(item.targetLocationId()),
                "交接SN目标库位与原单据不一致");
        }
    }

    /**
     * 将来源、方向、数量和策略版本绑定到命令指纹，后续重放必须保持原业务意图。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @param contentHash 按稳定顺序计算的完整内容摘要。
     * @return 按稳定顺序及原请求计算的完整内容摘要。
     */
    public String fingerprint(WmSerialPostingRequest r, long policyVersion, String contentHash) {
        // 生产批次在原来源门禁内分配；该ID由每件冻结明细与执行证明核对，不以新生成ID改变命令。
        return TraceHashes.fingerprint(
                2,
                policyVersion,
                r.sourceType(),
                r.sourceId(),
                r.lineKey(),
                r.txType(),
                r.materialId(),
                r.quantity().stripTrailingZeros().toPlainString(),
                r.direction(),
                contentHash);
    }

    /**
     * 在原业务事务中锁定来源并冻结完整逐件选择；原命令重放核对明细与指纹后返回既有过账组。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param detailVersion 单据实际身份明细版本。
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @param items 业务行已确认的实际明细集合。
     * @return 同一原单、明细版本与命令对应的稳定过账组标识。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long freeze(
            WmSerialPostingRequest r,
            String commandId,
            long detailVersion,
            long policyVersion,
            List<SnItem> items) {
        store.requireWrite();
        var policy = store.policy(r.materialId());
        need(policy.stage() == TrackingStage.STRICT && policy.serialManaged(), "仅STRICT单件物料使用冻结过账");
        store.lockSource(r);
        need(policy.version() == policyVersion, "物料策略版本已改变");
        need(
                commandId != null
                        && !commandId.isBlank()
                        && commandId.length() <= 128
                        && detailVersion > 0,
                "命令或明细版本无效");
        TraceRules.validateSerialItems(policy, r.quantity(), items);
        validateHandoverTargets(r, items);
        String hash = selectionHash(items), fp = fingerprint(r, policyVersion, hash);
        var prior = store.group(r);
        if (prior != null && prior.commandId().equals(commandId)) {
            need(
                    prior.commandId().equals(commandId)
                            && prior.detailVersion() == detailVersion
                            && fp.equals(prior.fingerprint())
                            && hash.equals(prior.contentHash())
                            && hash.equals(selectionHash(store.items(prior.id()))),
                    "原单据实物已冻结，修正必须另建来源单据");
            return prior.id();
        }
        if (prior != null) {
            need(
                    "WM_PRODUCTION_ISSUE".equals(r.sourceType())
                            && "POSTED".equals(prior.status())
                            && detailVersion == prior.detailVersion() + 1,
                    "原单据实物已冻结；仅部分生产领料可申请下一过账版本");
        }
        if (r.direction() > 0) {
            for (var item :
                    items.stream().sorted(Comparator.comparing(SnItem::identityId)).toList()) {
                store.validateInboundIdentitySource(r, item, commandId);
            }
        }
        return store.prepare(r, commandId, detailVersion, fp, hash, items);
    }

    /**
     * 在同一库存事务内执行完整逐件过账；审核阶段或非单件物料返回未接管，已过账重放只核验原证明。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @return 已由严格单件执行器接管时返回真；审核阶段或非单件物料返回假。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean post(WmSerialPostingRequest r) {
        store.requireWrite();
        TrackingPolicy policy = store.policy(r.materialId());
        if (policy.stage() == TrackingStage.AUDIT || !policy.serialManaged()) {
            return false;
        }
        store.lockSource(r);
        store.requirePostingState(r);
        var group = store.group(r);
        need(group != null, "STRICT单件物料必须先冻结实际SN明细");
        var items = store.items(group.id());
        TraceRules.validateSerialItems(policy, r.quantity(), items);
        validateHandoverTargets(r, items);
        String hash = selectionHash(items);
        long proofVersion =
                "POSTED".equals(group.status())
                        ? store.frozenPolicyVersion(group.id())
                        : policy.version();
        need(
                proofVersion > 0
                        && hash.equals(group.contentHash())
                        && fingerprint(r, proofVersion, hash).equals(group.fingerprint())
                        && r.quantity().compareTo(group.quantity()) == 0,
                "冻结实物明细、原始数量或策略版本已改变");
        if ("POSTED".equals(group.status())) {
            store.requireReplaySource(r);
            store.verifyProof(r, group, items);
            return true;
        }
        need("PREPARED".equals(group.status()), "过账组状态无效");
        store.validateFrozenSource(r, group, policy.version());
        if ("WM_MATERIAL_RECEIVE".equals(r.sourceType())) {
            store.verifyReceiveSelections(r, group, items);
        }
        if ("WM_MATERIAL_RETURN".equals(r.sourceType()) && "SCRAP".equals(r.txType())) {
            return postIssuedScrap(r, group, items);
        }
        List<Selection> selected = new ArrayList<>();
        Map<String, Long> inboundPools = new HashMap<>();
        // 稳定顺序锁正式身份及其全部历史库存，避免仅锁调用方指定行而遗漏重复正库存。
        for (SnItem item :
                items.stream().sorted(Comparator.comparing(SnItem::identityId)).toList()) {
            var identity = store.identity(item.identityId());
            need(
                    identity != null
                            && r.materialId().equals(identity.materialId())
                            && identity.code() != null,
                    "SN不属于原单据物料");
            if (policy.batchManaged()) {
                need(
                        item.batchId() != null
                                && (identity.batchId() == null
                                        || item.batchId().equals(identity.batchId())),
                        "SN与冻结批次不一致");
            }
            if (r.batchId() != null && !"PRD_WORK_ORDER".equals(r.sourceType())) {
                need(r.batchId().equals(item.batchId()), "SN不属于原单行显式指定批次");
            }
            var rows = store.stocks(item.identityId());
            List<WmMaterialStock> positive = new ArrayList<>();
            for (var stock : rows) {
                need(
                        stock.getStockQuantity() != null
                                && binary(stock.getStockQuantity())
                                && binary(zero(stock.getReservedQuantity()))
                                && zero(stock.getReservedQuantity())
                                                .compareTo(stock.getStockQuantity())
                                        <= 0,
                        "单件库存数量或预留不是0/1");
                if (stock.getStockQuantity().signum() > 0) {
                    positive.add(stock);
                }
            }
            need(positive.size() <= 1, "同一正式SN存在多行正库存，请先对账");
            if (r.direction() > 0) {
                need(positive.isEmpty(), "同一实物仍有在库正库存，禁止重复入库");
                need(!Set.of("SCRAPPED", "SCRAP").contains(identity.status()), "已报废SN不能重新收货");
                if (item.originalTxId() == null) {
                    need(
                            Set.of("REGISTERED", "CREATED", "IN_PRODUCTION", "PRODUCTION")
                                    .contains(identity.status()),
                            "已有历史生命周期的SN必须引用真实原事务退回");
                }
                store.validateInboundIdentitySource(r, item, group.commandId());
                BigDecimal cost = store.inboundCost(r, item);
                need(cost != null && cost.signum() >= 0, "入库成本来源缺失");
                var target = store.inboundStock(r, item, identity);
                store.validateInboundSelection(r, item, target);
                String poolKey =
                        TraceHashes.fingerprint(
                                target.getMaterialId(),
                                target.getWarehouseId(),
                                target.getLocationId(),
                                target.getBatchId(),
                                target.getVendorId(),
                                target.getWoId());
                Long pool =
                        inboundPools.computeIfAbsent(
                                poolKey, k -> store.inboundPool(target, policy.version()));
                target.setValuationGroupId(pool);
                selected.add(new Selection(item, identity, target, cost));
            } else {
                need("IN_STOCK".equals(identity.status()), "正式SN状态不是在库，不能扣减");
                need(positive.size() == 1, "选定SN当前不在库，不能扣减");
                var stock = positive.get(0);
                need(
                        item.sourceStockId() != null
                                && item.sourceStockId().equals(stock.getStockId())
                                && store.tenant().equals(stock.getTenantId())
                                && "0".equals(stock.getDelFlag())
                                && stock.getTrackingVersion() != null
                                && stock.getTrackingVersion() >= 2
                                && r.materialId().equals(stock.getMaterialId())
                                && item.identityId().equals(stock.getSnId())
                                && stock.getValuationGroupId() != null
                                && (r.warehouseId() == null
                                        || r.warehouseId().equals(stock.getWarehouseId()))
                                && (r.locationId() == null
                                        || r.locationId().equals(stock.getLocationId()))
                                && Objects.equals(item.batchId(), stock.getBatchId()),
                        "选定SN来源库存、版本或仓储维度漂移");
                need(
                        "COUNT_ADJUST".equals(r.txType())
                                || Long.valueOf(0).equals(stock.getFrozenFlag()),
                        "SN库存已冻结");
                need(
                        "COUNT_ADJUST".equals(r.txType())
                                || stock.getExpireDate() == null
                                || !stock.getExpireDate()
                                        .before(java.sql.Date.valueOf(java.time.LocalDate.now())),
                        "SN库存已过期");
                store.validateStockSource(r, stock);
                store.validateOutgoingOrigin(r, item, stock);
                selected.add(new Selection(item, identity, stock, null));
            }
        }
        Map<Long, List<Selection>> byPool = new TreeMap<>();
        for (var pick : selected) {
            byPool.computeIfAbsent(pick.stock().getValuationGroupId(), k -> new ArrayList<>())
                    .add(pick);
        }
        BigDecimal total = BigDecimal.ZERO, reserved = zero(r.consumeReserved());
        List<WmMaterialStockTx> facts = new ArrayList<>();
        for (var entry : byPool.entrySet()) {
            Long poolId = entry.getKey();
            var pool = store.pool(poolId);
            need(pool != null && r.materialId().equals(pool.materialId()), "原计价池映射缺失");
            var live = store.poolStocks(poolId);
            BigDecimal liveQty = BigDecimal.ZERO, liveAmount = BigDecimal.ZERO;
            for (var stock : live) {
                need(
                        stock.getStockQuantity() != null
                                && stock.getTotalCost() != null
                                && stock.getStockQuantity().signum() >= 0
                                && stock.getTotalCost().signum() >= 0,
                        "计价池余额未知");
                liveQty = liveQty.add(stock.getStockQuantity());
                liveAmount = liveAmount.add(stock.getTotalCost());
            }
            BigDecimal qty = BigDecimal.valueOf(entry.getValue().size());
            BigDecimal unit =
                    liveQty.signum() == 0
                            ? BigDecimal.ZERO
                            : live.stream()
                                    .filter(s -> s.getStockQuantity().signum() > 0)
                                    .findFirst()
                                    .orElseThrow()
                                    .getUnitCost();
            need(unit != null && unit.signum() >= 0, "原移动平均单位成本缺失");
            for (var stock : live) {
                if (stock.getStockQuantity().signum() > 0) {
                    need(unit.compareTo(stock.getUnitCost()) == 0, "原计价池单位成本不一致");
                }
            }
            BigDecimal amount;
            if (r.direction() > 0) {
                amount =
                        entry.getValue().stream()
                                .map(Selection::cost)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(4, RoundingMode.HALF_UP);
            } else {
                need(liveQty.compareTo(qty) >= 0, "原计价池数量不足");
                amount =
                        liveQty.compareTo(qty) == 0
                                ? liveAmount.setScale(4, RoundingMode.UNNECESSARY)
                                : unit.multiply(qty).setScale(4, RoundingMode.HALF_UP);
                need(amount.compareTo(liveAmount) <= 0, "原计价池成本不足");
            }
            var allocation =
                    TraceRules.allocateAmount(
                            amount,
                            entry.getValue().stream().map(x -> x.item().identityId()).toList());
            List<WmMaterialStock> remaining = new ArrayList<>(live);
            for (var pick : entry.getValue()) {
                var stock = pick.stock();
                BigDecimal oneAmount = allocation.get(pick.item().identityId());
                if (r.direction() > 0) {
                    stock.setStockQuantity(BigDecimal.ONE);
                    stock.setReservedQuantity(BigDecimal.ZERO);
                    stock.setTotalCost(oneAmount);
                    stock.setUnitCost(pick.cost().setScale(6, RoundingMode.HALF_UP));
                    store.insertStock(stock);
                    remaining.add(stock);
                    facts.add(
                            tx(
                                    r,
                                    group.id(),
                                    stock,
                                    1,
                                    oneAmount,
                                    stock.getUnitCost(),
                                    pick.item().originalTxId()));
                    store.updateIdentity(pick.identity(), stock, "IN_STOCK");
                    store.batch(r, stock, 1);
                } else {
                    BigDecimal consume = zero(store.consumeReservation(r, stock, reserved));
                    need(stock.getReservedQuantity().subtract(consume).signum() == 0, "SN已被其他来源预留");
                    stock.setReservedQuantity(BigDecimal.ZERO);
                    stock.setStockQuantity(BigDecimal.ZERO);
                    stock.setTotalCost(BigDecimal.ZERO);
                    store.saveStock(stock);
                    remaining.removeIf(s -> s.getStockId().equals(stock.getStockId()));
                    facts.add(
                            tx(
                                    r,
                                    group.id(),
                                    stock,
                                    -1,
                                    oneAmount,
                                    unit,
                                    pick.item().originalTxId()));
                    if (consume.signum() > 0) {
                        var reserve = tx(r, group.id(), stock, 0, BigDecimal.ZERO, unit, null);
                        reserve.setTxType("CONSUME_RESERVE");
                        reserve.setSourceDocType("SALES_ORDER");
                        reserve.setSourceDocId(r.reserveOrderId());
                        reserve.setSourceDocLineId(r.reserveLineId());
                        store.insertTx(reserve);
                        reserved = reserved.subtract(consume);
                    }
                    if (r.direction() == 0) {
                        WmMaterialStock target = new WmMaterialStock();
                        BeanUtils.copyProperties(stock, target);
                        target.setStockId(IdWorker.getId());
                        target.setVersion(0);
                        target.setWarehouseId(r.targetWarehouseId());
                        target.setAreaId(r.targetAreaId());
                        target.setLocationId(
                                pick.item().targetLocationId() != null
                                        ? pick.item().targetLocationId()
                                        : r.targetLocationId());
                        need(
                                target.getWarehouseId() != null && target.getLocationId() != null,
                                "调拨目标仓储维度缺失");
                        need(
                                !Objects.equals(target.getWarehouseId(), stock.getWarehouseId())
                                        || !Objects.equals(
                                                target.getLocationId(), stock.getLocationId()),
                                "调拨目标不能与同件来源储位相同");
                        store.storage(target);
                        target.setStockQuantity(BigDecimal.ONE);
                        target.setTotalCost(oneAmount);
                        target.setUnitCost(unit);
                        target.setValuationGroupId(
                                store.inboundPool(target, policy.version(), false));
                        var destination = store.poolStocks(target.getValuationGroupId());
                        store.insertStock(target);
                        destination = new ArrayList<>(destination);
                        destination.add(target);
                        rebalance(destination, sumAmount(destination));
                        facts.add(tx(r, group.id(), target, 1, oneAmount, unit, null));
                        store.updateIdentity(pick.identity(), target, "IN_STOCK");
                    } else {
                        store.updateIdentity(
                                pick.identity(),
                                stock,
                                "SCRAP".equals(r.txType()) ? "SCRAPPED" : "OUT");
                        store.batch(r, stock, -1);
                    }
                }
            }
            BigDecimal balance =
                    r.direction() > 0 ? liveAmount.add(amount) : liveAmount.subtract(amount);
            rebalance(remaining, balance, r.direction() > 0 ? null : unit);
            total = total.add(amount);
        }
        facts.sort(
                Comparator.comparing(WmMaterialStockTx::getSnId)
                        .thenComparing(WmMaterialStockTx::getTxFlag));
        for (var fact : facts) {
            store.insertTx(fact);
        }
        store.reduceSalesReservation(r, zero(r.consumeReserved()).subtract(reserved));
        Long single = byPool.size() == 1 ? byPool.keySet().iterator().next() : null;
        store.finish(group.id(), total, single);
        String payload =
                "{\"postingGroupId\":\""
                        + group.id()
                        + "\",\"sourceType\":\""
                        + r.sourceType()
                        + "\",\"sourceId\":\""
                        + r.sourceId()
                        + "\",\"quantity\":\""
                        + r.quantity().toPlainString()
                        + "\",\"amount\":\""
                        + total.toPlainString()
                        + "\",\"contentHash\":\""
                        + group.contentHash()
                        + "\"}";
        String children =
                facts.stream()
                        .map(
                                f ->
                                        "{\"identityId\":\""
                                                + f.getSnId()
                                                + "\",\"stockId\":\""
                                                + f.getMaterialStockId()
                                                + "\",\"txId\":\""
                                                + f.getTxId()
                                                + "\",\"txFlag\":\""
                                                + f.getTxFlag()
                                                + "\",\"quantity\":\"1\",\"amount\":\""
                                                + f.getTxAmount().toPlainString()
                                                + "\",\"identityVersion\":\""
                                                + store.identityVersion(f.getSnId())
                                                + "\"}")
                        .collect(java.util.stream.Collectors.joining(","));
        payload = payload.substring(0, payload.length() - 1) + ",\"items\":[" + children + "]}";
        store.enqueue(group.id(), payload, TraceHashes.sha256(payload));
        return true;
    }

    private boolean postIssuedScrap(
            WmSerialPostingRequest r, WmSerialInventoryRepository.Group group, List<SnItem> items) {
        List<WmMaterialStockTx> facts = new ArrayList<>();
        for (var item : items.stream().sorted(Comparator.comparing(SnItem::identityId)).toList()) {
            var sn = store.identity(item.identityId());
            need(
                    sn != null
                            && r.materialId().equals(sn.materialId())
                            && !Set.of("SCRAPPED", "SCRAP").contains(sn.status()),
                    "报废实物物料或原状态不符");
            var rows = store.stocks(item.identityId());
            need(
                    rows.stream()
                            .allMatch(
                                    s ->
                                            s.getStockQuantity() != null
                                                    && s.getStockQuantity().signum() == 0
                                                    && zero(s.getReservedQuantity()).signum() == 0),
                    "已领用报废实物不能仍有在库余额或预留");
            var original = store.issuedScrapStock(r, item);
            var fact =
                    tx(
                            r,
                            group.id(),
                            original,
                            0,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            item.originalTxId());
            fact.setBeforeQuantity(BigDecimal.ZERO);
            fact.setAfterQuantity(BigDecimal.ZERO);
            facts.add(fact);
        }
        for (var fact : facts) {
            store.insertTx(fact);
            var identity = store.identity(fact.getSnId());
            store.updateIdentity(identity, store.stock(fact.getMaterialStockId()), "SCRAPPED");
        }
        store.finish(group.id(), BigDecimal.ZERO, null);
        String payload =
                "{\"postingGroupId\":\""
                        + group.id()
                        + "\",\"sourceType\":\"WM_MATERIAL_RETURN\",\"sourceId\":\""
                        + r.sourceId()
                        + "\",\"effect\":\"ISSUED_SCRAP\",\"contentHash\":\""
                        + group.contentHash()
                        + "\"}";
        store.enqueue(group.id(), payload, TraceHashes.sha256(payload));
        return true;
    }

    /**
     * 从固定的原业务来源重新构造过账请求，实际库存写入仍走统一执行器。
     *
     * @param type 由业务入口固定的操作或来源类型。
     * @param line 服务端固定的原单行表名。
     * @return 上述校验或业务条件是否成立。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean postSource(String type, Long source, Long line) {
        return post(store.request(type, source, line));
    }

    /**
     * 按收货原单逐行应用冻结身份，任一行失败由原单事务整体回滚。
     *
     * @return 已经由严格单件库存路径处理的收货行主键集合。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Set<Long> postReceiveDocument(Long receive) {
        Set<Long> posted = new HashSet<>();
        for (Long line : store.receiveLineIds(receive)) {
            if (postSource("WM_MATERIAL_RECEIVE", receive, line)) {
                posted.add(line);
            }
        }
        return posted;
    }

    /**
     * 核验当前物料预留数量与追踪策略的一致性。
     *
     * @param material 实际业务物料及其上下文。
     * @param quantity 实际业务数量，不能以物料清单估算替代实际身份或用量。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void validateReservation(Long material, BigDecimal quantity) {
        var policy = store.policy(material);
        if (policy.stage() == TrackingStage.STRICT && policy.serialManaged()) {
            need(
                    quantity != null
                            && quantity.signum() > 0
                            && quantity.stripTrailingZeros().scale() <= 0,
                    "STRICT单件库存只允许整数件预留");
        }
    }

    /**
     * 按认证收货原证明及原成本执行完整逐件入库。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void postCertifiedReceive(Long receive, Map<Long, BigDecimal> costs) {
        for (var e : costs.entrySet()) {
            var r = store.request("WM_MATERIAL_RECEIVE", receive, e.getKey());
            need(
                    post(
                            WmSerialPostingRequest.in(
                                    r.sourceType(),
                                    r.sourceId(),
                                    r.sourceLineId(),
                                    r.txType(),
                                    r.materialId(),
                                    r.quantity(),
                                    r.warehouseId(),
                                    r.locationId(),
                                    r.batchId(),
                                    e.getValue())),
                    "认证单件收货不能降级到旧版数量路径");
        }
    }

    /**
     * 核对认证收货当时的完整流水集合与成本证明，后续移库不触发补库存。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void verifyCertifiedReceive(Long receive, Map<Long, BigDecimal> costs) {
        for (var e : costs.entrySet()) {
            var r = store.proofRequest("WM_MATERIAL_RECEIVE", receive, e.getKey(), "RECEIVE_IN");
            var g = store.group(r);
            need(g != null && "POSTED".equals(g.status()), "认证单件收货原组缺失");
            var items = store.items(g.id());
            need(selectionHash(items).equals(g.contentHash()), "认证单件原明细摘要不符");
            store.verifyProof(r, g, items);
            need(
                    r.quantity()
                                    .multiply(e.getValue())
                                    .setScale(4, RoundingMode.HALF_UP)
                                    .compareTo(g.amount())
                            == 0,
                    "认证单件收货成本与原供应报价不一致");
        }
    }

    public boolean hasV2Receipt(Long receive) {
        return store.hasV2Receipt(receive);
    }

    /**
     * 核对当前物料的身份库存与原计价范围余额守恒。
     *
     * @param material 实际业务物料及其上下文。
     */
    public void verifyMaterialPools(Long material) {
        store.verifyMaterialPools(material);
    }

    /**
     * 为有充分原领料证据的兼容路径登记实际来源绑定，不重复扣减库存。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     */
    public void bindTrackedLegacyIssue(WmSerialPostingRequest r) {
        if (isStrictTracked(r.materialId())) {
            store.bindActualLegacyIssue(r);
        }
    }

    /**
     * 判断当前物料是否已经进入严格追踪阶段。
     *
     * @param material 实际业务物料及其上下文。
     * @return 上述校验或业务条件是否成立。
     */
    public boolean isStrictTracked(Long material) {
        var p = store.policy(material);
        return p.stage() == TrackingStage.STRICT && (p.batchManaged() || p.serialManaged());
    }

    /**
     * 判断当前物料是否为严格单件追踪。
     *
     * @param material 实际业务物料及其上下文。
     * @return 上述校验或业务条件是否成立。
     */
    public boolean isStrictSerial(Long material) {
        var p = store.policy(material);
        return p.stage() == TrackingStage.STRICT && p.serialManaged();
    }

    /**
     * 读取本次来源实际消费的预留量，避免重复释放或扣减。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @return 本来源已过账结果中实际消费的预留数量。
     */
    public BigDecimal consumedReservation(WmSerialPostingRequest r) {
        return zero(store.consumedReservation(r));
    }

    /**
     * 原业务行的实际身份、批次与库存选择。
     *
     * @param item 当前实际业务行或单件明细。
     * @param identity 跨模块稳定实物身份。
     * @param stock 已核实来源与位置的实际库存记录。
     * @param cost 原来源凭据确定的实际成本。
     */
    private record Selection(
            SnItem item,
            WmSerialInventoryRepository.Identity identity,
            WmMaterialStock stock,
            BigDecimal cost) {}

    private void rebalance(List<WmMaterialStock> rows, BigDecimal total) {
        rebalance(rows, total, null);
    }

    private void rebalance(List<WmMaterialStock> rows, BigDecimal total, BigDecimal preservedUnit) {
        var active = rows.stream().filter(s -> s.getStockQuantity().signum() > 0).toList();
        if (active.isEmpty()) {
            need(total.signum() == 0, "计价池清空后金额未守恒");
            return;
        }
        var costs =
                TraceRules.allocateAmount(
                        total.setScale(4, RoundingMode.UNNECESSARY),
                        active.stream().map(WmMaterialStock::getSnId).toList());
        BigDecimal unit =
                preservedUnit != null
                        ? preservedUnit
                        : total.divide(BigDecimal.valueOf(active.size()), 6, RoundingMode.HALF_UP);
        for (var s : active) {
            need(
                    BigDecimal.ONE.compareTo(s.getStockQuantity()) == 0 && s.getSnId() != null,
                    "计价池含未审计聚合库存");
            s.setUnitCost(unit);
            s.setTotalCost(costs.get(s.getSnId()));
            store.saveStock(s);
        }
    }

    private BigDecimal sumAmount(List<WmMaterialStock> rows) {
        return rows.stream()
                .map(WmMaterialStock::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private WmMaterialStockTx tx(
            WmSerialPostingRequest r,
            Long group,
            WmMaterialStock s,
            int flag,
            BigDecimal amount,
            BigDecimal cost,
            Long related) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        BeanUtils.copyProperties(s, tx);
        tx.setTxId(IdWorker.getId());
        tx.setTxType(r.txType());
        tx.setTenantId(store.tenant());
        tx.setTrackingVersion(2L);
        tx.setPostingGroupId(group);
        tx.setMaterialStockId(s.getStockId());
        tx.setTxFlag(flag);
        tx.setTxQuantity(BigDecimal.ONE);
        tx.setBeforeQuantity(flag < 0 ? BigDecimal.ONE : BigDecimal.ZERO);
        tx.setAfterQuantity(flag > 0 ? BigDecimal.ONE : BigDecimal.ZERO);
        if (flag == 0) {
            tx.setBeforeQuantity(BigDecimal.ONE);
            tx.setAfterQuantity(BigDecimal.ONE);
        }
        tx.setUnitCost(cost);
        tx.setTxAmount(amount);
        tx.setSourceDocType(r.sourceType());
        tx.setSourceDocId(r.sourceId());
        tx.setSourceDocLineId(r.sourceLineId());
        tx.setRelatedTxId(related);
        tx.setDelFlag("0");
        tx.setTxDate(new Date());
        tx.setErpDate(new Date());
        return tx;
    }

    static boolean binary(BigDecimal qty) {
        return qty != null && (qty.signum() == 0 || BigDecimal.ONE.compareTo(qty) == 0);
    }

    static BigDecimal zero(BigDecimal qty) {
        return qty == null ? BigDecimal.ZERO : qty;
    }

    static void need(boolean valid, String message) {
        if (!valid) {
            throw new ServiceException(message);
        }
    }
}

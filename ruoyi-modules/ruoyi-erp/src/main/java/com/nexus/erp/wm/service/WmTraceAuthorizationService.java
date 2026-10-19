package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmTraceAuthorizationBo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import java.math.BigDecimal;
import java.util.*;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/**
 * 实际ERP来源授权：先锁原工单/需求/库存，持久化授权，最后同事务冻结可投递事件。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class WmTraceAuthorizationService {
    private final WmSerialInventoryRepository store;
    private final WmProductionRecptWriteGuard guard;
    private final IWmBatchService batches;

    /**
     * 来源凭据核验通过后返回的实际额度授权。
     *
     * @param sourceId 由来源类型限定的原业务记录标识。
     * @param eventId 跨模块事件的稳定标识。
     * @param erpBatchId 经来源凭据核实的库存批次身份。
     * @param contentHash 按稳定顺序计算的完整内容摘要。
     */
    public record Authorization(
            Long sourceId, String eventId, Long erpBatchId, String contentHash) {}

    @Transactional(rollbackFor = Exception.class)
    public Authorization productionLot(WmTraceAuthorizationBo command) {
        store.requireWrite();
        var wo = guard.lockWorkOrder(command.getErpWoId());
        need(
                Set.of("RELEASED", "IN_PROGRESS").contains(wo.getWoStatus())
                        && wo.getPlannedQty() != null
                        && wo.getPlannedQty().signum() > 0,
                "生产批次只能由已下达/生产中的原工单授权");
        var policy = store.policy(wo.getMaterialId());
        need(policy.version() == command.getPolicyVersion(), "生产策略版本已改变");
        String fp =
                TraceHashes.fingerprint(
                        command.getCommandId(),
                        wo.getWoId(),
                        command.getMesWoId(),
                        command.getWarehouseId(),
                        command.getLocationId(),
                        policy.version());
        var prior =
                store.jdbc.queryForList(
                        "SELECT * FROM wm_trace_outbox WHERE tenant_id=? AND"
                            + " event_type='ERP_PRODUCTION_LOT_AUTHORIZED' AND"
                            + " aggregate_type='PRODUCTION_LOT' ORDER BY aggregate_id FOR UPDATE",
                        store.tenant());
        for (var old : prior) {
            var value = JSON.parseObject(old.get("payload").toString());
            if (command.getCommandId().equals(value.getString("commandId"))) {
                need(fp.equals(value.getString("fingerprint")), "生产批次授权命令内容不符");
                return originalLot(command, wo, policy, old);
            }
        }
        if (policy.stage() == TrackingStage.STRICT
                && policy.batchManaged()
                && !policy.serialManaged()) {
            var original =
                    store.jdbc.queryForList(
                            "SELECT batch_id FROM wm_batch WHERE tenant_id=? AND"
                                    + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            store.tenant(),
                            wo.getWoId());
            need(original.size() <= 1, "原batch-only工单已有多个成品批次，须通过来源单据纠正后再授权");
            if (!original.isEmpty()) {
                Long batch = WmSerialInventoryRepository.id(original.get(0).get("batch_id"));
                var events =
                        prior.stream()
                                .filter(
                                        old ->
                                                Objects.equals(
                                                        batch,
                                                        JSON.parseObject(
                                                                        old.get("payload")
                                                                                .toString())
                                                                .getLong("erpBatchId")))
                                .toList();
                need(events.size() == 1, "原单批次工单缺少唯一真实生产批次授权");
                return originalLot(command, wo, policy, events.get(0));
            }
            need(
                    prior.stream()
                            .noneMatch(
                                    old ->
                                            Objects.equals(
                                                    wo.getWoId(),
                                                    JSON.parseObject(old.get("payload").toString())
                                                            .getLong("erpWoId"))),
                    "原生产批次授权缺少真实ERP批次，禁止补造第二来源");
        }
        WmProductionRecpt promise = new WmProductionRecpt();
        promise.setWoId(wo.getWoId());
        promise.setWoNo(wo.getWoNo());
        promise.setMaterialId(wo.getMaterialId());
        promise.setMaterialCode(wo.getMaterialCode());
        promise.setQuantity(wo.getPlannedQty());
        promise.setWarehouseId(command.getWarehouseId());
        promise.setLocationId(command.getLocationId());
        promise.setRecptDate(new Date());
        promise.setStatus("DRAFT");
        guard.validateDraft(wo, promise);
        var batch =
                batches.createBatchByMaterial(
                        wo.getMaterialId(),
                        "PRD_WORK_ORDER",
                        wo.getWoId(),
                        wo.getWoNo(),
                        command.getWarehouseId(),
                        null);
        need(batch != null && batch.getBatchId() != null, "原工单批次创建失败");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", "PRODUCTION_OUTPUT");
        payload.put("sourceId", wo.getWoId().toString());
        payload.put("sourceLineId", batch.getBatchId().toString());
        payload.put("erpWoId", wo.getWoId().toString());
        payload.put("mesWoId", command.getMesWoId().toString());
        payload.put("erpBatchId", batch.getBatchId().toString());
        payload.put("materialId", wo.getMaterialId().toString());
        payload.put("quantity", wo.getPlannedQty().toPlainString());
        payload.put("commandId", command.getCommandId());
        payload.put("fingerprint", fp);
        payload.put("warehouseId", command.getWarehouseId().toString());
        payload.put("locationId", command.getLocationId().toString());
        payload.put("policyVersion", Long.toString(policy.version()));
        String text = store.encode(payload),
                hash = TraceHashes.sha256(text),
                event = "ERP-LOT-" + batch.getBatchId();
        store.appendEvent(
                event,
                "PRODUCTION_LOT",
                batch.getBatchId(),
                1,
                "ERP_PRODUCTION_LOT_AUTHORIZED",
                text);
        return new Authorization(wo.getWoId(), event, batch.getBatchId(), hash);
    }

    private Authorization originalLot(
            WmTraceAuthorizationBo command,
            com.nexus.erp.prd.domain.PrdWorkOrder wo,
            TrackingPolicy policy,
            Map<String, Object> event) {
        String text = event.get("payload").toString();
        var value = JSON.parseObject(text);
        Long batch = value.getLong("erpBatchId");
        need(
                TraceHashes.sha256(text).equals(event.get("content_hash"))
                        && batch != null
                        && batch > 0
                        && "PRODUCTION_OUTPUT".equals(value.getString("sourceType"))
                        && Objects.equals(wo.getWoId(), value.getLong("sourceId"))
                        && Objects.equals(wo.getWoId(), value.getLong("erpWoId"))
                        && Objects.equals(command.getMesWoId(), value.getLong("mesWoId"))
                        && Objects.equals(wo.getMaterialId(), value.getLong("materialId"))
                        && Objects.equals(command.getWarehouseId(), value.getLong("warehouseId"))
                        && Objects.equals(command.getLocationId(), value.getLong("locationId"))
                        && Objects.equals(policy.version(), value.getLong("policyVersion"))
                        && value.getBigDecimal("quantity") != null
                        && wo.getPlannedQty().compareTo(value.getBigDecimal("quantity")) == 0
                        && TraceHashes.fingerprint(
                                        value.getString("commandId"),
                                        wo.getWoId(),
                                        command.getMesWoId(),
                                        command.getWarehouseId(),
                                        command.getLocationId(),
                                        policy.version())
                                .equals(value.getString("fingerprint")),
                "原生产批次授权的工单、MES归属、仓储或策略承诺不符，禁止另造批次");
        store.authorizedProductionBatchCode(
                wo.getWoId(), command.getMesWoId(), batch, command.getWarehouseId());
        return new Authorization(
                wo.getWoId(),
                event.get("event_id").toString(),
                batch,
                event.get("content_hash").toString());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Authorization> reserveBackflush(WmTraceAuthorizationBo command) {
        store.requireWrite();
        var wo = guard.lockWorkOrder(command.getErpWoId());
        need(Set.of("RELEASED", "IN_PROGRESS").contains(wo.getWoStatus()), "实际倒冲预留必须在生产完成前授权");
        need(wo.getBackflushFlag() != null && wo.getBackflushFlag() == 1, "原工单不是倒冲工单");
        need(
                command.getSelections() != null
                        && !command.getSelections().isEmpty()
                        && command.getMaterialId() != null
                        && command.getRequirementDetailId() != null,
                "倒冲预留缺少实际库存选择和原物料需求行");
        var requirement =
                store.row(
                        "SELECT d.* FROM prd_material_requirement_detail d JOIN"
                                + " prd_material_requirement h ON h.tenant_id=d.tenant_id AND"
                                + " h.requirement_id=d.requirement_id WHERE d.tenant_id=? AND"
                                + " d.detail_id=? AND h.plan_id=? AND d.material_id=? AND"
                                + " d.backflush_flag=1 AND d.del_flag='0' AND h.del_flag='0' FOR"
                                + " UPDATE",
                        store.tenant(),
                        command.getRequirementDetailId(),
                        wo.getPlanId(),
                        command.getMaterialId());
        var policy = store.policy(command.getMaterialId());
        need(
                policy.stage() == TrackingStage.STRICT
                        && (policy.batchManaged() || policy.serialManaged())
                        && policy.version() == command.getPolicyVersion(),
                "预留策略不是当前STRICT追踪物料");
        for (var pick : command.getSelections()) {
            need(
                    pick != null
                            && pick.sourceStockId() != null
                            && pick.sourceStockId() > 0
                            && pick.quantity() != null
                            && pick.quantity().signum() > 0
                            && pick.quantity().stripTrailingZeros().scale() <= 4,
                    "实际库存选择或基本单位数量精度无效");
        }
        var ordered =
                command.getSelections().stream()
                        .sorted(
                                Comparator.comparing(
                                        WmTraceAuthorizationBo.Selection::sourceStockId))
                        .toList();
        String hash =
                TraceHashes.fingerprint(
                        command.getErpWoId(),
                        command.getMesWoId(),
                        command.getRequirementDetailId(),
                        command.getMaterialId(),
                        policy.version(),
                        ordered);
        var old =
                store.jdbc.queryForList(
                        "SELECT * FROM wm_trace_material_reservation WHERE tenant_id=? AND"
                                + " command_id=? FOR UPDATE",
                        store.tenant(),
                        command.getCommandId());
        if (!old.isEmpty()) {
            need(hash.equals(old.get(0).get("content_hash")), "原预留命令内容不符");
            return authorizations(WmSerialInventoryRepository.id(old.get(0).get("reservation_id")));
        }
        for (var pick : ordered) {
            need(
                    pick != null && pick.quantity() != null && pick.quantity().signum() > 0,
                    "实际预留数量无效");
        }
        BigDecimal total =
                ordered.stream()
                        .map(WmTraceAuthorizationBo.Selection::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        need(total.signum() > 0, "预留实际数量必须大于0");
        BigDecimal open =
                store.jdbc.queryForObject(
                        "SELECT COALESCE(SUM(quantity),0) FROM wm_trace_material_reservation WHERE"
                                + " tenant_id=? AND requirement_detail_id=? AND status='RESERVED'",
                        BigDecimal.class,
                        store.tenant(),
                        command.getRequirementDetailId());
        need(
                open != null
                        && requirement.get("required_quantity") != null
                        && requirement.get("issued_quantity") != null
                        && total.add(open)
                                        .compareTo(
                                                WmSerialInventoryRepository.decimal(
                                                                requirement.get(
                                                                        "required_quantity"))
                                                        .subtract(
                                                                WmSerialInventoryRepository.decimal(
                                                                        requirement.get(
                                                                                "issued_quantity"))))
                                <= 0,
                "实际预留合计超过原已确认需求行剩余授权量");
        long reservation = IdWorker.getId();
        WmSerialInventoryRepository.one(
                store.jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_material_reservation(reservation_id,tenant_id,command_id,erp_wo_id,mes_wo_id,requirement_detail_id,material_id,policy_version,quantity,content_hash,status)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,'RESERVED')",
                        reservation,
                        store.tenant(),
                        command.getCommandId(),
                        wo.getWoId(),
                        command.getMesWoId(),
                        command.getRequirementDetailId(),
                        command.getMaterialId(),
                        policy.version(),
                        total,
                        hash));
        int index = 0;
        Set<Long> stocks = new HashSet<>();
        for (var pick : ordered) {
            need(
                    stocks.add(pick.sourceStockId())
                            && pick.quantity() != null
                            && pick.quantity().signum() > 0,
                    "实际库存选择重复或数量无效");
            var stock = store.stock(pick.sourceStockId());
            need(
                    stock.getMaterialId().equals(command.getMaterialId())
                            && Objects.equals(stock.getBatchId(), pick.erpBatchId())
                            && Objects.equals(stock.getSnId(), pick.identityId())
                            && Long.valueOf(0).equals(stock.getFrozenFlag())
                            && "0".equals(stock.getDelFlag()),
                    "预留物料、正式身份、批次或冻结状态不符");
            need(
                    stock.getWarehouseId().equals(command.getWarehouseId())
                            && Objects.equals(stock.getLocationId(), command.getLocationId()),
                    "实际预留不在指定的原仓储范围");
            if (policy.serialManaged()) {
                var identity = store.identity(pick.identityId());
                need(
                        identity != null
                                && stock.getMaterialId().equals(identity.materialId())
                                && "IN_STOCK".equals(identity.status())
                                && store.stocks(pick.identityId()).stream()
                                                .filter(s -> s.getStockQuantity().signum() > 0)
                                                .count()
                                        == 1,
                        "原单件预留身份不是唯一有效在库实物");
            }
            if (policy.serialManaged()) {
                need(
                        stock.getTrackingVersion() != null
                                && stock.getTrackingVersion() >= 2
                                && BigDecimal.ONE.compareTo(pick.quantity()) == 0
                                && BigDecimal.ONE.compareTo(stock.getStockQuantity()) == 0
                                && stock.getReservedQuantity().signum() == 0,
                        "单件实际预留必须是可用的一件正式SN");
            }
            if (policy.batchManaged()) {
                need(pick.erpBatchId() != null, "批次追踪预留缺少ERP批次");
            }
            WmSerialInventoryRepository.one(
                    store.jdbc.update(
                            "UPDATE wm_material_stock SET"
                                + " reserved_quantity=reserved_quantity+?,version=version+1 WHERE"
                                + " tenant_id=? AND stock_id=? AND version=? AND"
                                + " stock_quantity-reserved_quantity>=? AND frozen_flag=0 AND"
                                + " del_flag='0'",
                            pick.quantity(),
                            store.tenant(),
                            stock.getStockId(),
                            stock.getVersion(),
                            pick.quantity()));
            long line = IdWorker.getId();
            WmSerialInventoryRepository.one(
                    store.jdbc.update(
                            "INSERT INTO"
                                + " wm_trace_material_reservation_line(reservation_line_id,tenant_id,reservation_id,line_no,source_stock_id,identity_id,erp_batch_id,valuation_group_id,quantity,consumed_quantity,version)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,0,1)",
                            line,
                            store.tenant(),
                            reservation,
                            ++index,
                            stock.getStockId(),
                            stock.getSnId(),
                            stock.getBatchId(),
                            stock.getValuationGroupId(),
                            pick.quantity()));
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("sourceType", "BACKFLUSH_RESERVATION");
            source.put("sourceId", Long.toString(reservation));
            source.put("sourceLineId", Long.toString(line));
            source.put("erpWoId", wo.getWoId().toString());
            source.put("mesWoId", command.getMesWoId().toString());
            source.put("materialId", command.getMaterialId().toString());
            source.put(
                    "erpBatchId", pick.erpBatchId() == null ? null : pick.erpBatchId().toString());
            source.put("quantity", pick.quantity().toPlainString());
            source.put("reservationId", Long.toString(reservation));
            source.put("reservationLineId", Long.toString(line));
            source.put("sourceStockId", stock.getStockId().toString());
            source.put("identityId", stock.getSnId() == null ? null : stock.getSnId().toString());
            source.put("backflushFlag", 1);
            source.put("contentHash", hash);
            store.appendEvent(
                    "ERP-RESERVE-" + line,
                    "MATERIAL_RESERVATION_LINE",
                    line,
                    1,
                    "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED",
                    store.encode(source));
        }
        return authorizations(reservation);
    }

    private List<Authorization> authorizations(Long id) {
        return store
                .jdbc
                .queryForList(
                        "SELECT reservation_line_id,erp_batch_id FROM"
                                + " wm_trace_material_reservation_line WHERE tenant_id=? AND"
                                + " reservation_id=? ORDER BY line_no",
                        store.tenant(),
                        id)
                .stream()
                .map(
                        l ->
                                new Authorization(
                                        id,
                                        "ERP-RESERVE-" + l.get("reservation_line_id"),
                                        WmSerialInventoryRepository.id(l.get("erp_batch_id")),
                                        null))
                .toList();
    }
}

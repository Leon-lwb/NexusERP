package com.nexus.mes.trace;

import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;
import static com.nexus.mes.trace.MesTraceStore.number;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.mes.pro.event.SnCreatedEvent;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 仅在库存身份预留已提交后进入本地制造写入，业务行锁及写入效果均留在制造库。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesSnCreationWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MesTraceContext context;
    private final MesTraceStore events;
    private final ApplicationEventPublisher publisher;

    /**
     * 冻结原命令、实际来源和操作人员的单件创建请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param cardId 当前工单下的合法流转卡标识。
     * @param mesWoId MES 生产工单主键。
     * @param sourceId 由来源类型限定的原业务记录标识。
     * @param subcontractNo 真实委外单编号。
     * @param materialId 当前业务行实际物料标识。
     * @param productLotId 产品自身批次，独立于组件消耗批次。
     * @param parentSnId 关联产品在制造模块的本地主键。
     * @param remark 业务备注，不能替代来源证明。
     * @param expectedCode 原来源期待的物料或批次编码。
     */
    public record Request(
            String commandId,
            Long cardId,
            Long mesWoId,
            Long sourceId,
            String source,
            String subcontractNo,
            Long materialId,
            Long productLotId,
            Long parentSnId,
            String remark,
            String expectedCode) {}

    /**
     * 核对原本地创建请求及数量，内容一致时返回原已提交的本地主键集合。
     *
     * @param request 本次业务的原始请求，具体校验要求见方法说明。
     * @param quantity 原请求预期创建的制造单件数量。
     * @return 原命令已经创建的制造单件主键集合；没有原记录时返回空。
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> replay(Request request, int quantity) {
        context.requireTenant(context.tenant());
        MesTraceSql.command(request.commandId());
        var row =
                one(
                        jdbc,
                        "SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND event_id=? AND"
                                + " status='APPLIED'",
                        context.tenant(),
                        TraceHashes.fingerprint(
                                context.tenant(), "SN_CREATION", request.commandId()));
        if (row == null) {
            return null;
        }
        var payload = events.payload(row);
        need(
                payload.isArray()
                        && payload.size() == 4
                        && payload.get(0).equals(events.read(events.encode(request)))
                        && payload.get(3).size() == quantity,
                "同一发号命令不能更换本地业务来源或数量");
        var result = new ArrayList<Long>();
        events.result(row)
                .path("localSnIds")
                .forEach(n -> result.add(new BigDecimal(n.asText()).longValueExact()));
        return result;
    }

    /**
     * 在正式身份预留已提交后，使用同一正式编码创建制造本地记录并同库追加确认事件。
     *
     * @param request 本次业务的原始请求，具体校验要求见方法说明。
     * @return 与正式身份一一对应的新制造单件主键集合。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> write(SerialReservation reservation, Request request) {
        return writeLocal(reservation, request, null);
    }

    /**
     * 使用事务外已核验的真实来源创建本地单件，来源、身份映射及确认事件整体提交。
     *
     * @param request 本次业务的原始请求，具体校验要求见方法说明。
     * @return 已绑定真实来源并整体提交的制造单件主键集合。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> writeWithSource(
            SerialReservation reservation, Request request, MaterialTraceEvent source) {
        return writeLocal(reservation, request, source);
    }

    private List<Long> writeLocal(
            SerialReservation reservation, Request request, MaterialTraceEvent sourceProof) {
        context.requireWrite();
        String tenant = context.tenant();
        need(
                reservation != null
                        && reservation.commandId().equals(request.commandId())
                        && !reservation.identities().isEmpty()
                        && Set.of("RESERVED", "CONFIRMED").contains(reservation.status()),
                "ERP预留结果无效");
        var command =
                events.command(
                        request.commandId(),
                        "SN_CREATION",
                        Arrays.asList(
                                request,
                                reservation.reservationId(),
                                reservation.fingerprint(),
                                reservation.identities()));
        if ("APPLIED".equals(command.get("status"))) {
            var ids = new ArrayList<Long>();
            events.result(command)
                    .path("localSnIds")
                    .forEach(n -> ids.add(new BigDecimal(n.asText()).longValueExact()));
            return ids;
        }
        if (sourceProof != null) {
            events.subcontractProof(sourceProof);
        }
        Map<String, Object> wo = null, card = null;
        JsonNode subcontractFact = null;
        if (request.mesWoId() != null) {
            wo =
                    one(
                            jdbc,
                            "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant,
                            request.mesWoId());
            need(
                    wo != null
                            && Objects.equals(request.materialId(), id(wo.get("material_id")))
                            && Set.of("CREATED", "RELEASED", "IN_PROGRESS", "PARTIAL_COMPLETED")
                                    .contains(text(wo.get("wo_status"))),
                    "SN来源工单不存在、物料不符或已终结");
        } else {
            need(
                    "SUBCONTRACT".equals(request.source())
                            && positive(request.sourceId())
                            && request.subcontractNo() != null
                            && !request.subcontractNo().isBlank(),
                    "只有真实委外来源可以没有MES工单");
            var credential = events.subcontractSource(request.sourceId());
            need(credential != null, "委外SN缺少已核验的实际委外来源");
            var proof = events.payload(credential);
            subcontractFact = proof;
            need(
                    request.sourceId().equals(number(proof, "subcontractId"))
                            && request.materialId().equals(number(proof, "materialId"))
                            && request.subcontractNo().equals(proof.path("subcontractNo").asText()),
                    "委外来源与发号请求不匹配");
            Long existing =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM pro_sn WHERE tenant_id=? AND source='SUBCONTRACT'"
                                    + " AND source_id=? AND del_flag='0'",
                            Long.class,
                            tenant,
                            request.sourceId());
            need(
                    BigDecimal.valueOf(existing + reservation.identities().size())
                                    .compareTo(MesMaterialTraceWriter.decimal(proof, "quantity"))
                            <= 0,
                    "委外发号超过真实委外数量");
        }
        if (request.cardId() != null) {
            card =
                    one(
                            jdbc,
                            "SELECT * FROM pro_flow_card WHERE tenant_id=? AND card_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant,
                            request.cardId());
            need(
                    card != null
                            && Objects.equals(request.mesWoId(), id(card.get("mes_wo_id")))
                            && Objects.equals(request.materialId(), id(card.get("material_id")))
                            && !Set.of("COMPLETED", "CLOSED")
                                    .contains(text(card.get("card_status"))),
                    "SN实际流转卡与工单不匹配");
            Long existing =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM pro_sn WHERE tenant_id=? AND card_id=? AND"
                                    + " del_flag='0'",
                            Long.class,
                            tenant,
                            request.cardId());
            need(
                    BigDecimal.valueOf(existing + reservation.identities().size())
                                    .compareTo(qty(card.get("planned_qty")))
                            <= 0,
                    "SN生成数量超过实际流转卡计划量");
        }
        if (request.productLotId() != null) {
            var lot =
                    one(
                            jdbc,
                            "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0'"
                                    + " FOR UPDATE",
                            tenant,
                            request.productLotId());
            need(
                    lot != null && Objects.equals(request.materialId(), id(lot.get("material_id"))),
                    "成品批次与SN物料不匹配");
            long count =
                    jdbc.queryForList(
                                    "SELECT sn_id FROM pro_sn WHERE tenant_id=? AND"
                                            + " product_lot_id=? AND del_flag='0' FOR UPDATE",
                                    tenant,
                                    request.productLotId())
                            .size();
            need(
                    BigDecimal.valueOf(count + reservation.identities().size())
                                    .compareTo(qty(lot.get("quantity")))
                            <= 0,
                    "成品批次SN数量超过批次数量");
        }
        String parentCode = null;
        if (request.parentSnId() != null) {
            var parent =
                    one(
                            jdbc,
                            "SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'"
                                    + " FOR UPDATE",
                            tenant,
                            request.parentSnId());
            need(
                    parent != null
                            && !Set.of("SHIPPED", "SCRAPPED")
                                    .contains(text(parent.get("sn_status"))),
                    "父SN不存在或已终结");
            parentCode = text(parent.get("sn_code"));
        }
        var locals = new ArrayList<Long>();
        var identities = new ArrayList<Long>();
        var seen = new HashSet<Long>();
        for (var identity : reservation.identities()) {
            need(
                    positive(identity.identityId())
                            && seen.add(identity.identityId())
                            && request.materialId().equals(identity.materialId())
                            && identity.snCode() != null
                            && !identity.snCode().isBlank(),
                    "ERP预留身份重复或物料不匹配");
            need(
                    request.expectedCode() == null
                            || request.expectedCode().isBlank()
                            || request.expectedCode().equals(identity.snCode()),
                    "手工SN必须使用ERP正式号码，外部编码请登记为别名");
            Long local = IdWorker.getId();
            jdbc.update(
                    "INSERT INTO"
                        + " pro_sn(sn_id,tenant_id,identity_id,identity_version,product_lot_id,handoff_status,sn_code,mes_wo_id,wo_no,card_id,card_no,material_id,material_code,material_name,sn_status,parent_sn_id,parent_sn_code,source,source_id,subcontract_no,production_date,remark,del_flag)"
                        + " VALUES(?,?,?,1,?,'NONE',?,?,?,?,?,?,?,?,'IN_PRODUCTION',?,?,?,?,?,CURRENT_DATE,?,'0')",
                    local,
                    tenant,
                    identity.identityId(),
                    request.productLotId(),
                    identity.snCode(),
                    request.mesWoId(),
                    wo == null ? null : wo.get("wo_no"),
                    request.cardId(),
                    card == null ? null : card.get("card_no"),
                    request.materialId(),
                    card == null
                            ? (subcontractFact == null
                                    ? null
                                    : subcontractFact.path("materialCode").asText(null))
                            : card.get("material_code"),
                    card == null
                            ? (subcontractFact == null
                                    ? null
                                    : subcontractFact.path("materialName").asText(null))
                            : card.get("material_name"),
                    request.parentSnId(),
                    parentCode,
                    request.source(),
                    request.sourceId(),
                    request.subcontractNo(),
                    request.remark());
            locals.add(local);
            identities.add(identity.identityId());
            // 产品自身批次独立于组件消耗，标签监听器继续接收既有业务事件。
            publisher.publishEvent(
                    new SnCreatedEvent(
                            local,
                            identity.snCode(),
                            request.productLotId(),
                            request.materialId()));
        }
        events.append(
                "SERIAL_RESERVATION_CONFIRM",
                "SERIAL_RESERVATION",
                reservation.reservationId(),
                1,
                Map.of(
                        "commandId",
                        reservation.commandId(),
                        "fingerprint",
                        reservation.fingerprint(),
                        "reservationId",
                        reservation.reservationId(),
                        "localSnIds",
                        locals,
                        "identityIds",
                        identities));
        events.finish(command, Map.of("localSnIds", locals));
        return locals;
    }

    /**
     * 旧批次绑定入口只设置成品自身批次；组件实际用料需通过用量分配记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void attachProductLot(Long snId, Long lotId) {
        context.requireWrite();
        String tenant = context.tenant();
        var hint =
                one(
                        jdbc,
                        "SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'",
                        tenant,
                        snId);
        need(hint != null, "SN不存在");
        if (positive(id(hint.get("mes_wo_id")))) {
            need(
                    one(
                                    jdbc,
                                    "SELECT mes_wo_id FROM pro_work_order WHERE tenant_id=? AND"
                                            + " mes_wo_id=? AND del_flag='0' FOR UPDATE",
                                    tenant,
                                    hint.get("mes_wo_id"))
                            != null,
                    "SN实际工单不存在");
        } else {
            need(
                    "SUBCONTRACT".equals(hint.get("source"))
                            && events.subcontractSource(id(hint.get("source_id"))) != null,
                    "无工单SN必须有真实委外来源");
        }
        var lot =
                one(
                        jdbc,
                        "SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        lotId);
        var sn =
                one(
                        jdbc,
                        "SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant,
                        snId);
        need(
                sn != null
                        && positive(id(sn.get("identity_id")))
                        && lot != null
                        && Objects.equals(id(sn.get("material_id")), id(lot.get("material_id"))),
                "成品批次与正式SN物料不匹配");
        if (Objects.equals(lotId, id(sn.get("product_lot_id")))) {
            return;
        }
        need(
                sn.get("product_lot_id") == null
                        && "IN_PRODUCTION".equals(sn.get("sn_status"))
                        && "NONE".equals(sn.get("handoff_status")),
                "成品批次已确定或交付已冻结，须通过业务纠正保留原凭据");
        long count =
                jdbc.queryForList(
                                "SELECT sn_id FROM pro_sn WHERE tenant_id=? AND product_lot_id=?"
                                        + " AND del_flag='0' FOR UPDATE",
                                tenant,
                                lotId)
                        .size();
        need(
                BigDecimal.valueOf(count + 1).compareTo(qty(lot.get("quantity"))) <= 0,
                "成品批次SN分配超过实际批次数量");
        var command =
                events.command(
                        "PRODUCT_LOT:" + snId, "PRODUCT_LOT_ASSIGNMENT", List.of(snId, lotId));
        need(
                jdbc.update(
                                "UPDATE pro_sn SET product_lot_id=? WHERE tenant_id=? AND sn_id=?"
                                        + " AND product_lot_id IS NULL AND handoff_status='NONE'",
                                lotId,
                                tenant,
                                snId)
                        == 1,
                "成品批次已并发变化");
        events.append(
                "PRODUCT_LOT_ASSIGNED",
                "PRODUCT_LOT_ASSIGNMENT",
                snId,
                1,
                Map.of(
                        "localSnId",
                        snId,
                        "identityId",
                        sn.get("identity_id"),
                        "productLotId",
                        lotId));
        events.finish(command, Map.of("productLotId", lotId));
    }
}

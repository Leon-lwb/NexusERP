package com.nexus.mes.trace;

import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.mes.integration.trace.MesWorkOrderTrackingPolicyReader;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

import java.util.*;

/**
 * 冻结策略属于单次来源事务，必须在首次写入或业务行锁之前准备。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesPreparedWorkOrderPolicies {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    private final MesWorkOrderTrackingPolicyReader reader;

    @Value("${nexus.trace.relay.enabled:false}")
    private boolean traceRelayEnabled;

    /**
     * 制造工单、库存原工单、产品物料及冻结策略的一致性快照。
     *
     * @param woId 制造模块本地工单主键。
     * @param erpWoId ERP 生产工单主键。
     * @param materialId 当前业务行实际物料标识。
     * @param policy 来源业务已冻结的追踪策略。
     */
    private record Root(Long woId, Long erpWoId, Long materialId, TrackingPolicy policy) {}

    /**
     * 当前事务的策略准备上下文；首次业务写入后禁止继续追加跨库准备读取。
     */
    private static final class Frame implements TransactionSynchronization {
        String tenant;
        boolean started;
        final Map<Long, Root> roots = new HashMap<>();
        final Map<Long, Long> cards = new HashMap<>();
    }

    /**
     * 返回当前是否启用新的追踪交付路径。
     *
     * @return 上述校验或业务条件是否成立。
     */
    public boolean enabled() {
        return traceRelayEnabled;
    }

    /**
     * 首次制造写入前读取真实工单与库存原档策略，准备后再进入业务行锁。
     *
     * @param woId 制造模块本地工单主键。
     */
    public void prepareWorkOrder(Long woId) {
        if (!enabled()) {
            return;
        }
        var frame = frame(context.tenant());
        if (frame.roots.containsKey(woId)) {
            return;
        }
        need(!frame.started, "工单冻结策略必须在MES行锁和命令写入之前读取");
        var row =
                one(
                        jdbc,
                        "SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND"
                                + " del_flag='0'",
                        context.tenant(),
                        woId);
        prepareRoot(frame, row);
    }

    /**
     * 按实际流转卡关联准备工单策略，卡与工单必须属于同一来源。
     *
     * @param cardId 当前工单下的合法流转卡标识。
     */
    public void prepareCard(Long cardId) {
        if (!enabled()) {
            return;
        }
        var frame = frame(context.tenant());
        if (frame.cards.containsKey(cardId)) {
            return;
        }
        need(!frame.started, "卡完工冻结策略必须在MES行锁和命令写入之前读取");
        var row =
                one(
                        jdbc,
                        """
                        SELECT w.* FROM pro_flow_card c JOIN pro_work_order w
                        ON CAST(w.tenant_id AS BINARY(80))=CAST(c.tenant_id AS BINARY(80)) AND w.mes_wo_id=c.mes_wo_id
                        WHERE c.tenant_id=? AND c.card_id=? AND c.del_flag='0' AND w.del_flag='0'
                        """,
                        context.tenant(),
                        cardId);
        Root root = prepareRoot(frame, row);
        frame.cards.put(cardId, root.woId());
    }

    private Root prepareRoot(Frame frame, Map<String, Object> row) {
        need(row != null && context.tenant().equals(text(row.get("tenant_id"))), "完工缺少真实同租户MES工单");
        Long woId = id(row.get("mes_wo_id")),
                erpWoId = id(row.get("erp_wo_id")),
                material = id(row.get("material_id"));
        need(positive(woId) && positive(material), "产品策略缺少真实工单或物料");
        var old = frame.roots.get(woId);
        if (old != null) {
            need(
                    Objects.equals(old.erpWoId(), erpWoId)
                            && Objects.equals(old.materialId(), material),
                    "预读后的工单来源已变化");
            return old;
        }
        boolean internal = "REWORK".equals(text(row.get("source_type")));
        need(internal || positive(erpWoId), "工单缺少真实ERP来源，不能构造冻结策略");
        TrackingPolicy policy = internal ? null : reader.requirePolicy(woId, material);
        need(internal || policy != null, "工单没有已核实的冻结策略");
        Root root = new Root(woId, erpWoId, material, policy);
        frame.roots.put(woId, root);
        return root;
    }

    /**
     * 读取已经准备的产品策略，工单、库存原单和物料必须保持一致。
     *
     * @param woId 制造模块本地工单主键。
     * @param erpWoId ERP 生产工单主键。
     * @param materialId 当前业务行实际物料标识。
     * @return 该实际业务来源对应的追踪策略。
     */
    public TrackingPolicy productPolicy(Long woId, Long erpWoId, Long materialId) {
        need(enabled(), "冻结策略读取只适用于新追踪交付");
        var frame = frame(context.tenant());
        Root root = frame.roots.get(woId);
        need(
                root != null
                        && root.policy() != null
                        && Objects.equals(root.erpWoId(), erpWoId)
                        && Objects.equals(root.materialId(), materialId),
                "产品冻结策略未在写事务开始前按实际ERP工单/物料预读");
        return root.policy();
    }

    /**
     * 在写入阶段核对流转卡仍属于先前准备的实际工单。
     *
     * @param cardId 当前工单下的合法流转卡标识。
     * @param woId 制造模块本地工单主键。
     */
    public void assertPreparedCard(Long cardId, Long woId) {
        if (!enabled()) {
            return;
        }
        var frame = frame(context.tenant());
        need(Objects.equals(woId, frame.cards.get(cardId)), "新过站命令缺少写前冻结策略准备");
    }

    /**
     * 按原命令准备流转卡来源策略，重放优先核验原不可变请求。
     *
     * @param cardId 当前工单下的合法流转卡标识。
     * @param type 由业务入口固定的操作或来源类型。
     * @param command 本次业务命令号，重试时保持原值。
     */
    public void prepareCardForCommand(Long cardId, String type, String command) {
        if (enabled() && applied(type, command)) {
            return;
        }
        prepareCard(cardId);
    }

    /**
     * 按原命令准备工单来源策略，避免在业务行锁内执行跨库查询。
     *
     * @param woId 制造模块本地工单主键。
     * @param type 由业务入口固定的操作或来源类型。
     * @param command 本次业务命令号，重试时保持原值。
     */
    public void prepareWorkOrderForCommand(Long woId, String type, String command) {
        if (enabled() && applied(type, command)) {
            return;
        }
        prepareWorkOrder(woId);
    }

    /**
     * 在工位逐件作业开始前准备真实流转卡及冻结来源策略。
     *
     * @param cardId 当前工单下的合法流转卡标识。
     */
    public void prepareStationSingle(Long cardId, String clientKey) {
        if (!enabled()) {
            return;
        }
        if (one(
                        jdbc,
                        "SELECT report_id FROM mes_station_report WHERE tenant_id=? AND"
                                + " client_key=? LIMIT 1",
                        context.tenant(),
                        clientKey)
                != null) {
            return;
        }
        prepareCard(cardId);
    }

    /**
     * 读取真实工单组件的原档策略，用于后续本地实际消耗。
     *
     * @param type 由业务入口固定的操作或来源类型。
     * @param command 本次业务命令号，重试时保持原值。
     * @param woId 制造模块本地工单主键。
     * @param materialId 当前业务行实际物料标识。
     * @return 该实际业务来源对应的追踪策略。
     */
    public TrackingPolicy componentForWorkOrder(
            String type, String command, Long woId, Long materialId) {
        context.requireNoTransaction();
        need(enabled(), "组件冻结策略需要启用trace relay");
        if (applied(type, command)) {
            return null;
        }
        return reader.requirePolicy(woId, materialId);
    }

    /**
     * 按原实际消耗定位组件及其冻结策略。
     *
     * @param command 本次业务命令号，重试时保持原值。
     * @param consumptionId 已经发生的工单实际消耗记录。
     * @return 该实际业务来源对应的追踪策略。
     */
    public TrackingPolicy componentForConsumption(String command, Long consumptionId) {
        context.requireNoTransaction();
        need(enabled(), "组件冻结策略需要启用trace relay");
        if (applied("SN_CONSUMPTION", command)) {
            return null;
        }
        var row =
                one(
                        jdbc,
                        "SELECT wo_id,material_id FROM lt_wo_consumption WHERE tenant_id=? AND"
                                + " consumption_id=? AND del_flag='0'",
                        context.tenant(),
                        consumptionId);
        need(row != null, "逐件分配缺少真实实际消耗来源");
        return reader.requirePolicy(id(row.get("wo_id")), id(row.get("material_id")));
    }

    /**
     * 按真实产品与组件定位原档策略，不能从本地批次码推导单件身份。
     *
     * @param command 本次业务命令号，重试时保持原值。
     * @param materialId 当前业务行实际物料标识。
     * @return 该实际业务来源对应的追踪策略。
     */
    public TrackingPolicy componentForSn(String command, Long snId, Long materialId) {
        context.requireNoTransaction();
        need(enabled(), "组件冻结策略需要启用trace relay");
        if (applied("SN_COMPONENT_CORRECTION", command)) {
            return null;
        }
        var row =
                one(
                        jdbc,
                        "SELECT mes_wo_id FROM pro_sn WHERE tenant_id=? AND sn_id=? AND"
                                + " del_flag='0'",
                        context.tenant(),
                        snId);
        need(row != null, "组件更正缺少真实同租户MES身份");
        return reader.requirePolicy(id(row.get("mes_wo_id")), materialId);
    }

    /**
     * 此处只提供重放线索；写入器仍须核验原不可变请求后才返回旧结果。
     */
    private boolean applied(String type, String command) {
        MesTraceSql.command(command);
        String tenant = context.tenant(), eventId = TraceHashes.fingerprint(tenant, type, command);
        return one(
                        jdbc,
                        "SELECT event_id FROM mes_trace_inbox WHERE tenant_id=? AND event_id=? AND"
                                + " source_module='MES' AND event_type=? AND aggregate_type=? AND"
                                + " status='APPLIED'",
                        tenant,
                        eventId,
                        type,
                        "COMMAND_" + type)
                != null;
    }

    private static Frame current() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
            if (sync instanceof Frame frame) {
                return frame;
            }
        }
        return null;
    }

    private static Frame frame(String tenant) {
        need(
                TransactionSynchronizationManager.isActualTransactionActive()
                        && TransactionSynchronizationManager.isSynchronizationActive(),
                "产品策略准备需要实际MES源事务");
        Frame frame = current();
        if (frame == null) {
            frame = new Frame();
            TransactionSynchronizationManager.registerSynchronization(frame);
        }
        need(frame.tenant == null || frame.tenant.equals(tenant), "策略准备范围不能跨租户");
        frame.tenant = tenant;
        return frame;
    }

    /**
     * 标记本事务已经开始业务写入，后续准备请求不得再跨库读取。
     */
    public static void markWriteStarted() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        Frame frame = current();
        if (frame == null) {
            frame = new Frame();
            TransactionSynchronizationManager.registerSynchronization(frame);
        }
        frame.started = true;
    }
}

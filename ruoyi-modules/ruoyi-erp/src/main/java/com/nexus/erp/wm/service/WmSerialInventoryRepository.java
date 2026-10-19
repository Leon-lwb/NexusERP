package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import java.math.*;
import java.util.*;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/**
 * 全部SQL使用ERP事务连接；标识符只来自固定来源目录。此类不发布跨库MQ。
 */
@Repository
@DS("erp")
@RequiredArgsConstructor
public class WmSerialInventoryRepository {
    final JdbcTemplate jdbc;
    private final WmMaterialStockMapper stocks;
    private final WmMaterialStockTxMapper transactions;
    private final ErpTraceContext context;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    /**
     * 库存实物身份及其物料、批次和状态快照。
     *
     * @param id ERP 序列号主档主键，也是跨模块稳定实物身份。
     * @param materialId 当前业务行实际物料标识。
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param code 该身份在 ERP 登记的正式单件编码。
     * @param status 正式实物身份当前的业务状态。
     */
    public record Identity(Long id, Long materialId, Long batchId, String code, String status) {}

    /**
     * 保留原库存计价范围的数量与金额快照。
     *
     * @param id 保留原计价范围的计价组主键。
     * @param materialId 当前业务行实际物料标识。
     * @param warehouseId 业务所属仓库标识。
     * @param batchId 库存批次身份；与制造子批次标识分别保存。
     * @param sourceStockId 原库存行引用，保留领用或移库时的真实来源。
     * @param quantity 当前计价组的库存结存数量。
     * @param amount 所属计价范围的总金额。
     * @param unitCost 原计价策略确定的单位成本。
     */
    public record Pool(
            Long id,
            Long materialId,
            Long warehouseId,
            Long batchId,
            Long sourceStockId,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal unitCost) {}

    /**
     * 同一原单行及版本对应的完整过账证明。
     *
     * @param id 完整过账组主键。
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param detailVersion 单据实际身份明细版本。
     * @param fingerprint 原请求内容指纹，同命令不同内容必须拒绝。
     * @param contentHash 按稳定顺序计算的完整内容摘要。
     * @param quantity 该组已经过账的合计数量。
     * @param amount 所属计价范围的总金额。
     * @param status 过账组的准备或已过账状态。
     * @param valuationGroupId 保留原计价范围的成本组标识。
     */
    public record Group(
            Long id,
            String commandId,
            long detailVersion,
            String fingerprint,
            String contentHash,
            BigDecimal quantity,
            BigDecimal amount,
            String status,
            Long valuationGroupId) {}

    /**
     * 准备身份明细时读取的原单内容摘要、追踪策略与可用修订。
     *
     * @param detailVersion 当前原单行及策略的内容摘要，用于识别明细冻结依据。
     * @param policy 来源业务已冻结的追踪策略。
     * @param revision 当前可用的单据身份明细修订号。
     */
    public record Snapshot(String detailVersion, TrackingPolicy policy, long revision) {}

    /**
     * 由服务端固定的原单表、主键及数量字段配置。
     *
     * @param head 服务端固定的原单表名。
     * @param headId 原单表的主键字段。
     * @param line 服务端固定的原单行表名。
     * @param lineId 原单行表的主键字段名。
     * @param parent 原单行指向单头的固定字段。
     * @param quantity 原单行表的数量字段名。
     */
    record Spec(
            String head,
            String headId,
            String line,
            String lineId,
            String parent,
            String quantity) {}

    private static final Map<String, Spec> SOURCES =
            Map.ofEntries(
                    Map.entry(
                            "WM_SALES_ISSUE",
                            new Spec(
                                    "wm_sales_issue",
                                    "issue_id",
                                    "wm_sales_issue_line",
                                    "line_id",
                                    "issue_id",
                                    "quantity_issue")),
                    Map.entry(
                            "WM_PRODUCTION_ISSUE",
                            new Spec(
                                    "wm_production_issue",
                                    "issue_id",
                                    "wm_production_issue_line",
                                    "line_id",
                                    "issue_id",
                                    "current_qty")),
                    Map.entry(
                            "WM_MATERIAL_RECEIVE",
                            new Spec(
                                    "wm_material_receive",
                                    "receive_id",
                                    "wm_material_receive_line",
                                    "line_id",
                                    "receive_id",
                                    "quantity_received")),
                    Map.entry(
                            "WM_MISC_ISSUE",
                            new Spec(
                                    "wm_misc_issue",
                                    "issue_id",
                                    "wm_misc_issue_line",
                                    "line_id",
                                    "issue_id",
                                    "quantity")),
                    Map.entry(
                            "WM_MISC_RECPT",
                            new Spec(
                                    "wm_misc_recpt",
                                    "recpt_id",
                                    "wm_misc_recpt_line",
                                    "line_id",
                                    "recpt_id",
                                    "quantity")),
                    Map.entry(
                            "WM_TRANSFER",
                            new Spec(
                                    "wm_transfer",
                                    "transfer_id",
                                    "wm_transfer_line",
                                    "line_id",
                                    "transfer_id",
                                    "quantity")),
                    Map.entry(
                            "WM_STOCK_TAKING",
                            new Spec(
                                    "wm_stock_taking_plan",
                                    "plan_id",
                                    "wm_stock_taking_line",
                                    "line_id",
                                    "plan_id",
                                    "difference_quantity")),
                    Map.entry(
                            "WM_MATERIAL_RETURN",
                            new Spec(
                                    "wm_material_return",
                                    "return_id",
                                    "wm_material_return_line",
                                    "line_id",
                                    "return_id",
                                    "actual_qty")),
                    Map.entry(
                            "SO_RETURN",
                            new Spec(
                                    "so_return",
                                    "return_id",
                                    "so_return_line",
                                    "line_id",
                                    "return_id",
                                    "return_quantity")),
                    Map.entry(
                            "PU_PURCHASE_RETURN",
                            new Spec(
                                    "pu_purchase_return",
                                    "return_id",
                                    "pu_purchase_return_line",
                                    "line_id",
                                    "return_id",
                                    "return_quantity")),
                    Map.entry(
                            "PRD_REWORK_ORDER",
                            new Spec(
                                    "prd_rework_order",
                                    "rework_id",
                                    "prd_rework_order",
                                    "rework_id",
                                    "rework_id",
                                    "rework_qty")),
                    Map.entry(
                            "PRD_REWORK_ORDER_RECEIVE",
                            new Spec(
                                    "prd_rework_order",
                                    "rework_id",
                                    "prd_rework_order",
                                    "rework_id",
                                    "rework_id",
                                    "actual_qty")),
                    Map.entry(
                            "WM_SUBCONTRACT_ISSUE",
                            new Spec(
                                    "wm_subcontract_issue",
                                    "issue_id",
                                    "wm_subcontract_issue_line",
                                    "line_id",
                                    "issue_id",
                                    "issue_qty")),
                    Map.entry(
                            "WM_SUBCONTRACT_RECEIVE",
                            new Spec(
                                    "wm_subcontract_receive",
                                    "receive_id",
                                    "wm_subcontract_receive",
                                    "receive_id",
                                    "receive_id",
                                    "received_qty")));

    public String tenant() {
        return context.tenant();
    }

    public void requireWrite() {
        context.requireWrite(tenant());
    }

    public Map<String, Object> row(String sql, Object... args) {
        var found = jdbc.queryForList(sql, args);
        need(found.size() == 1, "原始来源缺失或不唯一");
        return found.get(0);
    }

    static Long id(Object o) {
        return o == null
                ? null
                : o instanceof Boolean flag ? (flag ? 1L : 0L) : ((Number) o).longValue();
    }

    static BigDecimal decimal(Object o) {
        return o == null ? null : new BigDecimal(o.toString());
    }

    static boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }

    public TrackingPolicy policy(Long material) {
        var m =
                row(
                        "SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant(),
                        material);
        need("1".equals(String.valueOf(m.get("status"))), "物料未启用");
        return TrackingPolicy.of(
                id(m.get("is_batch_manage")),
                id(m.get("is_serial_manage")),
                m.get("allow_mixed_lot") == null || id(m.get("allow_mixed_lot")) == 1,
                id(m.get("tracking_version")),
                String.valueOf(m.getOrDefault("tracking_stage", "AUDIT")));
    }

    private Map<String, Object> head(WmSerialPostingRequest r) {
        if ("PRD_WORK_ORDER".equals(r.sourceType())) {
            return row(
                    "SELECT * FROM prd_work_order WHERE tenant_id=? AND wo_id=? AND del_flag='0'"
                            + " FOR UPDATE",
                    tenant(),
                    r.sourceId());
        }
        Spec s = SOURCES.get(r.sourceType());
        need(s != null, "单件库存来源类型未授权");
        return row(
                "SELECT * FROM "
                        + s.head()
                        + " WHERE tenant_id=? AND "
                        + s.headId()
                        + "=? FOR UPDATE",
                tenant(),
                r.sourceId());
    }

    private Map<String, Object> line(WmSerialPostingRequest r) {
        if ("PRD_WORK_ORDER".equals(r.sourceType())) {
            return head(r);
        }
        Spec s = SOURCES.get(r.sourceType());
        need(s != null, "库存原始来源类型无效");
        if (s.head().equals(s.line())) {
            return head(r);
        }
        need(r.lineKey() > 0, "库存原始来源行无效");
        return row(
                "SELECT * FROM "
                        + s.line()
                        + " WHERE tenant_id=? AND "
                        + s.lineId()
                        + "=? AND "
                        + s.parent()
                        + "=? FOR UPDATE",
                tenant(),
                r.sourceLineId(),
                r.sourceId());
    }

    public String sourceHash(WmSerialPostingRequest r) {
        Map<String, Object> h = head(r), l = line(r);
        // 状态认领/更新时间不改变来源承诺；数量、基本单位、父引用、仓储维度和审批来源均绑定。
        List<String> keys =
                List.of(
                        "material_id",
                        "material_code",
                        "base_uom_id",
                        "unit_id",
                        "quantity",
                        "quantity_issue",
                        "quantity_received",
                        "return_quantity",
                        "current_qty",
                        "issued_qty",
                        "short_qty",
                        "required_qty",
                        "book_quantity",
                        "actual_quantity",
                        "difference_quantity",
                        "actual_qty",
                        "batch_id",
                        "warehouse_id",
                        "area_id",
                        "location_id",
                        "from_warehouse_id",
                        "from_location_id",
                        "to_warehouse_id",
                        "to_area_id",
                        "to_location_id",
                        "so_id",
                        "client_id",
                        "source_doc_line_id",
                        "order_id",
                        "order_line_id",
                        "pur_id",
                        "purchase_line_id",
                        "notice_id",
                        "notice_line_id",
                        "wo_id",
                        "planned_qty",
                        "completed_qty",
                        "unit_price",
                        "return_type",
                        "source_doc",
                        "rework_qty",
                        "actual_qty",
                        "issue_qty",
                        "received_qty",
                        "target_warehouse_id",
                        "target_location_id",
                        "subcontract_id",
                        "bom_line_id",
                        "source_wo_id",
                        "iqc_id",
                        "receive_date");
        var facts = new TreeMap<String, String>();
        for (String k : keys) {
            if (h.containsKey(k)) {
                facts.put("h." + k, canonical(h.get(k)));
            }
            if (l.containsKey(k)) {
                facts.put("l." + k, canonical(l.get(k)));
            }
        }
        if ("WM_MATERIAL_RECEIVE".equals(r.sourceType())) {
            var details =
                    jdbc.queryForList(
                            "SELECT"
                                + " detail_id,line_id,material_id,base_uom_id,quantity,batch_id,warehouse_id,area_id,location_id,sn_id"
                                + " FROM wm_material_receive_detail WHERE tenant_id=? AND"
                                + " receive_id=? AND line_id=? AND del_flag='0' ORDER BY detail_id"
                                + " FOR UPDATE",
                            tenant(),
                            r.sourceId(),
                            r.sourceLineId());
            for (var detail : details) {
                for (var e : detail.entrySet()) {
                    facts.put(
                            "d." + detail.get("detail_id") + "." + e.getKey(),
                            canonical(e.getValue()));
                }
            }
        }
        return TraceHashes.fingerprint(tenant(), r.sourceType(), r.sourceId(), r.lineKey(), facts);
    }

    static String canonical(Object v) {
        return v instanceof BigDecimal d
                ? d.stripTrailingZeros().toPlainString()
                : String.valueOf(v);
    }

    /**
     * 按固定来源目录锁定原单及业务行，禁止通过传入表名绕过来源守卫。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     */
    public void lockSource(WmSerialPostingRequest r) {
        need(
                r != null
                        && r.sourceId() != null
                        && r.sourceId() > 0
                        && r.materialId() != null
                        && r.quantity() != null
                        && r.quantity().signum() > 0,
                "过账来源与数量无效");
        var h = head(r);
        var l = line(r);
        need(
                (!h.containsKey("del_flag") || "0".equals(h.get("del_flag")))
                        && (!l.containsKey("del_flag") || "0".equals(l.get("del_flag")))
                        && same(r.materialId(), id(l.get("material_id"))),
                "过账来源已删除或物料漂移");
        String status = String.valueOf(h.get("status"));
        need(
                !Set.of("CANCELLED", "CLOSED", "REJECTED", "APPROVING").contains(status),
                "原单据状态不能授权库存效果");
        if ("PRD_WORK_ORDER".equals(r.sourceType())) {
            need(
                    Set.of("COMPLETED", "CLOSED").contains(String.valueOf(h.get("wo_status")))
                            && h.get("actual_end_date") != null,
                    "工单未真实最终完工");
            var receipts =
                    jdbc.queryForList(
                            "SELECT * FROM wm_production_recpt WHERE tenant_id=? AND wo_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant(),
                            r.sourceId());
            need(receipts.size() <= 1, "同工单不能创建第二张最终入库单");
            if (!receipts.isEmpty()) {
                need(
                        decimal(receipts.get(0).get("quantity")).compareTo(r.quantity()) == 0
                                && same(r.materialId(), id(receipts.get(0).get("material_id"))),
                        "原最终入库单数量或物料不符");
            }
            return;
        }
        Spec s = SOURCES.get(r.sourceType());
        BigDecimal qty = decimal(l.get(s.quantity()));
        if ("WM_PRODUCTION_ISSUE".equals(r.sourceType())
                && ("BACKFLUSH".equals(h.get("issue_type")) || "BACKFLUSH".equals(h.get("status")))
                && (qty == null || qty.signum() == 0)) {
            qty = decimal(l.get("quantity"));
        }
        if ("WM_STOCK_TAKING".equals(r.sourceType())) {
            qty = decimal(l.get("actual_quantity")).subtract(decimal(l.get("book_quantity")));
        }
        if (qty != null && "WM_STOCK_TAKING".equals(r.sourceType())) {
            qty = qty.abs();
        }
        need(qty != null && qty.compareTo(r.quantity()) == 0, "本次过账数量与锁内原始单据行不一致");
        for (String k : List.of("warehouse_id", "location_id", "batch_id")) {
            Object expected = l.get(k) != null ? l.get(k) : h.get(k);
            Long actual =
                    switch (k) {
                        case "warehouse_id" -> r.warehouseId();
                        case "location_id" -> r.locationId();
                        default -> r.batchId();
                    };
            if (expected != null && actual != null) {
                need(same(id(expected), actual), "库存来源仓储/批次维度不符");
            }
        }
        if ("WM_SALES_ISSUE".equals(r.sourceType())) {
            need(Set.of("CONFIRMED", "EXECUTING", "COMPLETED").contains(status), "销售出库原单未经确认");
            if (h.get("so_id") != null) {
                var so =
                        row(
                                "SELECT * FROM so_sales_order_line WHERE tenant_id=? AND so_id=?"
                                        + " AND line_id=? AND del_flag='0' FOR UPDATE",
                                tenant(),
                                h.get("so_id"),
                                l.get("source_doc_line_id"));
                need(
                        same(id(so.get("material_id")), r.materialId())
                                && "QUALIFIED".equals(so.get("oqc_status")),
                        "原销售行或出货检验不符");
            }
        }
        if ("SO_RETURN".equals(r.sourceType())) {
            need(
                    Set.of("CONFIRMED", "RECEIVED").contains(status)
                            && h.get("so_id") != null
                            && l.get("so_line_id") != null,
                    "销售退货缺少已确认原始订单来源");
            var so =
                    row(
                            "SELECT * FROM so_sales_order_line WHERE tenant_id=? AND so_id=? AND"
                                    + " line_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            h.get("so_id"),
                            l.get("so_line_id"));
            need(same(id(so.get("material_id")), r.materialId()), "销售退货物料不属于原始订单行");
            var order =
                    row(
                            "SELECT client_id FROM so_sales_order_header WHERE tenant_id=? AND"
                                    + " so_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            h.get("so_id"));
            need(same(h.get("client_id"), order.get("client_id")), "销售退货客户不属于原订单客户");
        }
        if ("PU_PURCHASE_RETURN".equals(r.sourceType())) {
            need(
                    Set.of("CONFIRMED", "EXECUTED").contains(status)
                            && h.get("order_id") != null
                            && l.get("order_line_id") != null,
                    "采购退货缺少原始采购行");
            var po =
                    row(
                            "SELECT * FROM pu_purchase_order_line WHERE tenant_id=? AND order_id=?"
                                    + " AND line_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            h.get("order_id"),
                            l.get("order_line_id"));
            need(same(id(po.get("material_id")), r.materialId()), "采购退货物料与原始采购行不符");
        }
    }

    public WmSerialPostingRequest request(String type, Long source, Long lineId) {
        var probe =
                WmSerialPostingRequest.out(
                        type, source, lineId, "", 1L, BigDecimal.ONE, null, null, null);
        var h = head(probe);
        var l = line(probe);
        Spec spec = SOURCES.get(type);
        need(spec != null, "该来源需使用专用最终入库接口");
        BigDecimal qty = decimal(l.get(spec.quantity()));
        if ("WM_STOCK_TAKING".equals(type)
                && l.get("actual_quantity") != null
                && l.get("book_quantity") != null) {
            qty = decimal(l.get("actual_quantity")).subtract(decimal(l.get("book_quantity")));
        }
        if (qty == null || qty.signum() == 0) {
            qty = decimal(l.get("quantity"));
        }
        need(qty != null && qty.signum() != 0, "原单据尚无可过账数量");
        Long warehouse =
                id(l.get("warehouse_id") != null ? l.get("warehouse_id") : h.get("warehouse_id"));
        Long location = id(l.get("location_id"));
        Long batch = id(l.get("batch_id"));
        Long material = id(l.get("material_id"));
        String tx =
                switch (type) {
                    case "WM_SALES_ISSUE" -> "SALES_OUT";
                    case "WM_PRODUCTION_ISSUE" -> "PRODUCTION_ISSUE";
                    case "WM_MATERIAL_RECEIVE" -> "RECEIVE_IN";
                    case "WM_MISC_ISSUE" -> "MISC_ISSUE";
                    case "WM_MISC_RECPT" -> "MISC_RECPT";
                    case "WM_TRANSFER" -> "TRANSFER";
                    case "WM_STOCK_TAKING" -> "COUNT_ADJUST";
                    case "WM_MATERIAL_RETURN" ->
                            "SCRAP".equals(h.get("return_type")) ? "SCRAP" : "RETURN_IN";
                    case "SO_RETURN" -> "RETURN_IN";
                    case "PU_PURCHASE_RETURN" -> "PUR_RETURN_OUT";
                    case "PRD_REWORK_ORDER" -> "REWORK_ISSUE";
                    case "PRD_REWORK_ORDER_RECEIVE" -> "REWORK_RECEIVE";
                    case "WM_SUBCONTRACT_ISSUE" -> "SUBCONTRACT_ISSUE";
                    case "WM_SUBCONTRACT_RECEIVE" -> "SUBCONTRACT_RECEIVE";
                    default -> throw new ServiceException("来源未支持");
                };
        boolean incoming =
                Set.of(
                                        "WM_MATERIAL_RECEIVE",
                                        "WM_MISC_RECPT",
                                        "WM_MATERIAL_RETURN",
                                        "SO_RETURN",
                                        "PRD_REWORK_ORDER_RECEIVE",
                                        "WM_SUBCONTRACT_RECEIVE")
                                .contains(type)
                        || "WM_STOCK_TAKING".equals(type) && qty.signum() > 0;
        if ("WM_SUBCONTRACT_RECEIVE".equals(type)) {
            warehouse = id(h.get("target_warehouse_id"));
            location = id(h.get("target_location_id"));
        }
        var r =
                incoming
                        ? WmSerialPostingRequest.in(
                                type, source, lineId, tx, material, qty.abs(), warehouse, location,
                                batch, null)
                        : WmSerialPostingRequest.out(
                                type, source, lineId, tx, material, qty.abs(), warehouse, location,
                                batch);
        if ("WM_TRANSFER".equals(type)) {
            r =
                    WmSerialPostingRequest.out(
                                    type,
                                    source,
                                    lineId,
                                    tx,
                                    material,
                                    qty,
                                    id(h.get("from_warehouse_id")),
                                    id(l.get("from_location_id")),
                                    batch)
                            .transfer(
                                    id(h.get("to_warehouse_id")),
                                    id(h.get("to_area_id")),
                                    id(l.get("to_location_id")));
        }
        return r;
    }

    /**
     * 读取当前原单行与物料策略，计算用于冻结明细的内容摘要和可用修订。
     *
     * @param type 由业务入口固定的操作或来源类型。
     * @param lineId 原单业务行主键。
     * @return 当前原单内容摘要、追踪策略及下一次可用的明细修订。
     */
    public Snapshot snapshot(String type, Long source, Long lineId) {
        var probe =
                WmSerialPostingRequest.out(
                        type, source, lineId, "", 1L, BigDecimal.ONE, null, null, null);
        var l = line(probe);
        var p = policy(id(l.get("material_id")));
        String hash = sourceHash(probe);
        long revision = 1;
        if (p.stage() == TrackingStage.STRICT && p.serialManaged()) {
            var groups =
                    jdbc.queryForList(
                            "SELECT detail_version,status FROM wm_trace_posting_group WHERE"
                                + " tenant_id=? AND source_doc_type=? AND source_doc_id=? AND"
                                + " source_doc_line_id=? ORDER BY detail_version DESC LIMIT 1 FOR"
                                + " UPDATE",
                            tenant(),
                            type,
                            source,
                            lineId);
            if (!groups.isEmpty()) {
                revision =
                        id(groups.get(0).get("detail_version"))
                                + ("POSTED".equals(groups.get(0).get("status")) ? 1 : 0);
            }
        }
        return new Snapshot(hash, p, revision);
    }

    public BigDecimal consumedReservation(WmSerialPostingRequest r) {
        var g = group(r);
        if (g == null) {
            return BigDecimal.ZERO;
        }
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(tx_quantity),0) FROM wm_material_stock_tx WHERE tenant_id=?"
                        + " AND posting_group_id=? AND tx_type='CONSUME_RESERVE' AND del_flag='0'",
                BigDecimal.class,
                tenant(),
                g.id());
    }

    public void reduceSalesReservation(WmSerialPostingRequest r, BigDecimal consumed) {
        if (consumed.signum() > 0) {
            one(
                    jdbc.update(
                            "UPDATE so_sales_order_line SET reserved_qty=reserved_qty-? WHERE"
                                + " tenant_id=? AND so_id=? AND line_id=? AND reserved_qty>=? AND"
                                + " del_flag='0'",
                            consumed,
                            tenant(),
                            r.reserveOrderId(),
                            r.reserveLineId(),
                            consumed));
        }
    }

    public void requirePostingState(WmSerialPostingRequest r) {
        var h = head(r);
        String state = String.valueOf(h.get("status"));
        if ("PRD_WORK_ORDER".equals(r.sourceType())) {
            var receipt =
                    row(
                            "SELECT status FROM wm_production_recpt WHERE tenant_id=? AND wo_id=?"
                                    + " AND del_flag='0' FOR UPDATE",
                            tenant(),
                            r.sourceId());
            need(
                    Set.of("EXECUTING", "EXECUTED").contains(String.valueOf(receipt.get("status"))),
                    "生产入库必须从唯一原收货执行入口进入");
            return;
        }
        Set<String> allowed =
                switch (r.sourceType()) {
                    case "WM_SALES_ISSUE" -> Set.of("EXECUTING", "COMPLETED");
                    case "WM_MATERIAL_RECEIVE" -> Set.of("EXECUTING", "SHELVED", "FINISHED");
                    case "WM_PRODUCTION_ISSUE" ->
                            Set.of("EXECUTING", "BACKFLUSH", "PARTIAL", "COMPLETED", "EXECUTED");
                    case "WM_MISC_ISSUE", "WM_MISC_RECPT" -> Set.of("EXECUTING", "EXECUTED");
                    case "WM_TRANSFER" -> Set.of("EXECUTING", "EXECUTED", "COMPLETED");
                    case "WM_STOCK_TAKING" -> Set.of("POSTING", "POSTED");
                    case "WM_MATERIAL_RETURN" -> Set.of("DRAFT", "COMPLETED");
                    case "SO_RETURN" -> Set.of("RECEIVED");
                    case "PU_PURCHASE_RETURN" -> Set.of("EXECUTED");
                    case "PRD_REWORK_ORDER", "PRD_REWORK_ORDER_RECEIVE" -> Set.of("IN_PROGRESS");
                    case "WM_SUBCONTRACT_ISSUE", "WM_SUBCONTRACT_RECEIVE" ->
                            Set.of("EXECUTING", "EXECUTED");
                    default -> Set.of();
                };
        need(allowed.contains(state), "原单据尚未进入实际执行状态，不能绕过原执行门禁");
    }

    /**
     * 加锁读取原单行及版本对应的过账组，重放使用该组的不可变证明。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @return 当前原单行的最新过账组；没有记录时返回空。
     */
    public Group group(WmSerialPostingRequest r) {
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND"
                            + " source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? AND"
                            + " tx_type=? ORDER BY detail_version DESC FOR UPDATE",
                        tenant(),
                        r.sourceType(),
                        r.sourceId(),
                        r.lineKey(),
                        r.txType());
        if (rows.isEmpty()) {
            return null;
        }
        var g = rows.get(0);
        return new Group(
                id(g.get("posting_group_id")),
                g.get("command_id").toString(),
                id(g.get("detail_version")),
                g.get("fingerprint").toString(),
                g.get("content_hash").toString(),
                decimal(g.get("quantity")),
                decimal(g.get("amount")),
                g.get("status").toString(),
                id(g.get("valuation_group_id")));
    }

    public long frozenPolicyVersion(Long group) {
        var original =
                row(
                        "SELECT policy_version FROM wm_trace_posting_group WHERE tenant_id=? AND"
                                + " posting_group_id=? AND status='POSTED' FOR UPDATE",
                        tenant(),
                        group);
        Long version = id(original.get("policy_version"));
        need(version != null && version > 0, "原已过账组缺少可信策略版本");
        return version;
    }

    /**
     * 按稳定行序读取指定过账组的全部逐件明细。
     *
     * @return 该过账组按稳定身份排序的完整单件明细。
     */
    public List<SnItem> items(Long group) {
        return jdbc.query(
                "SELECT * FROM wm_document_sn WHERE tenant_id=? AND posting_group_id=? ORDER BY"
                        + " identity_id",
                (rs, n) ->
                        new SnItem(
                                rs.getLong("identity_id"),
                                nullable(rs, "source_stock_id"),
                                nullable(rs, "target_location_id"),
                                nullable(rs, "original_tx_id"),
                                nullable(rs, "batch_id")),
                tenant(),
                group);
    }

    static Long nullable(java.sql.ResultSet rs, String key) throws java.sql.SQLException {
        long v = rs.getLong(key);
        return rs.wasNull() ? null : v;
    }

    /**
     * 在当前库存事务内创建过账组并持久化逐件选择，保留原请求及内容摘要。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param command 本次过账的原业务命令号。
     * @param revision 本次冻结的单据身份明细修订号。
     * @param fingerprint 原请求内容指纹，同命令不同内容必须拒绝。
     * @param hash 原冻结内容的摘要。
     * @param items 业务行已确认的实际明细集合。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    public Long prepare(
            WmSerialPostingRequest r,
            String command,
            long revision,
            String fingerprint,
            String hash,
            List<SnItem> items) {
        long gid = IdWorker.getId();
        long policy = policy(r.materialId()).version();
        String source = sourceHash(r);
        one(
                jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_posting_group(posting_group_id,tenant_id,command_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,tx_type,material_id,quantity,amount,fingerprint,content_hash,source_hash,policy_version,status)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,'PREPARED')",
                        gid,
                        tenant(),
                        command,
                        r.sourceType(),
                        r.sourceId(),
                        r.lineKey(),
                        revision,
                        r.txType(),
                        r.materialId(),
                        r.quantity(),
                        fingerprint,
                        hash,
                        source,
                        policy));
        int line = 0;
        for (var i : items.stream().sorted(Comparator.comparing(SnItem::identityId)).toList()) {
            one(
                    jdbc.update(
                            "INSERT INTO"
                                + " wm_document_sn(document_sn_id,tenant_id,command_id,line_no,source_doc_type,source_doc_id,source_doc_line_id,detail_version,identity_id,material_id,batch_id,source_stock_id,target_location_id,original_tx_id,posting_group_id,quantity)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)",
                            IdWorker.getId(),
                            tenant(),
                            command,
                            ++line,
                            r.sourceType(),
                            r.sourceId(),
                            r.lineKey(),
                            revision,
                            i.identityId(),
                            r.materialId(),
                            i.batchId(),
                            i.sourceStockId(),
                            i.targetLocationId(),
                            i.originalTxId(),
                            gid));
        }
        return gid;
    }

    /**
     * 核对冻结后原单、版本及业务数量，避免使用已经变化的来源执行旧选择。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param version 当前事实或规则版本。
     */
    public void validateFrozenSource(WmSerialPostingRequest r, Group g, long version) {
        var frozen =
                row(
                        "SELECT source_hash,policy_version FROM wm_trace_posting_group WHERE"
                                + " tenant_id=? AND posting_group_id=? FOR UPDATE",
                        tenant(),
                        g.id());
        need(
                sourceHash(r).equals(frozen.get("source_hash"))
                        && same(version, id(frozen.get("policy_version"))),
                "原始来源或追踪策略已改变，请重新核实单据");
    }

    /**
     * 读取并锁定当前租户正式身份，后续位置与状态更新复用同一行锁。
     *
     * @param sn 实际单件身份或本地单件记录。
     * @return 已锁定正式身份的物料、批次、编码和状态快照。
     */
    public Identity identity(Long sn) {
        var r =
                row(
                        "SELECT * FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR"
                                + " UPDATE",
                        tenant(),
                        sn);
        return new Identity(
                sn,
                id(r.get("material_id")),
                id(r.get("batch_id")),
                String.valueOf(r.get("sn_code")),
                String.valueOf(r.get("status")));
    }

    /**
     * 读取并锁定原库存行，保留真实仓储位置与计价引用。
     *
     * @param stock 已核实来源与位置的实际库存记录。
     * @return 已锁定的原库存行及其数量、位置和成本。
     */
    public WmMaterialStock stock(Long stock) {
        var rows =
                jdbc.query(
                        "SELECT * FROM wm_material_stock WHERE tenant_id=? AND stock_id=? FOR"
                                + " UPDATE",
                        BeanPropertyRowMapper.newInstance(WmMaterialStock.class),
                        tenant(),
                        stock);
        need(rows.size() == 1, "原分配库存不存在");
        return rows.get(0);
    }

    public List<Long> receiveLineIds(Long receive) {
        return jdbc.queryForList(
                "SELECT line_id FROM wm_material_receive_line WHERE tenant_id=? AND receive_id=?"
                        + " AND del_flag='0' ORDER BY line_id FOR UPDATE",
                Long.class,
                tenant(),
                receive);
    }

    /**
     * 出库前核验实际身份、原库存及来源凭据，阻止挪用其它单据的实物。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param item 当前实际业务行或单件明细。
     * @param stock 已核实来源与位置的实际库存记录。
     */
    public void validateOutgoingOrigin(
            WmSerialPostingRequest r, SnItem item, WmMaterialStock stock) {
        if (!"PU_PURCHASE_RETURN".equals(r.sourceType())) {
            return;
        }
        need(item.originalTxId() != null, "STRICT采购退货必须引用同一正式实物的原采购入库流水");
        var tx =
                row(
                        "SELECT * FROM wm_material_stock_tx WHERE tenant_id=? AND tx_id=? AND"
                                + " sn_id=? AND material_id=? AND del_flag='0' FOR UPDATE",
                        tenant(),
                        item.originalTxId(),
                        item.identityId(),
                        r.materialId());
        need(
                "WM_MATERIAL_RECEIVE".equals(tx.get("source_doc_type"))
                        && id(tx.get("tx_flag")) == 1
                        && id(tx.get("tracking_version")) == 2
                        && BigDecimal.ONE.compareTo(decimal(tx.get("tx_quantity"))) == 0,
                "采购退货原流水不是同件真实采购收货");
        var origin =
                row(
                        "SELECT"
                            + " h.pur_id,h.vendor_id,COALESCE(l.purchase_line_id,l.source_doc_line_id)"
                            + " AS purchase_line_id FROM wm_material_receive h JOIN"
                            + " wm_material_receive_line l ON l.tenant_id=h.tenant_id AND"
                            + " l.receive_id=h.receive_id WHERE h.tenant_id=? AND h.receive_id=?"
                            + " AND l.line_id=? AND h.del_flag='0' AND l.del_flag='0' FOR UPDATE",
                        tenant(),
                        tx.get("source_doc_id"),
                        tx.get("source_doc_line_id"));
        var h = head(r);
        var l = line(r);
        need(
                same(h.get("order_id"), origin.get("pur_id"))
                        && same(l.get("order_line_id"), origin.get("purchase_line_id")),
                "采购退货SN不是原采购订单行实际收到的实物");
        Long returned =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_material_stock_tx WHERE tenant_id=? AND"
                                + " related_tx_id=? AND tx_type='PUR_RETURN_OUT' AND del_flag='0'",
                        Long.class,
                        tenant(),
                        item.originalTxId());
        need(returned != null && returned == 0, "原采购入库实物已退货，禁止复用旧采购收货凭据");
    }

    /**
     * 核验库存行与原业务物料、批次和仓储来源一致。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param stock 已核实来源与位置的实际库存记录。
     */
    public void validateStockSource(WmSerialPostingRequest r, WmMaterialStock stock) {
        var h = head(r);
        var l = line(r);
        Long warehouse =
                id(l.get("warehouse_id") != null ? l.get("warehouse_id") : h.get("warehouse_id"));
        Long location = id(l.get("location_id"));
        Long batch = id(l.get("batch_id"));
        if ("WM_TRANSFER".equals(r.sourceType())) {
            warehouse = id(h.get("from_warehouse_id"));
            location = id(l.get("from_location_id"));
        }
        need(
                (warehouse == null || warehouse.equals(stock.getWarehouseId()))
                        && (location == null || location.equals(stock.getLocationId()))
                        && (batch == null || batch.equals(stock.getBatchId())),
                "选中实物不符合原单据仓库/储位/显式批次");
    }

    public List<WmMaterialStock> stocks(Long sn) {
        return jdbc.query(
                "SELECT * FROM wm_material_stock WHERE tenant_id=? AND sn_id=? ORDER BY stock_id"
                        + " FOR UPDATE",
                BeanPropertyRowMapper.newInstance(WmMaterialStock.class),
                tenant(),
                sn);
    }

    /**
     * 读取同一原计价范围内的库存集合，单件拆分保持该范围的成本含义。
     *
     * @return 已按稳定顺序锁定的同一计价组库存集合。
     */
    public List<WmMaterialStock> poolStocks(Long pool) {
        return jdbc.query(
                "SELECT * FROM wm_material_stock WHERE tenant_id=? AND valuation_group_id=? ORDER"
                        + " BY sn_id,stock_id FOR UPDATE",
                BeanPropertyRowMapper.newInstance(WmMaterialStock.class),
                tenant(),
                pool);
    }

    /**
     * 读取并锁定原计价范围的余额快照。
     *
     * @return 已锁定计价组的数量、金额和单位成本快照。
     */
    public Pool pool(Long pool) {
        var p =
                row(
                        "SELECT * FROM wm_valuation_group WHERE tenant_id=? AND"
                                + " valuation_group_id=? FOR UPDATE",
                        tenant(),
                        pool);
        return new Pool(
                pool,
                id(p.get("material_id")),
                id(p.get("warehouse_id")),
                id(p.get("batch_id")),
                id(p.get("source_stock_id")),
                decimal(p.get("quantity")),
                decimal(p.get("amount")),
                decimal(p.get("unit_cost")));
    }

    /**
     * 按原业务计价范围定位或建立入库成本组，保持供应商范围约束。
     *
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    public Long inboundPool(WmMaterialStock target, long policyVersion) {
        return inboundPool(target, policyVersion, true);
    }

    /**
     * 按原业务计价范围定位或建立入库成本组，保持供应商范围约束。
     *
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    public Long inboundPool(WmMaterialStock target, long policyVersion, boolean vendorScoped) {
        List<Object> params =
                new ArrayList<>(
                        Arrays.asList(
                                tenant(),
                                target.getMaterialId(),
                                target.getWarehouseId(),
                                target.getLocationId(),
                                target.getBatchId()));
        String vendor = "";
        if (vendorScoped && target.getVendorId() != null) {
            vendor = " AND vendor_id=?";
            params.add(target.getVendorId());
        }
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM wm_material_stock WHERE tenant_id=? AND material_id=? AND"
                                + " warehouse_id=? AND location_id <=> ? AND batch_id <=> ?"
                                + vendor
                                + " AND del_flag='0' ORDER BY stock_id FOR UPDATE",
                        params.toArray());
        Set<Long> groups = new HashSet<>();
        for (var s : rows) {
            Long g = id(s.get("valuation_group_id"));
            need(g != null && id(s.get("tracking_version")) >= 2, "原库存池尚未完成审计映射，禁止按SN重新定价");
            groups.add(g);
        }
        need(groups.size() <= 1, "原库存池映射冲突");
        if (!groups.isEmpty()) {
            return groups.iterator().next();
        }
        long g = IdWorker.getId();
        String snapshot =
                "{\"trackingVersion\":\"2\",\"materialId\":\""
                        + target.getMaterialId()
                        + "\",\"warehouseId\":\""
                        + target.getWarehouseId()
                        + "\",\"locationId\":\""
                        + target.getLocationId()
                        + "\",\"batchId\":\""
                        + target.getBatchId()
                        + "\",\"vendorId\":\""
                        + target.getVendorId()
                        + "\",\"workOrderId\":\""
                        + target.getWoId()
                        + "\",\"quantity\":\"0\",\"amount\":\"0.0000\"}";
        one(
                jdbc.update(
                        "INSERT INTO"
                            + " wm_valuation_group(valuation_group_id,tenant_id,material_id,warehouse_id,batch_id,source_stock_id,quantity,amount,unit_cost,valuation_method,policy_version,valuation_snapshot)"
                            + " VALUES(?,?,?,?,?,?,0,0,0,'MOVING_AVERAGE',?,?)",
                        g,
                        tenant(),
                        target.getMaterialId(),
                        target.getWarehouseId(),
                        target.getBatchId(),
                        null,
                        policyVersion,
                        snapshot));
        return g;
    }

    /**
     * 首次入库必须消费同一来源的正式身份；REGISTERED本身不证明生产已经交付。
     */
    public void validateInboundIdentitySource(
            WmSerialPostingRequest r, SnItem item, String command) {
        if (item.originalTxId() != null) {
            need(
                    Set.of("SO_RETURN", "WM_MATERIAL_RETURN", "PRD_REWORK_ORDER_RECEIVE")
                            .contains(r.sourceType()),
                    "原出库流水只允许由对应真实退回来源引用，不能把采购、杂收、盘盈或完工伪作退回");
            original(r, item);
            return;
        }
        var birth =
                row(
                        "SELECT material_id,status,source_doc_type,source_doc_id FROM wm_sn WHERE"
                                + " tenant_id=? AND sn_id=? AND del_flag='0' FOR UPDATE",
                        tenant(),
                        item.identityId());
        need(same(r.materialId(), id(birth.get("material_id"))), "正式身份注册物料不属于入库来源");
        String type = String.valueOf(birth.get("source_doc_type"));
        Long source = id(birth.get("source_doc_id"));
        boolean production = type.startsWith("MES_") || "PRD_WORK_ORDER".equals(type);
        if ("PRD_WORK_ORDER".equals(r.sourceType())) {
            need(production || "ERP_MANUAL".equals(type) && source == null, "其他业务预留的正式身份不能充作生产交付");
            if ("PRD_WORK_ORDER".equals(type)) {
                need(same(source, r.sourceId()), "正式身份注册于其他ERP生产工单");
            }
            if (production) {
                need(source != null && source > 0, "生产正式身份缺少原注册来源");
            }
            validateProductionHandoff(r, command, type, source);
            return;
        }
        need(!production, "MES在制正式身份只能由原最终生产交接入库，不能提前经其他库存单据收货");
        need(
                Set.of("REGISTERED", "CREATED").contains(String.valueOf(birth.get("status"))),
                "在制身份尚未交接，不能作为首次库存收货");
        if ("ERP_MANUAL".equals(type) && source == null) {
            return;
        }
        if (type.equals(r.sourceType())) {
            need(source != null && same(source, r.sourceId()), "正式身份已为其他原入库单预留");
            return;
        }
        if (Set.of("PU_PURCHASE_ORDER", "PURCHASE_ORDER").contains(type)
                && "WM_MATERIAL_RECEIVE".equals(r.sourceType())) {
            need(source != null && same(source, id(head(r).get("pur_id"))), "正式身份不属于原收货采购订单");
            return;
        }
        if (Set.of("SUBCONTRACT", "PRD_SUBCONTRACT_ORDER").contains(type)
                && "WM_SUBCONTRACT_RECEIVE".equals(r.sourceType())) {
            need(source != null && same(source, id(head(r).get("subcontract_id"))), "正式身份不属于原委外来源");
            return;
        }
        throw new ServiceException("正式身份注册来源不能授权本次入库，请核对原始来源凭据");
    }

    private void validateProductionHandoff(
            WmSerialPostingRequest r, String command, String birthType, Long birthSource) {
        need("PRODUCTION_IN".equals(r.txType()) && command != null, "生产身份缺少最终冻结交接命令");
        String[] parts = command.split("/", -1);
        need(
                parts.length == 4
                        && "MES_FINAL".equals(parts[0])
                        && parts[3].matches("[0-9a-f]{64}"),
                "生产身份只接受最终冻结清单交接");
        Long manifest = positiveId(parts[1]), revision = positiveId(parts[2]);
        need(manifest != null && revision != null, "最终交接清单身份无效");
        var messages =
                jdbc.queryForList(
                        "SELECT payload,content_hash,status FROM wm_trace_inbox WHERE tenant_id=?"
                            + " AND source_module='MES' AND event_type='COMPLETION_MANIFEST_FROZEN'"
                            + " AND aggregate_type='COMPLETION_MANIFEST' AND aggregate_id=? AND"
                            + " status IN ('WAITING','APPLIED') ORDER BY event_id FOR UPDATE",
                        tenant(),
                        manifest);
        boolean matched = false;
        for (var message : messages) {
            if (!Set.of("WAITING", "APPLIED").contains(String.valueOf(message.get("status")))) {
                continue;
            }
            String payload = String.valueOf(message.get("payload"));
            need(TraceHashes.sha256(payload).equals(message.get("content_hash")), "原MES交接凭据摘要不符");
            try {
                var proof = json.readTree(payload);
                Long mesWo = jsonId(proof, "mesWoId");
                if (same(manifest, jsonId(proof, "manifestId"))
                        && same(revision, jsonId(proof, "revision"))
                        && parts[3].equals(proof.path("manifestHash").asText())
                        && same(r.sourceId(), jsonId(proof, "erpWoId"))
                        && same(r.materialId(), jsonId(proof, "materialId"))
                        && mesWo != null
                        && (!"MES_MANUAL".equals(birthType) || same(birthSource, mesWo))) {
                    matched = true;
                }
            } catch (java.io.IOException malformed) {
                throw new ServiceException("原MES交接凭据格式无效");
            }
        }
        need(matched, "正式生产身份缺少同工单、同物料及同清单摘要的ERP本地冻结交接凭据");
    }

    private static Long jsonId(com.fasterxml.jackson.databind.JsonNode payload, String key) {
        return positiveId(payload.path(key).asText());
    }

    private static Long positiveId(String text) {
        try {
            long value = new BigDecimal(text).longValueExact();
            return value > 0 ? value : null;
        } catch (NumberFormatException | ArithmeticException invalid) {
            return null;
        }
    }

    /**
     * 根据已核验的入库选择构造每件数量为一的实际库存行。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param sn 实际单件身份或本地单件记录。
     * @return 数量为一且保留实际仓储位置的新单件库存对象。
     */
    public WmMaterialStock inboundStock(WmSerialPostingRequest r, SnItem i, Identity sn) {
        var m =
                row(
                        "SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND"
                                + " del_flag='0'",
                        tenant(),
                        r.materialId());
        WmMaterialStock s = new WmMaterialStock();
        s.setStockId(IdWorker.getId());
        s.setTenantId(tenant());
        s.setMaterialId(r.materialId());
        s.setMaterialCode((String) m.get("material_code"));
        s.setMaterialName((String) m.get("material_name"));
        s.setMaterialSpec((String) m.get("material_spec"));
        s.setCategoryId(id(m.get("category_id")));
        s.setBaseUomId(String.valueOf(m.get("base_uom_id")));
        s.setSnId(sn.id());
        s.setSnCode(sn.code());
        s.setBatchId(i.batchId());
        s.setWarehouseId(
                r.warehouseId() != null
                        ? r.warehouseId()
                        : id(
                                head(r).get("warehouse_id") != null
                                        ? head(r).get("warehouse_id")
                                        : head(r).get("target_warehouse_id")));
        s.setLocationId(i.targetLocationId() != null ? i.targetLocationId() : r.locationId());
        if (s.getLocationId() == null && !"PRD_WORK_ORDER".equals(r.sourceType())) {
            s.setLocationId(
                    id(
                            line(r).get("location_id") != null
                                    ? line(r).get("location_id")
                                    : line(r).get("target_location_id")));
        }
        if (s.getWarehouseId() == null && s.getLocationId() != null) {
            s.setWarehouseId(
                    id(
                            row(
                                            "SELECT warehouse_id FROM wm_storage_location WHERE"
                                                + " tenant_id=? AND location_id=? AND del_flag='0'"
                                                + " FOR UPDATE",
                                            tenant(),
                                            s.getLocationId())
                                    .get("warehouse_id")));
        }
        if ("PRD_WORK_ORDER".equals(r.sourceType())) {
            s.setWoId(r.sourceId());
            s.setWoNo((String) head(r).get("wo_no"));
        }
        if ("WM_MATERIAL_RECEIVE".equals(r.sourceType())) {
            s.setVendorId(id(head(r).get("vendor_id")));
        }
        if (i.batchId() != null) {
            var batch =
                    row(
                            "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                    + " material_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            i.batchId(),
                            r.materialId());
            s.setBatchCode((String) batch.get("batch_code"));
            s.setExpireDate((java.util.Date) batch.get("expiry_date"));
        }
        if (i.originalTxId() != null) {
            var original = original(r, i);
            var old = stock(id(original.get("material_stock_id")));
            s.setVendorId(old.getVendorId());
            s.setVendorCode(old.getVendorCode());
            s.setVendorName(old.getVendorName());
            s.setVendorNick(old.getVendorNick());
            s.setWoId(old.getWoId());
            s.setWoNo(old.getWoNo());
        }
        storage(s);
        s.setStockQuantity(BigDecimal.ONE);
        s.setReservedQuantity(BigDecimal.ZERO);
        s.setTrackingVersion(2L);
        s.setVersion(0);
        s.setFrozenFlag(0L);
        s.setDelFlag("0");
        s.setRecptDate(new Date());
        return s;
    }

    public void storage(WmMaterialStock s) {
        var w =
                row(
                        "SELECT * FROM wm_warehouse WHERE tenant_id=? AND warehouse_id=? AND"
                                + " del_flag='0' FOR UPDATE",
                        tenant(),
                        s.getWarehouseId());
        need("1".equals(String.valueOf(w.get("status"))), "目标仓库未启用");
        s.setWarehouseCode((String) w.get("warehouse_code"));
        s.setWarehouseName((String) w.get("warehouse_name"));
        if (s.getLocationId() != null) {
            var l =
                    row(
                            "SELECT * FROM wm_storage_location WHERE tenant_id=? AND location_id=?"
                                    + " AND warehouse_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            s.getLocationId(),
                            s.getWarehouseId());
            need(
                    "1".equals(String.valueOf(l.get("enable_flag")))
                            && "0".equals(String.valueOf(l.get("frozen_flag"))),
                    "目标储位停用或冻结");
            s.setAreaId(id(l.get("area_id")));
            s.setLocationCode((String) l.get("location_code"));
            s.setLocationName((String) l.get("location_name"));
        }
        if (s.getAreaId() != null) {
            var a =
                    row(
                            "SELECT * FROM wm_storage_area WHERE tenant_id=? AND area_id=? AND"
                                    + " warehouse_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            s.getAreaId(),
                            s.getWarehouseId());
            need(
                    "1".equals(String.valueOf(a.get("enable_flag")))
                            && "0".equals(String.valueOf(a.get("frozen_flag"))),
                    "目标库区停用或冻结");
            s.setAreaCode((String) a.get("area_code"));
            s.setAreaName((String) a.get("area_name"));
        }
    }

    /**
     * 在当前库存事务内写入逐件库存，唯一性和数量守卫共同限制有效占用。
     */
    public void insertStock(WmMaterialStock s) {
        one(stocks.insert(s));
    }

    /**
     * 在原库存事务内保存已加锁的库存状态。
     */
    public void saveStock(WmMaterialStock s) {
        one(stocks.updateById(s));
    }

    /**
     * 写入带稳定身份与过账组的库存流水，作为后续重放和退回依据。
     */
    public void insertTx(WmMaterialStockTx t) {
        one(transactions.insert(t));
    }

    /**
     * 在同一库存事务内更新正式身份的状态与当前位置。
     *
     * @param sn 实际单件身份或本地单件记录。
     */
    public void updateIdentity(Identity sn, WmMaterialStock s, String state) {
        one(
                jdbc.update(
                        "UPDATE wm_sn SET"
                            + " batch_id=?,batch_code=?,stock_id=?,warehouse_id=?,warehouse_code=?,warehouse_name=?,location_id=?,location_code=?,status=?,identity_version=identity_version+1"
                            + " WHERE tenant_id=? AND sn_id=? AND material_id=? AND del_flag='0'",
                        s.getBatchId(),
                        s.getBatchCode(),
                        s.getStockId(),
                        s.getWarehouseId(),
                        s.getWarehouseCode(),
                        s.getWarehouseName(),
                        s.getLocationId(),
                        s.getLocationCode(),
                        state,
                        tenant(),
                        sn.id(),
                        sn.materialId()));
    }

    /**
     * 按实际业务方向更新原批次累计量，与库存数量一同提交或回滚。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param stock 已核实来源与位置的实际库存记录。
     */
    public void batch(WmSerialPostingRequest r, WmMaterialStock stock, int direction) {
        if (stock.getBatchId() == null || direction == 0) {
            return;
        }
        String column = direction > 0 ? "received_qty" : "issued_qty";
        String sourceGuard =
                "PRD_WORK_ORDER".equals(r.sourceType())
                        ? " AND source_doc_type='PRD_WORK_ORDER' AND source_doc_id=" + r.sourceId()
                        : "";
        String available =
                direction < 0 ? " AND IFNULL(issued_qty,0)+1<=IFNULL(received_qty,0)" : "";
        one(
                jdbc.update(
                        "UPDATE wm_batch SET "
                                + column
                                + "=IFNULL("
                                + column
                                + ",0)+1 WHERE tenant_id=? AND batch_id=? AND material_id=? AND"
                                + " del_flag='0'"
                                + sourceGuard
                                + available,
                        tenant(),
                        stock.getBatchId(),
                        r.materialId()));
    }

    public long identityVersion(Long sn) {
        return rowNumber("SELECT identity_version FROM wm_sn WHERE tenant_id=? AND sn_id=?", sn);
    }

    private long rowNumber(String sql, Long key) {
        Long value = jdbc.queryForObject(sql, Long.class, tenant(), key);
        need(value != null, "原身份版本缺失");
        return value;
    }

    /**
     * 核验入库身份与原来源选择，防止同件实物同时形成多条有效库存。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param item 当前实际业务行或单件明细。
     * @param stock 已核实来源与位置的实际库存记录。
     */
    public void validateInboundSelection(
            WmSerialPostingRequest r, SnItem item, WmMaterialStock stock) {
        if ("PRD_WORK_ORDER".equals(r.sourceType()) && item.batchId() != null) {
            var batch =
                    row(
                            "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant(),
                            item.batchId());
            need(
                    "PRD_WORK_ORDER".equals(batch.get("source_doc_type"))
                            && same(r.sourceId(), id(batch.get("source_doc_id")))
                            && same(r.materialId(), id(batch.get("material_id")))
                            && same(stock.getWarehouseId(), id(batch.get("warehouse_id"))),
                    "成品批次不是原始工单的真实来源");
        }
        if (!"WM_MATERIAL_RECEIVE".equals(r.sourceType())) {
            return;
        }
        var details =
                jdbc.queryForList(
                        "SELECT * FROM wm_material_receive_detail WHERE tenant_id=? AND"
                                + " receive_id=? AND line_id=? AND del_flag='0' FOR UPDATE",
                        tenant(),
                        r.sourceId(),
                        r.sourceLineId());
        if (details.isEmpty()) {
            return;
        }
        BigDecimal matching = BigDecimal.ZERO;
        for (var d : details) {
            if (same(item.batchId(), id(d.get("batch_id")))
                    && same(stock.getLocationId(), id(d.get("location_id")))
                    && same(stock.getWarehouseId(), id(d.get("warehouse_id")))
                    && (d.get("sn_id") == null || same(item.identityId(), id(d.get("sn_id"))))) {
                matching = matching.add(decimal(d.get("quantity")));
            }
        }
        need(matching.signum() > 0, "SN选择不属于原收货分配维度");
    }

    /**
     * 从原领用证据定位在制报废对应的实物库存事实。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param item 当前实际业务行或单件明细。
     * @return 原领料流水对应、当前库存数量已归零的单件库存行。
     */
    public WmMaterialStock issuedScrapStock(WmSerialPostingRequest r, SnItem item) {
        need(item.originalTxId() != null, "已领用报废必须引用原领料流水");
        var t = original(r, item);
        var s = stock(id(t.get("material_stock_id")));
        need(
                item.identityId().equals(s.getSnId())
                        && s.getStockQuantity().signum() == 0
                        && zero(s.getReservedQuantity()).signum() == 0,
                "已领用报废的原实物库存已改变");
        return s;
    }

    static BigDecimal zero(BigDecimal d) {
        return d == null ? BigDecimal.ZERO : d;
    }

    /**
     * 退回沿用原出库成本，其余入库按原来源凭据确定成本。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @param item 当前实际业务行或单件明细。
     * @return 本件入库成本；有原出库流水时沿用该流水金额。
     */
    public BigDecimal inboundCost(WmSerialPostingRequest r, SnItem item) {
        if (item.originalTxId() != null) {
            var tx = original(r, item);
            return decimal(tx.get("tx_amount"));
        }
        need(
                !Set.of("SO_RETURN", "WM_MATERIAL_RETURN", "PRD_REWORK_ORDER_RECEIVE")
                        .contains(r.sourceType()),
                "退回必须引用真实原出库流水");
        if (r.unitCost() != null) {
            need(r.unitCost().signum() >= 0, "入库成本无效");
            return r.unitCost();
        }
        var m =
                row(
                        "SELECT standard_cost FROM bd_material WHERE tenant_id=? AND material_id=?"
                                + " AND del_flag='0'",
                        tenant(),
                        r.materialId());
        BigDecimal c = decimal(m.get("standard_cost"));
        need(c != null && c.signum() >= 0, "入库标准成本来源未知");
        return c;
    }

    private Map<String, Object> original(WmSerialPostingRequest r, SnItem i) {
        var t =
                row(
                        "SELECT * FROM wm_material_stock_tx WHERE tenant_id=? AND tx_id=? AND"
                                + " sn_id=? AND material_id=? AND del_flag='0' FOR UPDATE",
                        tenant(),
                        i.originalTxId(),
                        i.identityId(),
                        r.materialId());
        need(
                id(t.get("tx_flag")) == -1
                        && BigDecimal.ONE.compareTo(decimal(t.get("tx_quantity"))) == 0
                        && id(t.get("tracking_version")) >= 2,
                "退回原事务不是同一件真实出库事实");
        var prior =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_material_stock_tx WHERE tenant_id=? AND"
                                + " related_tx_id=? AND tx_flag=1 AND del_flag='0'",
                        Long.class,
                        tenant(),
                        i.originalTxId());
        need(prior != null && prior == 0, "原出库实物已退回，禁止再次重授库存");
        if ("SO_RETURN".equals(r.sourceType())) {
            var h = head(r);
            var l = line(r);
            var issue =
                    row(
                            "SELECT h.so_id,l.source_doc_line_id FROM wm_sales_issue h JOIN"
                                + " wm_sales_issue_line l ON l.tenant_id=h.tenant_id AND"
                                + " l.issue_id=h.issue_id WHERE h.tenant_id=? AND h.issue_id=? AND"
                                + " l.line_id=? AND h.del_flag='0' AND l.del_flag='0' FOR UPDATE",
                            tenant(),
                            t.get("source_doc_id"),
                            t.get("source_doc_line_id"));
            need(
                    "WM_SALES_ISSUE".equals(t.get("source_doc_type"))
                            && same(h.get("so_id"), issue.get("so_id"))
                            && same(l.get("so_line_id"), issue.get("source_doc_line_id")),
                    "销售退货实物未从原始订单行发出");
        }
        if ("PRD_REWORK_ORDER_RECEIVE".equals(r.sourceType())) {
            need(
                    "PRD_REWORK_ORDER".equals(t.get("source_doc_type"))
                            && same(r.sourceId(), id(t.get("source_doc_id"))),
                    "返工回库实物不是原返工单实际发出");
        }
        if ("WM_MATERIAL_RETURN".equals(r.sourceType())) {
            need("WM_PRODUCTION_ISSUE".equals(t.get("source_doc_type")), "生产退料/报废只能引用原始生产领料流水");
            var issue =
                    row(
                            "SELECT * FROM wm_production_issue WHERE tenant_id=? AND issue_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant(),
                            t.get("source_doc_id"));
            Object source = head(r).get("source_doc");
            need(
                    source != null
                            && (source.equals(issue.get("issue_code"))
                                    || source.equals(issue.get("wo_no"))),
                    "生产退料来源单号不能证明原始领料归属");
        }
        return t;
    }

    /**
     * 按实际单件消费原业务预留，数量不得因重复命令再次减少。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     * @return 本件实际消费的预留量，只能为零或一。
     */
    public BigDecimal consumeReservation(
            WmSerialPostingRequest r, WmMaterialStock s, BigDecimal remaining) {
        if (remaining == null || remaining.signum() <= 0 || s.getReservedQuantity().signum() == 0) {
            return BigDecimal.ZERO;
        }
        need(r.reserveOrderId() != null && r.reserveLineId() != null, "不能消费其他来源的预留");
        BigDecimal net =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(CASE WHEN tx_type='RESERVE' THEN tx_quantity WHEN"
                            + " tx_type IN ('UNRESERVE','CONSUME_RESERVE') THEN -tx_quantity ELSE 0"
                            + " END),0) FROM wm_material_stock_tx WHERE tenant_id=? AND"
                            + " source_doc_type='SALES_ORDER' AND source_doc_id=? AND"
                            + " source_doc_line_id=? AND material_stock_id=? AND del_flag='0'",
                        BigDecimal.class,
                        tenant(),
                        r.reserveOrderId(),
                        r.reserveLineId(),
                        s.getStockId());
        return net != null && net.compareTo(BigDecimal.ONE) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    /**
     * 在全部逐件效果完成后冻结过账总金额和原计价组引用。
     *
     * @param amount 所属计价范围的总金额。
     */
    public void finish(Long group, BigDecimal amount, Long valuation) {
        one(
                jdbc.update(
                        "UPDATE wm_trace_posting_group SET"
                            + " amount=?,valuation_group_id=?,status='POSTED' WHERE tenant_id=? AND"
                            + " posting_group_id=? AND status='PREPARED'",
                        amount,
                        valuation,
                        tenant(),
                        group));
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ServiceException("事件载荷冻结失败");
        }
    }

    /**
     * 调用方已持有正式身份、来源工单或过账组父锁；发布版本只由本地已登记stream分配。
     */
    public void appendEvent(
            String event,
            String aggregate,
            Long id,
            long sourceVersion,
            String type,
            String payload) {
        requireWrite();
        try {
            payload = encode(json.readTree(payload));
        } catch (Exception failure) {
            throw new ServiceException("事件载荷不是有效JSON");
        }
        Long registered =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(aggregate_version),0) FROM wm_trace_outbox WHERE"
                                + " tenant_id=? AND source_module='ERP' AND aggregate_type=? AND"
                                + " aggregate_id=? FOR UPDATE",
                        Long.class,
                        tenant(),
                        aggregate,
                        id);
        need(registered != null && registered >= 0, "原发布stream版本未知");
        long publicationVersion = Math.addExact(registered, 1L);
        one(
                jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_outbox(event_id,tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version,event_type,schema_version,payload,content_hash,status)"
                            + " VALUES(?,?,'ERP',?,?,?,?,1,?,?,'NEW')",
                        event,
                        tenant(),
                        aggregate,
                        id,
                        publicationVersion,
                        type,
                        payload,
                        TraceHashes.sha256(payload)));
    }

    public Long mesWorkOrder(Long erpWo) {
        var rows =
                jdbc.queryForList(
                        "SELECT payload FROM wm_trace_outbox WHERE tenant_id=? AND"
                                + " event_type='ERP_PRODUCTION_LOT_AUTHORIZED' ORDER BY"
                                + " created_at,aggregate_id FOR UPDATE",
                        tenant());
        Set<Long> ids = new HashSet<>();
        for (var row : rows) {
            var p = com.alibaba.fastjson2.JSON.parseObject(row.get("payload").toString());
            if (Objects.equals(erpWo, p.getLong("erpWoId"))) {
                ids.add(p.getLong("mesWoId"));
            }
        }
        need(ids.size() == 1, "缺少唯一真实MES工单生产批次授权绑定");
        return ids.iterator().next();
    }

    /**
     * 在同一本地库存事务中追加来源事件，发布失败不丢失已提交业务状态。
     *
     * @param payload 冻结的事件载荷，后续重放不得重新拼装。
     * @param hash 原冻结内容的摘要。
     */
    public void enqueue(Long group, String payload, String hash) {
        var g =
                row(
                        "SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND"
                                + " posting_group_id=? FOR UPDATE",
                        tenant(),
                        group);
        String type = String.valueOf(g.get("source_doc_type"));
        if ("WM_SALES_ISSUE".equals(type)) {
            var ids = items(group).stream().map(i -> i.identityId().toString()).toList();
            Map<String, Object> shippingProof = new LinkedHashMap<>();
            shippingProof.put("postingGroupId", group.toString());
            shippingProof.put("sourceDocId", String.valueOf(g.get("source_doc_id")));
            shippingProof.put("sourceDocType", type);
            shippingProof.put("identityIds", ids);
            shippingProof.put(
                    "items",
                    items(group).stream()
                            .map(
                                    i ->
                                            Map.of(
                                                    "identityId",
                                                    i.identityId().toString(),
                                                    "identityVersion",
                                                    Long.toString(identityVersion(i.identityId()))))
                            .toList());
            String shipping = encode(shippingProof);
            appendEvent(
                    "ERP-SHIP-" + group,
                    "STOCK_POSTING",
                    group,
                    1,
                    "ERP_SHIPMENT_POSTED",
                    shipping);
            return;
        }
        if ("WM_PRODUCTION_ISSUE".equals(type)) {
            var issue =
                    row(
                            "SELECT * FROM wm_production_issue WHERE tenant_id=? AND issue_id=? AND"
                                    + " del_flag='0' FOR UPDATE",
                            tenant(),
                            g.get("source_doc_id"));
            if (!"BACKFLUSH".equals(issue.get("status"))
                    && !"BACKFLUSH".equals(issue.get("issue_type"))) {
                Long mesWo = mesWorkOrder(id(issue.get("wo_id")));
                var children =
                        jdbc.queryForList(
                                "SELECT sn_id,material_stock_id,batch_id,tx_quantity FROM"
                                        + " wm_material_stock_tx WHERE tenant_id=? AND"
                                        + " posting_group_id=? AND tx_flag=-1 ORDER BY sn_id,tx_id",
                                tenant(),
                                group);
                boolean serial = children.stream().anyMatch(c -> c.get("sn_id") != null);
                long version = 0;
                var sources =
                        serial
                                ? children
                                : jdbc.queryForList(
                                        "SELECT batch_id,SUM(tx_quantity) AS tx_quantity FROM"
                                            + " wm_material_stock_tx WHERE tenant_id=? AND"
                                            + " posting_group_id=? AND tx_flag=-1 GROUP BY batch_id"
                                            + " ORDER BY batch_id",
                                        tenant(),
                                        group);
                for (var child : sources) {
                    need(child.get("batch_id") != null || serial, "预领料批次来源缺失");
                    if (serial) {
                        need(
                                child.get("sn_id") != null
                                        && BigDecimal.ONE.compareTo(
                                                        decimal(child.get("tx_quantity")))
                                                == 0,
                                "预领料单件事实不是完整的一件");
                    }
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("sourceType", "PRODUCTION_ISSUE");
                    p.put("sourceId", group.toString());
                    p.put(
                            "sourceLineId",
                            String.valueOf(serial ? child.get("sn_id") : child.get("batch_id")));
                    p.put("postingGroupId", group.toString());
                    p.put("mesWoId", mesWo.toString());
                    p.put("erpWoId", String.valueOf(issue.get("wo_id")));
                    p.put("materialId", String.valueOf(g.get("material_id")));
                    p.put(
                            "erpBatchId",
                            child.get("batch_id") == null
                                    ? null
                                    : child.get("batch_id").toString());
                    p.put("quantity", decimal(child.get("tx_quantity")).toPlainString());
                    p.put("backflushFlag", 0);
                    if (serial) {
                        p.put("identityId", String.valueOf(child.get("sn_id")));
                        p.put("sourceStockId", String.valueOf(child.get("material_stock_id")));
                        p.put(
                                "identityVersion",
                                Long.toString(identityVersion(id(child.get("sn_id")))));
                    }
                    appendEvent(
                            "ERP-MATERIAL-" + group + "-" + (++version),
                            "STOCK_POSTING",
                            group,
                            version,
                            "ERP_MATERIAL_SOURCE_POSTED",
                            encode(p));
                }
                return;
            }
        }
        appendEvent(
                "ERP-STOCK-" + group, "STOCK_POSTING", group, 1, "ERP_INVENTORY_POSTED", payload);
    }

    /**
     * 验证原过账组、完整逐件流水和原选择，当前位置变化不改变历史入库结果。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     */
    public void verifyProof(WmSerialPostingRequest r, Group g, List<SnItem> selected) {
        var rows =
                jdbc.query(
                        "SELECT * FROM wm_material_stock_tx WHERE tenant_id=? AND"
                                + " posting_group_id=? ORDER BY sn_id,tx_id FOR UPDATE",
                        BeanPropertyRowMapper.newInstance(WmMaterialStockTx.class),
                        tenant(),
                        g.id());
        Set<Long> ids = new HashSet<>();
        BigDecimal qty = BigDecimal.ZERO, amount = BigDecimal.ZERO;
        int reserveFacts = 0;
        for (var t : rows) {
            if ("CONSUME_RESERVE".equals(t.getTxType())) {
                need(
                        t.getTxFlag() == 0
                                && "SALES_ORDER".equals(t.getSourceDocType())
                                && t.getSnId() != null
                                && BigDecimal.ONE.compareTo(t.getTxQuantity()) == 0
                                && same(r.reserveOrderId(), t.getSourceDocId())
                                && same(r.reserveLineId(), t.getSourceDocLineId()),
                        "原预留消费证明不符");
                reserveFacts++;
                continue;
            }
            need(
                    tenant().equals(t.getTenantId())
                            && t.getTxId() != null
                            && t.getMaterialStockId() != null
                            && Objects.equals(r.sourceLineId(), t.getSourceDocLineId())
                            && r.txType().equals(t.getTxType())
                            && t.getTxAmount() != null
                            && t.getTxAmount().signum() >= 0
                            && t.getTxAmount().stripTrailingZeros().scale() <= 4
                            && t.getUnitCost() != null
                            && t.getUnitCost().signum() >= 0
                            && "0".equals(t.getDelFlag())
                            && Long.valueOf(2).equals(t.getTrackingVersion())
                            && same(g.id(), t.getPostingGroupId())
                            && same(r.materialId(), t.getMaterialId())
                            && same(r.sourceType(), t.getSourceDocType())
                            && same(r.sourceId(), t.getSourceDocId())
                            && t.getTxDate() != null,
                    "单件库存原证明身份或来源不完整");
            need(
                    t.getSnId() != null
                            && BigDecimal.ONE.compareTo(t.getTxQuantity()) == 0
                            && t.getBeforeQuantity() != null
                            && t.getAfterQuantity() != null
                            && t.getTxFlag() != null
                            && t.getAfterQuantity()
                                            .subtract(t.getBeforeQuantity())
                                            .compareTo(BigDecimal.valueOf(t.getTxFlag()))
                                    == 0,
                    "单件库存原证明数量不平衡");
            SnItem frozen =
                    selected.stream()
                            .filter(i -> i.identityId().equals(t.getSnId()))
                            .findFirst()
                            .orElse(null);
            need(
                    frozen != null && Objects.equals(frozen.batchId(), t.getBatchId()),
                    "原逐件证明不属于冻结实物/批次全集");
            if (t.getTxFlag()
                    == ("SCRAP".equals(r.txType()) ? 0 : r.direction() == 0 ? -1 : r.direction())) {
                need(ids.add(t.getSnId()), "原单件证明重复");
                qty = qty.add(t.getTxQuantity());
                amount = amount.add(t.getTxAmount());
            }
        }
        need(
                ids.equals(new HashSet<>(selected.stream().map(SnItem::identityId).toList()))
                        && qty.compareTo(g.quantity()) == 0
                        && amount.compareTo(g.amount()) == 0
                        && rows.size() - reserveFacts
                                == selected.size() * (r.direction() == 0 ? 2 : 1),
                "原单件证明不完整或数量金额不守恒");
        // 仅核对不可变交易证明，后续合法转移/销售不要求当前库存仍处于收货时状态。
    }

    /**
     * 把经兼容证明核验的原入库结果关联到冻结清单，不产生第二次库存效果。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param receipt 原生产入库或交付回执。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    public Long recordLegacyManifest(CompletionManifest manifest, Long receipt) {
        var versions =
                jdbc.queryForList(
                        "SELECT policy_version FROM wm_trace_posting_group WHERE tenant_id=? AND"
                                + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? AND"
                                + " tx_type='PRODUCTION_IN_LEGACY' FOR UPDATE",
                        tenant(),
                        manifest.erpWoId());
        need(versions.size() <= 1, "原旧版最终收货存在重复过账组");
        long version =
                versions.isEmpty()
                        ? policy(manifest.materialId()).version()
                        : id(versions.get(0).get("policy_version"));
        return recordLegacyManifest(manifest, receipt, version);
    }

    /**
     * 把经兼容证明核验的原入库结果关联到冻结清单，不产生第二次库存效果。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param receipt 原生产入库或交付回执。
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    public Long recordLegacyManifest(
            CompletionManifest manifest, Long receipt, long policyVersion) {
        var r =
                WmSerialPostingRequest.in(
                        "PRD_WORK_ORDER",
                        manifest.erpWoId(),
                        null,
                        "PRODUCTION_IN_LEGACY",
                        manifest.materialId(),
                        manifest.goodQty(),
                        null,
                        null,
                        null,
                        null);
        String command =
                "MES_FINAL/"
                        + manifest.manifestId()
                        + "/"
                        + manifest.revision()
                        + "/"
                        + manifest.contentHash();
        var prior = group(r);
        var tx =
                row(
                        "SELECT * FROM wm_material_stock_tx WHERE tenant_id=? AND"
                            + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? FOR UPDATE",
                        tenant(),
                        manifest.erpWoId());
        need(
                Long.valueOf(1L).equals(id(tx.get("tracking_version")))
                        && tx.get("posting_group_id") == null
                        && tx.get("sn_id") == null
                        && "PRODUCTION_IN".equals(tx.get("tx_type"))
                        && Long.valueOf(1L).equals(id(tx.get("tx_flag")))
                        && "0".equals(tx.get("del_flag"))
                        && manifest.materialId().equals(id(tx.get("material_id")))
                        && manifest.goodQty().compareTo(decimal(tx.get("tx_quantity"))) == 0,
                "旧版最终收货原证明不符");
        if (prior != null) {
            var original =
                    row(
                            "SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND"
                                    + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? AND"
                                    + " tx_type='PRODUCTION_IN_LEGACY' FOR UPDATE",
                            tenant(),
                            manifest.erpWoId());
            Long originalPolicy = id(original.get("policy_version"));
            need(
                    command.equals(prior.commandId())
                            && "POSTED".equals(prior.status())
                            && manifest.contentHash().equals(prior.contentHash())
                            && originalPolicy != null
                            && originalPolicy > 0
                            && Long.valueOf(0).equals(id(original.get("source_doc_line_id")))
                            && manifest.materialId().equals(id(original.get("material_id")))
                            && manifest.revision() == id(original.get("detail_version"))
                            && manifest.goodQty().compareTo(prior.quantity()) == 0
                            && decimal(tx.get("tx_amount")).compareTo(prior.amount()) == 0
                            && sourceHash(r).equals(original.get("source_hash"))
                            && TraceHashes.fingerprint(
                                            1, originalPolicy, receipt, manifest.contentHash())
                                    .equals(prior.fingerprint()),
                    "原旧版最终收货组的来源、数量、金额或版本证明不符");
            return prior.id();
        }
        long group = IdWorker.getId();
        String fp = TraceHashes.fingerprint(1, policyVersion, receipt, manifest.contentHash());
        one(
                jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_posting_group(posting_group_id,tenant_id,command_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,tx_type,material_id,quantity,amount,fingerprint,content_hash,source_hash,policy_version,status)"
                            + " VALUES(?,?,?,'PRD_WORK_ORDER',?,0,?,'PRODUCTION_IN_LEGACY',?,?,?,?,?,?,?,'POSTED')",
                        group,
                        tenant(),
                        command,
                        manifest.erpWoId(),
                        manifest.revision(),
                        manifest.materialId(),
                        manifest.goodQty(),
                        decimal(tx.get("tx_amount")),
                        fp,
                        manifest.contentHash(),
                        sourceHash(r),
                        policyVersion));
        String payload =
                "{\"postingGroupId\":\""
                        + group
                        + "\",\"receiptId\":\""
                        + receipt
                        + "\",\"manifestId\":\""
                        + manifest.manifestId()
                        + "\",\"manifestHash\":\""
                        + manifest.contentHash()
                        + "\",\"trackingVersion\":\"1\",\"items\":[]}";
        enqueue(group, payload, TraceHashes.sha256(payload));
        return group;
    }

    public com.nexus.erp.wm.domain.vo.PutawayLocation productionLotTarget(
            Long wo, Long mesWo, Long batch) {
        var event =
                row(
                        "SELECT payload,content_hash FROM wm_trace_outbox WHERE tenant_id=? AND"
                                + " aggregate_type='PRODUCTION_LOT' AND aggregate_id=? AND"
                                + " event_type='ERP_PRODUCTION_LOT_AUTHORIZED' FOR UPDATE",
                        tenant(),
                        batch);
        String text = event.get("payload").toString();
        var payload = com.alibaba.fastjson2.JSON.parseObject(text);
        need(
                TraceHashes.sha256(text).equals(event.get("content_hash"))
                        && Objects.equals(wo, payload.getLong("erpWoId"))
                        && Objects.equals(mesWo, payload.getLong("mesWoId")),
                "原生产批次授权与最终工单不符");
        var target = new com.nexus.erp.wm.domain.vo.PutawayLocation();
        target.setWarehouseId(payload.getLong("warehouseId"));
        target.setLocationId(payload.getLong("locationId"));
        return target;
    }

    public String authorizedProductionBatchCode(Long wo, Long mesWo, Long batch, Long warehouse) {
        var source =
                row(
                        "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=? AND"
                                + " warehouse_id=? AND del_flag='0' FOR UPDATE",
                        tenant(),
                        batch,
                        wo,
                        warehouse);
        var event =
                row(
                        "SELECT payload,content_hash FROM wm_trace_outbox WHERE tenant_id=? AND"
                                + " aggregate_type='PRODUCTION_LOT' AND aggregate_id=? AND"
                                + " event_type='ERP_PRODUCTION_LOT_AUTHORIZED' FOR UPDATE",
                        tenant(),
                        batch);
        String text = event.get("payload").toString();
        var payload = com.alibaba.fastjson2.JSON.parseObject(text);
        need(
                TraceHashes.sha256(text).equals(event.get("content_hash"))
                        && Objects.equals(mesWo, payload.getLong("mesWoId"))
                        && Objects.equals(wo, payload.getLong("erpWoId")),
                "成品批次缺少同工单真实授权");
        return source.get("batch_code").toString();
    }

    public Long bindActualLegacyIssue(WmSerialPostingRequest r) {
        var tx =
                row(
                        "SELECT SUM(tx_quantity) AS quantity,SUM(tx_amount) AS amount FROM"
                            + " wm_material_stock_tx WHERE tenant_id=? AND"
                            + " source_doc_type='WM_PRODUCTION_ISSUE' AND source_doc_id=? AND"
                            + " source_doc_line_id=? AND tx_flag=-1 AND del_flag='0' FOR UPDATE",
                        tenant(),
                        r.sourceId(),
                        r.sourceLineId());
        need(r.quantity().compareTo(decimal(tx.get("quantity"))) == 0, "实际批次扣料数量不守恒");
        long group = IdWorker.getId();
        String hash =
                TraceHashes.fingerprint(
                        r.sourceId(), r.sourceLineId(), r.materialId(), r.quantity());
        one(
                jdbc.update(
                        "INSERT INTO"
                            + " wm_trace_posting_group(posting_group_id,tenant_id,command_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,tx_type,material_id,quantity,amount,fingerprint,content_hash,source_hash,policy_version,status)"
                            + " VALUES(?,?,?,'WM_PRODUCTION_ISSUE',?,?,1,'PRODUCTION_ISSUE',?,?,?,?,?,?,?,'POSTED')",
                        group,
                        tenant(),
                        "ACTUAL_BATCH/" + r.sourceId() + "/" + r.sourceLineId(),
                        r.sourceId(),
                        r.sourceLineId(),
                        r.materialId(),
                        r.quantity(),
                        decimal(tx.get("amount")),
                        hash,
                        hash,
                        sourceHash(r),
                        policy(r.materialId()).version()));
        need(
                jdbc.update(
                                "UPDATE wm_material_stock_tx SET posting_group_id=? WHERE"
                                    + " tenant_id=? AND source_doc_type='WM_PRODUCTION_ISSUE' AND"
                                    + " source_doc_id=? AND source_doc_line_id=? AND tx_flag=-1 AND"
                                    + " posting_group_id IS NULL",
                                group,
                                tenant(),
                                r.sourceId(),
                                r.sourceLineId())
                        > 0,
                "实际批次来源流水未绑定");
        enqueue(group, "{\"postingGroupId\":\"" + group + "\"}", hash);
        return group;
    }

    public boolean hasV2Receipt(Long receive) {
        Long n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_trace_posting_group WHERE tenant_id=? AND"
                                + " source_doc_type='WM_MATERIAL_RECEIVE' AND source_doc_id=? AND"
                                + " status='POSTED'",
                        Long.class,
                        tenant(),
                        receive);
        return n != null && n > 0;
    }

    public void verifyMaterialPools(Long material) {
        var pools =
                jdbc.queryForList(
                        "SELECT valuation_group_id,quantity,amount FROM wm_valuation_group WHERE"
                                + " tenant_id=? AND material_id=? ORDER BY valuation_group_id FOR"
                                + " UPDATE",
                        tenant(),
                        material);
        for (var p : pools) {
            Long pool = id(p.get("valuation_group_id"));
            var rows = poolStocks(pool);
            BigDecimal qty = BigDecimal.ZERO, amount = BigDecimal.ZERO;
            for (var stock : rows) {
                need(stock.getStockQuantity() != null && stock.getTotalCost() != null, "单件计价池余额缺失");
                qty = qty.add(stock.getStockQuantity());
                amount = amount.add(stock.getTotalCost());
            }
            var effects =
                    row(
                            "SELECT COALESCE(SUM(t.tx_flag*t.tx_quantity),0) AS"
                                    + " qty,COALESCE(SUM(t.tx_flag*t.tx_amount),0) AS amount FROM"
                                    + " wm_material_stock_tx t JOIN wm_material_stock s ON"
                                    + " s.tenant_id=t.tenant_id AND s.stock_id=t.material_stock_id"
                                    + " WHERE t.tenant_id=? AND s.valuation_group_id=? AND"
                                    + " t.tracking_version=2 AND t.del_flag='0'",
                            tenant(),
                            pool);
            need(
                    qty.compareTo(decimal(p.get("quantity")).add(decimal(effects.get("qty")))) == 0
                            && amount.compareTo(
                                            decimal(p.get("amount"))
                                                    .add(decimal(effects.get("amount"))))
                                    == 0,
                    "单件计价池原始基线与逐件效果数量金额不守恒");
        }
        var unknown =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id=? AND material_id=?"
                            + " AND stock_quantity>0 AND (tracking_version<2 OR sn_id IS NULL OR"
                            + " valuation_group_id IS NULL)",
                        Long.class,
                        tenant(),
                        material);
        need(unknown != null && unknown == 0, "STRICT物料仍有未审计库存池");
    }

    /**
     * 核验原收货选择及完整逐件结果，避免把部分流水误当作整单成功。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     */
    public void verifyReceiveSelections(
            WmSerialPostingRequest r, Group group, List<SnItem> selected) {
        var details =
                jdbc.queryForList(
                        "SELECT * FROM wm_material_receive_detail WHERE tenant_id=? AND"
                                + " receive_id=? AND line_id=? AND del_flag='0' FOR UPDATE",
                        tenant(),
                        r.sourceId(),
                        r.sourceLineId());
        if (details.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> expected = new TreeMap<>(), actual = new TreeMap<>();
        Set<Long> explicit = new HashSet<>();
        for (var d : details) {
            String key =
                    canonical(d.get("warehouse_id"))
                            + "/"
                            + canonical(d.get("location_id"))
                            + "/"
                            + canonical(d.get("batch_id"));
            expected.merge(key, decimal(d.get("quantity")), BigDecimal::add);
            if (d.get("sn_id") != null) {
                need(explicit.add(id(d.get("sn_id"))), "原收货分配重复实物");
            }
        }
        for (var item : selected) {
            Long location =
                    item.targetLocationId() != null ? item.targetLocationId() : r.locationId();
            Long warehouse = r.warehouseId();
            if (location != null) {
                warehouse =
                        id(
                                row(
                                                "SELECT warehouse_id FROM wm_storage_location WHERE"
                                                        + " tenant_id=? AND location_id=? AND"
                                                        + " del_flag='0'",
                                                tenant(),
                                                location)
                                        .get("warehouse_id"));
            }
            if (warehouse == null) {
                warehouse = id(head(r).get("warehouse_id"));
            }
            String key =
                    canonical(warehouse)
                            + "/"
                            + canonical(location)
                            + "/"
                            + canonical(item.batchId());
            actual.merge(key, BigDecimal.ONE, BigDecimal::add);
        }
        need(expected.keySet().equals(actual.keySet()), "冻结SN与原收货分配全集不一致");
        for (var e : expected.entrySet()) {
            need(e.getValue().compareTo(actual.get(e.getKey())) == 0, "冻结SN与原收货分配维度数量不守恒");
        }
        need(
                selected.stream()
                        .map(SnItem::identityId)
                        .collect(java.util.stream.Collectors.toSet())
                        .containsAll(explicit),
                "冻结SN遗漏原单指定的正式实物");
    }

    public WmSerialPostingRequest proofRequest(String type, Long source, Long line, String tx) {
        var g =
                row(
                        "SELECT * FROM wm_trace_posting_group WHERE tenant_id=? AND"
                            + " source_doc_type=? AND source_doc_id=? AND source_doc_line_id=? AND"
                            + " tx_type=? AND status='POSTED' ORDER BY detail_version DESC LIMIT 1"
                            + " FOR UPDATE",
                        tenant(),
                        type,
                        source,
                        line == null ? 0 : line,
                        tx);
        int direction =
                Set.of(
                                        "PRODUCTION_IN",
                                        "RECEIVE_IN",
                                        "MISC_RECPT",
                                        "RETURN_IN",
                                        "REWORK_RECEIVE",
                                        "SUBCONTRACT_RECEIVE")
                                .contains(tx)
                        ? 1
                        : -1;
        return new WmSerialPostingRequest(
                type,
                source,
                line,
                tx,
                id(g.get("material_id")),
                decimal(g.get("quantity")),
                "TRANSFER".equals(tx) ? 0 : direction,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO);
    }

    /**
     * 核对一份生产原单、一个过账组与完整逐件流水集合。
     *
     * @param receipt 原生产入库或交付回执。
     */
    public void verifyProductionSource(
            WmProductionRecpt receipt, Long group, List<WmMaterialStockTx> txs) {
        need(txs != null && !txs.isEmpty(), "生产单件证明缺失");
        BigDecimal qty = BigDecimal.ZERO;
        Map<Long, BigDecimal> batches = new TreeMap<>();
        Set<Long> ids = new HashSet<>();
        for (var tx : txs) {
            need(
                    same(group, tx.getPostingGroupId())
                            && same(receipt.getWoId(), tx.getSourceDocId())
                            && same(receipt.getMaterialId(), tx.getMaterialId())
                            && same(receipt.getMaterialCode(), tx.getMaterialCode())
                            && same(receipt.getWarehouseId(), tx.getWarehouseId())
                            && same(receipt.getLocationId(), tx.getLocationId())
                            && Integer.valueOf(1).equals(tx.getTxFlag())
                            && "PRODUCTION_IN".equals(tx.getTxType())
                            && tx.getSnId() != null
                            && ids.add(tx.getSnId()),
                    "生产单件凭据父来源或仓储漂移");
            qty = qty.add(tx.getTxQuantity());
            if (tx.getBatchId() != null) {
                batches.merge(tx.getBatchId(), tx.getTxQuantity(), BigDecimal::add);
            }
        }
        need(receipt.getQuantity().compareTo(qty) == 0, "生产单件证明总数量与唯一入库单不符");
        for (var entry : batches.entrySet()) {
            var batch =
                    row(
                            "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                    + " material_id=? AND del_flag='0' FOR UPDATE",
                            tenant(),
                            entry.getKey(),
                            receipt.getMaterialId());
            need(
                    "PRD_WORK_ORDER".equals(batch.get("source_doc_type"))
                            && same(receipt.getWoId(), id(batch.get("source_doc_id")))
                            && same(receipt.getWarehouseId(), id(batch.get("warehouse_id")))
                            && entry.getValue().compareTo(decimal(batch.get("received_qty"))) <= 0,
                    "原生产批次来源或已收数量不符");
            verifyBatchLedger(entry.getKey(), batch);
        }
        var all =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_material_stock_tx WHERE tenant_id=? AND"
                                + " source_doc_type='PRD_WORK_ORDER' AND source_doc_id=?",
                        Long.class,
                        tenant(),
                        receipt.getWoId());
        need(all != null && all == txs.size(), "工单有额外、删除或异版本的生产入库流水");
    }

    /**
     * 核对原批次累计事实与当前库存台账的一致性。
     *
     * @param batch 实际批次及其来源上下文。
     */
    public void verifyBatchLedger(Long batch, Map<String, Object> current) {
        var effects =
                row(
                        "SELECT COALESCE(SUM(CASE WHEN tx_flag=1 THEN tx_quantity ELSE 0 END),0) AS"
                            + " received,COALESCE(SUM(CASE WHEN tx_flag=-1 THEN tx_quantity ELSE 0"
                            + " END),0) AS issued FROM wm_material_stock_tx WHERE tenant_id=? AND"
                            + " batch_id=? AND tracking_version=2 AND tx_type<>'TRANSFER' AND"
                            + " del_flag='0'",
                        tenant(),
                        batch);
        need(
                decimal(current.get("received_qty")).compareTo(decimal(effects.get("received")))
                                == 0
                        && decimal(current.get("issued_qty"))
                                        .compareTo(decimal(effects.get("issued")))
                                == 0,
                "新版批次累计量与原逐件库存效果不守恒");
    }

    /**
     * 重放只接受原持久化来源引用，不按当前物料配置重新分类历史凭据。
     *
     * @param r 包含原单、物料、方向和数量的受控过账请求。
     */
    public void requireReplaySource(WmSerialPostingRequest r) {
        if ("WM_PRODUCTION_ISSUE".equals(r.sourceType())) {
            need(decimal(line(r).get("current_qty")).signum() == 0, "本次领料尚未冻结新版本，不能复用上次过账结果");
        }
    }

    static void one(int n) {
        need(n == 1, "库存事实并发冲突或写入失败");
    }
}

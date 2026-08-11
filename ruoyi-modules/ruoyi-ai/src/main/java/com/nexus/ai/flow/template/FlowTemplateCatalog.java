package com.nexus.ai.flow.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置流程模板目录（对齐 n8n 模板库的精简版）
 * <p>3-5 个 ERP 场景模板：库存预警 / 采购对账 / 销售催办 / 生产工单进度巡检。
 * 模板均为"纯本地节点"（SQL/IF/CODE/NOTIFY），不依赖 LLM/RAG 模型配置，
 * 从模板创建后可直接测试运行——与种子演示流程同构。</p>
 * <p>模板不入库：静态注册表返回 config_json，前端"从模板创建"时经
 * {@code POST /ai/flow} 写入流程定义（可再编辑）。</p>
 *
 * @author NexusERP
 */
@Component
public class FlowTemplateCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 模板分类 */
    public static final String CATEGORY_STOCK = "库存";
    public static final String CATEGORY_PURCHASE = "采购";
    public static final String CATEGORY_SALES = "销售";
    public static final String CATEGORY_PRODUCTION = "生产";

    /**
     * 获取全部内置模板
     *
     * @return 模板列表（key/name/category/description/inputExample/configJson）
     */
    public List<Map<String, Object>> getTemplates() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(stockAlertTemplate());
        list.add(purchaseReconciliationTemplate());
        list.add(salesFollowUpTemplate());
        list.add(workOrderProgressTemplate());
        return list;
    }

    /**
     * 模板 1：库存预警通知
     * <p>SQL 查低于阈值的库存 → IF 判断是否有低库存 → true 发站内通知 / false 输出正常。
     * 运行入参: {"threshold": 100, "owner": 1}</p>
     */
    private Map<String, Object> stockAlertTemplate() {
        // node_1: sql
        ObjectNode sqlConfig = MAPPER.createObjectNode();
        sqlConfig.put("sqlTemplate",
            "SELECT material_code, material_name, material_spec, stock_quantity, warehouse_name FROM wm_material_stock WHERE stock_quantity < {{input.threshold}}");
        sqlConfig.put("maxRows", 50);
        // node_2: if count > 0
        ObjectNode ifConfig = MAPPER.createObjectNode();
        ifConfig.put("logic", "AND");
        ArrayNode ifConditions = ifConfig.putArray("conditions");
        ObjectNode cond = ifConditions.addObject();
        cond.put("variable", "nodes.node_1.output.count");
        cond.put("operator", "GT");
        cond.put("value", "0");
        // node_3: notify
        ObjectNode notifyConfig = MAPPER.createObjectNode();
        notifyConfig.put("userId", "{{input.owner}}");
        notifyConfig.put("title", "库存预警");
        notifyConfig.put("content", "发现 {{nodes.node_1.output.count}} 种物料低于阈值 {{input.threshold}}，请及时补货：{{nodes.node_1.output.rows}}");
        notifyConfig.put("category", "ai_flow_notice");
        // node_4: code 库存正常
        ObjectNode okConfig = MAPPER.createObjectNode();
        ObjectNode okExpr = okConfig.putObject("expressions");
        okExpr.put("summary", "'库存正常，无低于阈值的物料'");

        ObjectNode config = buildGraph(
            Map.of(
                "node_1", node("node_1", "sql", new double[]{60, 200}, "低库存查询", sqlConfig),
                "node_2", node("node_2", "if", new double[]{360, 200}, "是否存在低库存", ifConfig),
                "node_3", node("node_3", "notify", new double[]{640, 80}, "低库存通知", notifyConfig),
                "node_4", node("node_4", "code", new double[]{640, 320}, "库存正常输出", okConfig)
            ),
            List.of(
                edge("edge_1", "node_1", "outputs/main/0", "node_2", "inputs/main/0"),
                edge("edge_2", "node_2", "outputs/if/true", "node_3", "inputs/main/0"),
                edge("edge_3", "node_2", "outputs/if/false", "node_4", "inputs/main/0")
            )
        );
        return template("stock_alert", "库存预警通知", CATEGORY_STOCK,
            "查询低于阈值的库存物料，有低库存时向负责人发送站内通知",
            "{\"threshold\":100,\"owner\":1}", config.toString());
    }

    /**
     * 模板 2：采购对账提醒
     * <p>SQL 查待对账采购单（已收货/审批通过）→ IF 判断是否有待对账单 → 通知采购负责人。
     * 运行入参: {"owner": 1}</p>
     */
    private Map<String, Object> purchaseReconciliationTemplate() {
        ObjectNode sqlConfig = MAPPER.createObjectNode();
        sqlConfig.put("sqlTemplate",
            "SELECT order_no, vendor_name, total_amount, status FROM pu_purchase_order WHERE status IN ('APPROVED', 'RECEIVED') AND total_amount IS NOT NULL");
        sqlConfig.put("maxRows", 50);

        ObjectNode ifConfig = MAPPER.createObjectNode();
        ifConfig.put("logic", "AND");
        ArrayNode ifConditions = ifConfig.putArray("conditions");
        ObjectNode cond = ifConditions.addObject();
        cond.put("variable", "nodes.node_1.output.count");
        cond.put("operator", "GT");
        cond.put("value", "0");

        ObjectNode notifyConfig = MAPPER.createObjectNode();
        notifyConfig.put("userId", "{{input.owner}}");
        notifyConfig.put("title", "采购对账提醒");
        notifyConfig.put("content", "有 {{nodes.node_1.output.count}} 张采购单待对账：{{nodes.node_1.output.rows}}");
        notifyConfig.put("category", "ai_flow_notice");

        ObjectNode okConfig = MAPPER.createObjectNode();
        okConfig.putObject("expressions").put("summary", "'无待对账采购单'");

        ObjectNode config = buildGraph(
            Map.of(
                "node_1", node("node_1", "sql", new double[]{60, 200}, "待对账采购单查询", sqlConfig),
                "node_2", node("node_2", "if", new double[]{360, 200}, "是否有待对账单", ifConfig),
                "node_3", node("node_3", "notify", new double[]{640, 80}, "对账提醒", notifyConfig),
                "node_4", node("node_4", "code", new double[]{640, 320}, "无待对账输出", okConfig)
            ),
            List.of(
                edge("edge_1", "node_1", "outputs/main/0", "node_2", "inputs/main/0"),
                edge("edge_2", "node_2", "outputs/if/true", "node_3", "inputs/main/0"),
                edge("edge_3", "node_2", "outputs/if/false", "node_4", "inputs/main/0")
            )
        );
        return template("purchase_reconciliation", "采购对账提醒", CATEGORY_PURCHASE,
            "查询待对账的采购订单（已收货/审批通过），有数据时通知采购负责人",
            "{\"owner\":1}", config.toString());
    }

    /**
     * 模板 3：销售订单催办
     * <p>SQL 查未完成销售订单（确认/已提交待处理）→ IF 判断是否有待办 → 通知销售负责人。
     * 运行入参: {"owner": 1}</p>
     */
    private Map<String, Object> salesFollowUpTemplate() {
        ObjectNode sqlConfig = MAPPER.createObjectNode();
        sqlConfig.put("sqlTemplate",
            "SELECT co_no, client_name, status, requested_date, total_amount FROM so_customer_order WHERE status IN ('CONFIRMED', 'SUBMITTED', 'PENDING_REVIEW') AND requested_date IS NOT NULL");
        sqlConfig.put("maxRows", 50);

        ObjectNode ifConfig = MAPPER.createObjectNode();
        ifConfig.put("logic", "AND");
        ArrayNode ifConditions = ifConfig.putArray("conditions");
        ObjectNode cond = ifConditions.addObject();
        cond.put("variable", "nodes.node_1.output.count");
        cond.put("operator", "GT");
        cond.put("value", "0");

        ObjectNode notifyConfig = MAPPER.createObjectNode();
        notifyConfig.put("userId", "{{input.owner}}");
        notifyConfig.put("title", "销售订单催办");
        notifyConfig.put("content", "有 {{nodes.node_1.output.count}} 张销售订单需跟进：{{nodes.node_1.output.rows}}");
        notifyConfig.put("category", "ai_flow_notice");

        ObjectNode okConfig = MAPPER.createObjectNode();
        okConfig.putObject("expressions").put("summary", "'无待跟进销售订单'");

        ObjectNode config = buildGraph(
            Map.of(
                "node_1", node("node_1", "sql", new double[]{60, 200}, "待办销售订单查询", sqlConfig),
                "node_2", node("node_2", "if", new double[]{360, 200}, "是否有待办订单", ifConfig),
                "node_3", node("node_3", "notify", new double[]{640, 80}, "订单催办通知", notifyConfig),
                "node_4", node("node_4", "code", new double[]{640, 320}, "无待办输出", okConfig)
            ),
            List.of(
                edge("edge_1", "node_1", "outputs/main/0", "node_2", "inputs/main/0"),
                edge("edge_2", "node_2", "outputs/if/true", "node_3", "inputs/main/0"),
                edge("edge_3", "node_2", "outputs/if/false", "node_4", "inputs/main/0")
            )
        );
        return template("sales_followup", "销售订单催办", CATEGORY_SALES,
            "查询待跟进的销售订单（确认/待审），有数据时通知销售负责人",
            "{\"owner\":1}", config.toString());
    }

    /**
     * 模板 4：生产工单进度巡检
     * <p>SQL 查在制工单（已下达/生产中）→ IF 判断是否有在制工单 → 通知生产负责人。
     * 运行入参: {"owner": 1}</p>
     */
    private Map<String, Object> workOrderProgressTemplate() {
        ObjectNode sqlConfig = MAPPER.createObjectNode();
        sqlConfig.put("sqlTemplate",
            "SELECT wo_no, material_name, wo_status, planned_qty, completed_qty, plan_end_date FROM prd_work_order WHERE wo_status IN ('RELEASED', 'IN_PROGRESS') AND planned_qty > 0");
        sqlConfig.put("maxRows", 50);

        ObjectNode ifConfig = MAPPER.createObjectNode();
        ifConfig.put("logic", "AND");
        ArrayNode ifConditions = ifConfig.putArray("conditions");
        ObjectNode cond = ifConditions.addObject();
        cond.put("variable", "nodes.node_1.output.count");
        cond.put("operator", "GT");
        cond.put("value", "0");

        ObjectNode notifyConfig = MAPPER.createObjectNode();
        notifyConfig.put("userId", "{{input.owner}}");
        notifyConfig.put("title", "生产工单进度巡检");
        notifyConfig.put("content", "当前有 {{nodes.node_1.output.count}} 张在制工单需关注进度：{{nodes.node_1.output.rows}}");
        notifyConfig.put("category", "ai_flow_notice");

        ObjectNode okConfig = MAPPER.createObjectNode();
        okConfig.putObject("expressions").put("summary", "'当前无在制工单'");

        ObjectNode config = buildGraph(
            Map.of(
                "node_1", node("node_1", "sql", new double[]{60, 200}, "在制工单查询", sqlConfig),
                "node_2", node("node_2", "if", new double[]{360, 200}, "是否有在制工单", ifConfig),
                "node_3", node("node_3", "notify", new double[]{640, 80}, "进度巡检通知", notifyConfig),
                "node_4", node("node_4", "code", new double[]{640, 320}, "无在制输出", okConfig)
            ),
            List.of(
                edge("edge_1", "node_1", "outputs/main/0", "node_2", "inputs/main/0"),
                edge("edge_2", "node_2", "outputs/if/true", "node_3", "inputs/main/0"),
                edge("edge_3", "node_2", "outputs/if/false", "node_4", "inputs/main/0")
            )
        );
        return template("work_order_progress", "生产工单进度巡检", CATEGORY_PRODUCTION,
            "查询在制工单（已下达/生产中），有数据时通知生产负责人",
            "{\"owner\":1}", config.toString());
    }

    /* ==================== 图构建工具 ==================== */

    /** 组装模板元数据 */
    private Map<String, Object> template(String key, String name, String category,
                                         String description, String inputExample, String configJson) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("key", key);
        t.put("name", name);
        t.put("category", category);
        t.put("description", description);
        t.put("inputExample", inputExample);
        t.put("configJson", configJson);
        return t;
    }

    /** 构建画布节点（config_json 契约: {id, type, position, data:{label, config}}） */
    private ObjectNode node(String id, String type, double[] position, String label, ObjectNode config) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", id);
        n.put("type", type);
        n.putArray("position").add(position[0]).add(position[1]);
        ObjectNode data = n.putObject("data");
        data.put("label", label);
        data.set("config", config);
        return n;
    }

    /** 构建画布边（sourceHandle/targetHandle 对齐 outputs/main/0 编码） */
    private ObjectNode edge(String id, String source, String sourceHandle, String target, String targetHandle) {
        ObjectNode e = MAPPER.createObjectNode();
        e.put("id", id);
        e.put("source", source);
        e.put("sourceHandle", sourceHandle);
        e.put("target", target);
        e.put("targetHandle", targetHandle);
        return e;
    }

    /** 组装 config_json: {nodes: [...], edges: [...]} */
    private ObjectNode buildGraph(Map<String, ObjectNode> nodes, List<ObjectNode> edges) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode nodesNode = root.putArray("nodes");
        nodes.values().forEach(nodesNode::add);
        ArrayNode edgesNode = root.putArray("edges");
        edges.forEach(edgesNode::add);
        return root;
    }
}

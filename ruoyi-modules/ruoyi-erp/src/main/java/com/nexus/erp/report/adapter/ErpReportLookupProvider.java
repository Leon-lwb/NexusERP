package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@DS("erp")
public class ErpReportLookupProvider implements ReportLookupProvider {
    private record Rule(
            ReportLookupBinding binding, String table, String key, String code, String label) {}

    private static Rule rule(
            String dataset,
            String parameter,
            String label,
            String permission,
            String table,
            String key,
            String code,
            String name) {
        return new Rule(
                new ReportLookupBinding(dataset, 1, parameter, label, permission),
                table,
                key,
                code,
                name);
    }

    private static final List<Rule> RULES =
            List.of(
                    rule(
                            "ERP_PURCHASE_ORDER_DOCUMENT",
                            "orderId",
                            "采购订单",
                            "pu:order:query",
                            "pu_purchase_order",
                            "order_id",
                            "order_no",
                            "vendor_name"),
                    rule(
                            "ERP_DELIVERY_DOCUMENT",
                            "issueId",
                            "送货单",
                            "wm:salesIssue:query",
                            "wm_sales_issue",
                            "issue_id",
                            "issue_code",
                            "client_name"),
                    rule(
                            "ERP_SALES_EXECUTION",
                            "clientId",
                            "客户",
                            "bd:client:list",
                            "bd_client",
                            "client_id",
                            "client_code",
                            "client_name"),
                    rule(
                            "ERP_PURCHASE_EXECUTION",
                            "vendorId",
                            "供应商",
                            "bd:vendor:list",
                            "bd_vendor",
                            "vendor_id",
                            "vendor_code",
                            "vendor_name"),
                    rule(
                            "ERP_STOCK_BALANCE",
                            "warehouseId",
                            "仓库",
                            "wm:warehouse:list",
                            "wm_warehouse",
                            "warehouse_id",
                            "warehouse_code",
                            "warehouse_name"),
                    rule(
                            "ERP_STOCK_BALANCE",
                            "materialCode",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_code",
                            "material_code",
                            "material_name"));
    private final JdbcTemplate jdbc;

    public ErpReportLookupProvider(JdbcTemplate jdbc) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));
        this.jdbc.setQueryTimeout(10);
    }

    public List<ReportLookupBinding> bindings() {
        return RULES.stream().map(Rule::binding).toList();
    }

    public Result query(ReportLookupBinding binding, ReportExecutionContext context, Query query) {
        var rule =
                RULES.stream()
                        .filter(r -> r.binding().equals(binding))
                        .findFirst()
                        .orElseThrow(
                                () -> new DatasetAccessException("LOOKUP_UNSUPPORTED", "选择器未安装"));
        if (!context.actor().has(binding.permission()))
            throw new DatasetAccessException("SCOPE_DENIED", "缺少选择器业务权限");
        context.cancellation().check();
        var scopes = new ArrayList<>(context.scopes());
        scopes.add(context.actor().scopeFor(binding.permission()));
        var clause =
                ReportSqlScope.where(
                        "p",
                        new ReportExecutionContext(
                                context.actor(), scopes, context.cancellation()));
        String where =
                clause.sql()
                        + " AND (p."
                        + rule.code()
                        + " LIKE ? ESCAPE '!' OR p."
                        + rule.label()
                        + " LIKE ? ESCAPE '!')";
        var values = new ArrayList<Object>(clause.parameters());
        String pattern =
                "%"
                        + query.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_")
                        + "%";
        values.add(pattern);
        values.add(pattern);
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + rule.table() + " p WHERE " + where,
                        Long.class,
                        values.toArray());
        values.add(query.size());
        values.add((long) (query.page() - 1) * query.size());
        var options =
                jdbc.query(
                        "SELECT p."
                                + rule.key()
                                + " lookup_value,p."
                                + rule.code()
                                + " lookup_code,p."
                                + rule.label()
                                + " lookup_label FROM "
                                + rule.table()
                                + " p WHERE "
                                + where
                                + " ORDER BY p."
                                + rule.key()
                                + " LIMIT ? OFFSET ?",
                        (r, i) ->
                                new Option(
                                        r.getString("lookup_value"),
                                        r.getString("lookup_code")
                                                + " · "
                                                + Objects.toString(
                                                        r.getString("lookup_label"), "")),
                        values.toArray());
        context.cancellation().check();
        return new Result(options, count == null ? 0 : count);
    }
}

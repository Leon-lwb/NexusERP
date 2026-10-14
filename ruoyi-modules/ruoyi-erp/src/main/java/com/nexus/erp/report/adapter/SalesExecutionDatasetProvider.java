package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 代码维护的业务只读投影，物理标识符不会来自报表定义。 */
@Service
@DS("erp")
public class SalesExecutionDatasetProvider extends JdbcDatasetProvider {
    /**
     * 初始化销售执行数据集提供者并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     */
    public SalesExecutionDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "so_sales_order_header s", "s", "s.so_id", columns());
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @return 当前处理返回的固定结果
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_SALES_EXECUTION",
                "销售执行",
                1,
                "so:orderheader:list",
                false,
                List.of(
                        new DatasetField("orderId", "销售订单ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "orderNo", "销售订单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "clientName", "客户", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "orderDate", "订单日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "promiseDate", "承诺交期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "totalAmount",
                                "订单金额",
                                ValueType.DECIMAL,
                                "so:orderheader:query",
                                List.of("currency"),
                                true),
                        new DatasetField("currency", "币种", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "status", "执行状态", ValueType.STRING, null, List.of(), true)),
                List.of(
                        new DatasetParameter(
                                "fromDate", "开始日期", ValueType.DATE, false, "MONTHS_AGO_6"),
                        new DatasetParameter("toDate", "结束日期", ValueType.DATE, false, "TODAY"),
                        new DatasetParameter("clientId", "客户ID", ValueType.ID, false, null)),
                List.of(
                        "orderNo",
                        "clientName",
                        "orderDate",
                        "promiseDate",
                        "totalAmount",
                        "currency",
                        "status"));
    }

    /**
     * 声明逻辑字段到可信投影的映射。
     *
     * @return 当前处理返回的固定结果
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("orderId", "s.so_id");
        values.put("orderNo", "s.so_no");
        values.put("clientName", "s.client_name");
        values.put("orderDate", "s.order_date");
        values.put("promiseDate", "s.promise_date");
        values.put("totalAmount", "s.total_amount");
        values.put("currency", "s.currency");
        values.put("status", "s.order_status");
        return values;
    }

    /**
     * 生成契约允许的绑定筛选条件。
     *
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        filters.addAll(dates(query, "s.order_date"));
        equal(filters, query, "clientId", "s.client_id");
        filters.add(
                new Filter("s.order_status IS NULL OR s.order_status <> 'CANCELLED'", List.of()));
        return filters;
    }
}

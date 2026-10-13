package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Code-owned read-only business projection; raw identifiers never come from report JSON. */
@Service
@DS("erp")
public class SalesExecutionDatasetProvider extends JdbcDatasetProvider {
    public SalesExecutionDatasetProvider(JdbcTemplate jdbc){super(jdbc,definition(),"so_sales_order_header s","s","s.so_id",columns());}
    private static DatasetDescriptor definition(){return new DatasetDescriptor("ERP_SALES_EXECUTION","销售执行",1,"so:orderheader:list",false,List.of(
        new DatasetField("orderId","销售订单ID",ValueType.ID,null,List.of(),true),
        new DatasetField("orderNo","销售订单号",ValueType.STRING,null,List.of(),true),
        new DatasetField("clientName","客户",ValueType.STRING,null,List.of(),true),
        new DatasetField("orderDate","订单日期",ValueType.DATE,null,List.of(),true),
        new DatasetField("promiseDate","承诺交期",ValueType.DATE,null,List.of(),true),
        new DatasetField("totalAmount","订单金额",ValueType.DECIMAL,"so:orderheader:query",List.of("currency"),true),
        new DatasetField("currency","币种",ValueType.STRING,null,List.of(),true),
        new DatasetField("status","执行状态",ValueType.STRING,null,List.of(),true)),List.of(
        new DatasetParameter("fromDate","开始日期",ValueType.DATE,false,"MONTHS_AGO_6"),
        new DatasetParameter("toDate","结束日期",ValueType.DATE,false,"TODAY"),
        new DatasetParameter("clientId","客户ID",ValueType.ID,false,null)),List.of("orderNo","clientName","orderDate","promiseDate","totalAmount","currency","status"));}
    private static Map<String,String> columns(){Map<String,String> values=new LinkedHashMap<>();
        values.put("orderId","s.so_id");
        values.put("orderNo","s.so_no");
        values.put("clientName","s.client_name");
        values.put("orderDate","s.order_date");
        values.put("promiseDate","s.promise_date");
        values.put("totalAmount","s.total_amount");
        values.put("currency","s.currency");
        values.put("status","s.order_status");
        return values;}
    protected List<Filter> filters(DatasetQuery query){List<Filter> filters=new ArrayList<>();
        filters.addAll(dates(query,"s.order_date"));
        equal(filters,query,"clientId","s.client_id");
        filters.add(new Filter("s.order_status IS NULL OR s.order_status <> 'CANCELLED'",List.of()));
        return filters;}
}

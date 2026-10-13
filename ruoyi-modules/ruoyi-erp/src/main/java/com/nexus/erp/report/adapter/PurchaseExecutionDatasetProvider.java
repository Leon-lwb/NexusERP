package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Code-owned read-only business projection; raw identifiers never come from report JSON. */
@Service
@DS("erp")
public class PurchaseExecutionDatasetProvider extends JdbcDatasetProvider {
    public PurchaseExecutionDatasetProvider(JdbcTemplate jdbc){super(jdbc,definition(),"pu_purchase_order p","p","p.order_id",columns());}
    private static DatasetDescriptor definition(){return new DatasetDescriptor("ERP_PURCHASE_EXECUTION","采购执行",1,"pu:order:list",false,List.of(
        new DatasetField("orderId","采购订单ID",ValueType.ID,null,List.of(),true),
        new DatasetField("orderNo","采购订单号",ValueType.STRING,null,List.of(),true),
        new DatasetField("vendorName","供应商",ValueType.STRING,null,List.of(),true),
        new DatasetField("orderDate","订单日期",ValueType.DATE,null,List.of(),true),
        new DatasetField("expectedDate","预计到货",ValueType.DATE,null,List.of(),true),
        new DatasetField("totalAmount","订单金额",ValueType.DECIMAL,"pu:order:query",List.of("currency"),true),
        new DatasetField("currency","币种",ValueType.STRING,null,List.of(),true),
        new DatasetField("status","执行状态",ValueType.STRING,null,List.of(),true)),List.of(
        new DatasetParameter("fromDate","开始日期",ValueType.DATE,false,"MONTHS_AGO_6"),
        new DatasetParameter("toDate","结束日期",ValueType.DATE,false,"TODAY"),
        new DatasetParameter("vendorId","供应商ID",ValueType.ID,false,null)),List.of("orderNo","vendorName","orderDate","expectedDate","totalAmount","currency","status"));}
    private static Map<String,String> columns(){Map<String,String> values=new LinkedHashMap<>();
        values.put("orderId","p.order_id");
        values.put("orderNo","p.order_no");
        values.put("vendorName","p.vendor_name");
        values.put("orderDate","p.order_date");
        values.put("expectedDate","p.expected_arrival_date");
        values.put("totalAmount","p.total_amount");
        values.put("currency","p.currency");
        values.put("status","p.status");
        return values;}
    protected List<Filter> filters(DatasetQuery query){List<Filter> filters=new ArrayList<>();
        filters.addAll(dates(query,"p.order_date"));
        equal(filters,query,"vendorId","p.vendor_id");
        filters.add(new Filter("p.status IS NULL OR p.status <> 'CANCELLED'",List.of()));
        return filters;}
}

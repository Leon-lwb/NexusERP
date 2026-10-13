package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Code-owned read-only business projection; raw identifiers never come from report JSON. */
@Service
@DS("erp")
public class InventoryBalanceDatasetProvider extends JdbcDatasetProvider {
    public InventoryBalanceDatasetProvider(JdbcTemplate jdbc){super(jdbc,definition(),"wm_material_stock s","s","s.stock_id",columns());}
    private static DatasetDescriptor definition(){return new DatasetDescriptor("ERP_STOCK_BALANCE","库存余额",1,"wm:stock:list",false,List.of(
        new DatasetField("stockId","库存ID",ValueType.ID,null,List.of(),true),
        new DatasetField("materialCode","物料编码",ValueType.STRING,null,List.of(),true),
        new DatasetField("materialName","物料名称",ValueType.STRING,null,List.of(),true),
        new DatasetField("warehouseName","仓库",ValueType.STRING,null,List.of(),true),
        new DatasetField("unitName","单位",ValueType.STRING,null,List.of(),true),
        new DatasetField("quantity","库存数量",ValueType.DECIMAL,null,List.of("materialCode","unitName"),true),
        new DatasetField("reservedQuantity","预留数量",ValueType.DECIMAL,null,List.of("materialCode","unitName"),true),
        new DatasetField("unitCost","单位成本",ValueType.DECIMAL,"wm:stock:query",List.of(),false),
        new DatasetField("totalCost","库存成本",ValueType.DECIMAL,"wm:stock:query",List.of(),false)),List.of(
        new DatasetParameter("warehouseId","仓库ID",ValueType.ID,false,null),
        new DatasetParameter("materialCode","物料编码",ValueType.STRING,false,null)),List.of("materialCode","materialName","warehouseName","quantity","unitName"));}
    private static Map<String,String> columns(){Map<String,String> values=new LinkedHashMap<>();
        values.put("stockId","s.stock_id");
        values.put("materialCode","s.material_code");
        values.put("materialName","s.material_name");
        values.put("warehouseName","s.warehouse_name");
        values.put("unitName","s.base_uom_name");
        values.put("quantity","s.stock_quantity");
        values.put("reservedQuantity","s.reserved_quantity");
        values.put("unitCost","s.unit_cost");
        values.put("totalCost","s.total_cost");
        return values;}
    protected List<Filter> filters(DatasetQuery query){List<Filter> filters=new ArrayList<>();
        equal(filters,query,"warehouseId","s.warehouse_id");
        equal(filters,query,"materialCode","s.material_code");
        return filters;}
}

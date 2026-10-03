package com.nexus.erp.pu.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import java.util.*;
import java.util.function.Supplier;

/** 使用实际独占连接的元数据，而非安装 SQL 或调用方提供的 verified/数量标志。 */
public final class PuCertifiedPurchaseLifecycleSourceGate {
    private static final Set<String> ALLOCATION=Set.of("pu_certified_purchase_command","pu_requirement_allocation_ledger","pu_requirement_allocation_event");
    private static final Set<String> EFFECTS=Set.of("pu_certified_purchase_command","pu_requirement_allocation_ledger","pu_requirement_allocation_event",
        "wm_material_receive","wm_material_receive_line","wm_material_stock_tx","pu_purchase_return","pu_purchase_return_line");
    private static final Set<String> QUANTITIES=Set.of("wm_material_receive_line.quantity_received","wm_material_stock_tx.tx_quantity",
        "wm_material_stock_tx.before_quantity","wm_material_stock_tx.after_quantity","pu_purchase_return_line.return_quantity");
    private static final Map<String,List<List<String>>> KEYS=Map.of(
        "pu_certified_purchase_command",List.of(List.of("tenant_id","request_key")),
        "pu_requirement_allocation_ledger",List.of(List.of("tenant_id","allocation_key")),
        "pu_requirement_allocation_event",List.of(List.of("tenant_id","event_key"),List.of("tenant_id","allocation_id","event_sequence")));
    private final PuCertifiedPurchaseLifecycleMapper mapper;
    private final PuRequirementErpTransactionQualification qualification;
    public PuCertifiedPurchaseLifecycleSourceGate(PuCertifiedPurchaseLifecycleMapper mapper,PuRequirementErpTransactionQualification qualification) {
        this.mapper=Objects.requireNonNull(mapper);this.qualification=Objects.requireNonNull(qualification);
    }
    private static final Set<String> ORDER_TABLES=Set.of("pu_purchase_order","pu_purchase_order_line","pu_certified_purchase_order_terms",
        "pu_certified_purchase_order_terms_confirmation","pu_certified_purchase_order_operation");
    private static final Map<String,List<List<String>>> ORDER_KEYS=Map.of(
        "pu_certified_purchase_order_terms",List.of(List.of("quote_id"),List.of("tenant_id","request_key"),List.of("tenant_id","order_id","quote_version")),
        "pu_certified_purchase_order_terms_confirmation",List.of(List.of("confirmation_id"),List.of("tenant_id","request_key"),List.of("tenant_id","quote_id")),
        "pu_certified_purchase_order_operation",List.of(List.of("command_id"),List.of("tenant_id","request_key"),List.of("tenant_id","order_id","operation")));
    private record ColumnSpec(String type,int precision,int scale,long length,boolean notNull) { }
    private static final Map<String,ColumnSpec> ORDER_COLUMNS=orderColumns();
    private static Map<String,ColumnSpec> orderColumns() {
        Map<String,ColumnSpec> m=new LinkedHashMap<>();
        columns(m,"pu_certified_purchase_order_terms","bigint",0,0,0,true,"quote_id,order_id,entered_by,vendor_id,material_id,base_uom_id");
        columns(m,"pu_certified_purchase_order_terms","int",0,0,0,true,"quote_version");
        columns(m,"pu_certified_purchase_order_terms","datetime",0,0,0,true,"entered_at");
        columns(m,"pu_certified_purchase_order_terms","decimal",18,4,0,true,"quantity,unit_price_excluding_tax");
        columns(m,"pu_certified_purchase_order_terms","decimal",10,4,0,true,"tax_rate");
        columns(m,"pu_certified_purchase_order_terms","decimal",18,6,0,true,"exchange_rate");
        columns(m,"pu_certified_purchase_order_terms","date",0,0,0,true,"promised_arrival_date,valid_from,valid_until");
        columns(m,"pu_certified_purchase_order_terms","varchar",0,0,128,true,"supplier_quote_reference,supplier_contact");
        columns(m,"pu_certified_purchase_order_terms","varchar",0,0,255,true,"evidence_reference,payment_terms,delivery_terms");
        columns(m,"pu_certified_purchase_order_terms_confirmation","bigint",0,0,0,true,"confirmation_id,quote_id,actor_id");
        columns(m,"pu_certified_purchase_order_terms_confirmation","datetime",0,0,0,true,"confirmed_at");
        columns(m,"pu_certified_purchase_order_terms_confirmation","varchar",0,0,500,true,"review_note");
        columns(m,"pu_certified_purchase_order_operation","bigint",0,0,0,true,"command_id,order_id,actor_id");
        columns(m,"pu_certified_purchase_order_operation","int",0,0,0,true,"expected_version,result_version");
        columns(m,"pu_certified_purchase_order_operation","bigint",0,0,0,false,"quote_id");
        columns(m,"pu_certified_purchase_order_operation","date",0,0,0,false,"expected_arrival_date");
        columns(m,"pu_certified_purchase_order_operation","decimal",18,2,0,false,"total_amount,amount_excluding_tax,tax_amount");
        columns(m,"pu_certified_purchase_order_operation","datetime",0,0,0,true,"occurred_at");
        columns(m,"pu_purchase_order","bigint",0,0,0,false,"order_id,requisition_id,vendor_id,select_flow_id,certified_terms_id");
        columns(m,"pu_purchase_order","varchar",0,0,64,false,"order_no");
        columns(m,"pu_purchase_order","datetime",0,0,0,false,"expected_arrival_date");
        columns(m,"pu_purchase_order","varchar",0,0,16,false,"currency");
        columns(m,"pu_purchase_order","decimal",18,6,0,false,"exchange_rate");
        columns(m,"pu_purchase_order","decimal",18,2,0,false,"total_amount,amount_excluding_tax,tax_amount,discount_amount,freight_amount");
        columns(m,"pu_purchase_order","decimal",10,2,0,false,"discount_rate");
        columns(m,"pu_purchase_order","varchar",0,0,255,false,"payment_terms,delivery_terms");
        columns(m,"pu_purchase_order","varchar",0,0,32,false,"status");
        columns(m,"pu_purchase_order","varchar",0,0,20,false,"tenant_id");
        columns(m,"pu_purchase_order","char",0,0,1,false,"del_flag");
        columns(m,"pu_purchase_order_line","bigint",0,0,0,false,"line_id,order_id,requisition_line_id,material_id");
        columns(m,"pu_purchase_order_line","varchar",0,0,32,false,"unit,receive_status");
        columns(m,"pu_purchase_order_line","decimal",18,4,0,false,"order_quantity,received_quantity,invoiced_qty,returned_quantity,unit_price,unit_price_excluding_tax");
        columns(m,"pu_purchase_order_line","decimal",10,4,0,false,"tax_rate");
        columns(m,"pu_purchase_order_line","decimal",18,2,0,false,"line_amount,line_amount_excluding_tax,line_tax_amount");
        columns(m,"pu_purchase_order_line","varchar",0,0,20,false,"tenant_id");
        columns(m,"pu_purchase_order_line","char",0,0,1,false,"del_flag");
        columns(m,"pu_purchase_order","int",0,0,0,false,"certified_version");
        columns(m,"pu_certified_purchase_order_terms","varchar",0,0,20,true,"tenant_id");
        columns(m,"pu_certified_purchase_order_terms","varchar",0,0,128,true,"request_key,source_request_key");
        columns(m,"pu_certified_purchase_order_terms","char",0,0,64,true,"request_hash,source_fingerprint,catalog_fingerprint,order_fingerprint,evidence_sha256,quote_hash");
        columns(m,"pu_certified_purchase_order_terms","varchar",0,0,16,true,"currency");
        columns(m,"pu_certified_purchase_order_terms","varchar",0,0,64,true,"rounding_policy");
        columns(m,"pu_certified_purchase_order_terms_confirmation","varchar",0,0,20,true,"tenant_id");
        columns(m,"pu_certified_purchase_order_terms_confirmation","varchar",0,0,128,true,"request_key");
        columns(m,"pu_certified_purchase_order_terms_confirmation","char",0,0,64,true,"quote_hash,request_hash");
        columns(m,"pu_certified_purchase_order_terms_confirmation","varchar",0,0,64,true,"permission_code");
        columns(m,"pu_certified_purchase_order_operation","varchar",0,0,20,true,"tenant_id");
        columns(m,"pu_certified_purchase_order_operation","varchar",0,0,128,true,"request_key");
        columns(m,"pu_certified_purchase_order_operation","char",0,0,64,true,"request_hash,result_fingerprint");
        columns(m,"pu_certified_purchase_order_operation","char",0,0,64,false,"quote_hash");
        columns(m,"pu_certified_purchase_order_operation","varchar",0,0,32,true,"operation,result_status");
        return Collections.unmodifiableMap(m);
    }
    private static void columns(Map<String,ColumnSpec> m,String table,String type,int precision,int scale,long length,boolean notNull,String names) {
        for(String name:names.split(","))m.put(table+"."+name,new ColumnSpec(type,precision,scale,length,notNull));
    }
    /** 核验报价/确认/操作的每个字段及精确的原生金额定义，而非默认价格。 */
    public void requireOrderDefinitions() {
        qualification.requireSourceChainTransaction();
        require(query(mapper::pinOrderDefinitions)!=null,"整订单实际定义pin失败");
        requireAllocationDefinitions();
        List<TableDefinition> tables=query(mapper::orderTableDefinitions);Set<String> seen=new HashSet<>();
        require(tables!=null,"整订单实际表定义未知");
        for(TableDefinition t:tables)require(t!=null&&ORDER_TABLES.contains(t.tableName())&&seen.add(t.tableName())&&"InnoDB".equalsIgnoreCase(t.engine()),"订单/报价/审计/重放表不是唯一InnoDB来源");
        require(seen.equals(ORDER_TABLES),"整订单实际表全集缺失");
        List<OrderColumnDefinition> columns=query(mapper::orderColumnDefinitions);Set<String> found=new HashSet<>();require(columns!=null,"整订单实际列定义未知");
        for(OrderColumnDefinition c:columns) {
            require(c!=null,"整订单列定义含NULL");String key=c.tableName()+"."+c.columnName();ColumnSpec spec=ORDER_COLUMNS.get(key);if(spec==null)continue;
            require(found.add(key)&&spec.type().equalsIgnoreCase(c.dataType())&&(!spec.notNull()||"NO".equals(c.nullable())),"订单/报价列类型或非空合同不符");
            if("decimal".equals(spec.type()))require(Integer.valueOf(spec.precision()).equals(c.precision())&&Integer.valueOf(spec.scale()).equals(c.scale()),"真实金额/数量DECIMAL精度不符");
            if(spec.length()>0)require(Long.valueOf(spec.length()).equals(c.maxLength()),"永久请求键/摘要/条款字段长度不符");
        }
        require(found.equals(ORDER_COLUMNS.keySet()),"报价全字段/订单原生列合同缺失");
        requireUniqueKeys(query(mapper::orderUniqueDefinitions),ORDER_KEYS);
    }
    /** 核心操作仅需其实际可变占用来源，以及守卫已有的来源门禁。 */
    public void requireAllocationDefinitions() {
        qualification.requireSourceChainTransaction();
        require(query(mapper::pinAllocationDefinitions)!=null,"占用来源固定空读pin失败");
        requireTables(ALLOCATION);requireKeys();
    }
    /** 涵盖全部物理影响表，而非仅原始来源门禁的十七张表。 */
    public void requireDefinitions() {
        qualification.requireSourceChainTransaction();
        require(query(mapper::pinLifecycleDefinitions)!=null,"生命周期来源固定空读pin失败");
        requireTables(EFFECTS);requireKeys();
        List<NativeQuantityDefinition> columns=query(mapper::lifecycleQuantityDefinitions);
        require(columns!=null,"实际数量列定义未知");Set<String> seen=new HashSet<>();
        for(NativeQuantityDefinition column:columns) {
            require(column!=null,"实际数量列定义含NULL");String key=column.tableName()+"."+column.columnName();
            require(QUANTITIES.contains(key)&&seen.add(key)&&"decimal".equalsIgnoreCase(column.dataType())
                &&Integer.valueOf(18).equals(column.precision())&&Integer.valueOf(4).equals(column.scale()),"物理effect原生数量列不是DECIMAL(18,4)");
        }
        require(seen.equals(QUANTITIES),"实际effect数量列全集缺失");
    }
    /** 关闭/转单时，不得将无法加锁的下游表视为空来源。 */
    public void requireMovementDefinitions() {
        qualification.requireSourceChainTransaction();
        require(query(mapper::pinLifecycleDefinitions)!=null,"下游来源固定空读pin失败");
        requireTables(EFFECTS);requireKeys();
    }
    private void requireTables(Set<String> required) {
        List<TableDefinition> rows=query(mapper::lifecycleTableDefinitions);
        require(rows!=null,"实际来源表定义未知");Set<String> seen=new HashSet<>();
        for(TableDefinition row:rows) {
            require(row!=null,"实际来源表定义含NULL");if(!required.contains(row.tableName()))continue;
            require(seen.add(row.tableName())&&"InnoDB".equalsIgnoreCase(row.engine()),"生命周期来源表非InnoDB或定义重复");
        }
        require(seen.equals(required),"实际来源表全集缺失");
    }
    private void requireKeys() {
        requireUniqueKeys(query(mapper::lifecycleUniqueDefinitions),KEYS);
    }
    private static void requireUniqueKeys(List<IndexDefinition> rows,Map<String,List<List<String>>> keys) {
        require(rows!=null,"永久键实际索引未知");Map<String,List<IndexDefinition>> indices=new HashMap<>();
        for(IndexDefinition row:rows) {
            require(row!=null&&keys.containsKey(row.tableName())&&row.indexName()!=null&&row.nonUnique()!=null
                &&row.sequence()!=null&&row.sequence()>0&&row.columnName()!=null,"永久键索引定义未知");
            indices.computeIfAbsent(row.tableName()+":"+row.indexName(),k->new ArrayList<>()).add(row);
        }
        for(var table:keys.entrySet()) for(List<String> key:table.getValue()) {
            boolean found=false;
            for(List<IndexDefinition> parts:indices.values()) {
                if(!table.getKey().equals(parts.get(0).tableName())||parts.size()!=key.size())continue;
                parts.sort(Comparator.comparing(IndexDefinition::sequence));boolean matches=true;
                for(int i=0;i<parts.size();i++) {
                    IndexDefinition part=parts.get(i);
                    matches&=Integer.valueOf(0).equals(part.nonUnique())&&part.sequence()==i+1&&part.subPart()==null
                        &&key.get(i).equals(part.columnName());
                }
                found|=matches;
            }
            require(found,"永久占用/事件因果sequence/command缺少完整租户唯一键");
        }
    }
    private static <T> T query(Supplier<T> call) {
        try{return call.get();}catch(RuntimeException failure){throw new ServiceException("生命周期实际来源定义读取失败");}
    }
    private static void require(boolean condition,String message) {if(!condition)throw new ServiceException("可信采购生命周期拒绝："+message);}
}

package com.nexus.erp.pu.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import java.util.*;
import java.util.function.Supplier;

/** Actual owned-connection metadata, not installation SQL or caller-supplied verified/quantity flags. */
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
    /** Core operations need only their actual mutable allocation sources, plus the guard's existing source gate. */
    public void requireAllocationDefinitions() {
        qualification.requireSourceChainTransaction();
        require(query(mapper::pinAllocationDefinitions)!=null,"占用来源固定空读pin失败");
        requireTables(ALLOCATION);requireKeys();
    }
    /** All physical effect tables, not merely the original source gate's seventeen tables. */
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
    /** Closing/transferring must not treat an un-lockable downstream table as an empty source. */
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
        List<IndexDefinition> rows=query(mapper::lifecycleUniqueDefinitions);
        require(rows!=null,"永久键实际索引未知");Map<String,List<IndexDefinition>> indices=new HashMap<>();
        for(IndexDefinition row:rows) {
            require(row!=null&&KEYS.containsKey(row.tableName())&&row.indexName()!=null&&row.nonUnique()!=null
                &&row.sequence()!=null&&row.sequence()>0&&row.columnName()!=null,"永久键索引定义未知");
            indices.computeIfAbsent(row.tableName()+":"+row.indexName(),k->new ArrayList<>()).add(row);
        }
        for(var table:KEYS.entrySet()) for(List<String> key:table.getValue()) {
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

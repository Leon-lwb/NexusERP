package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.ErpPurchaseSourceSchemaMapper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.function.Supplier;

/** 事务性表和服务器锁语义必须先证明，再允许技术guard写入及全局来源认证。 */
@Service
@DS("erp")
public class ErpPurchaseSourceSchemaVerifier {
    static final Set<String> TABLES = Set.of("bd_material","bd_unit_of_measure","bd_vendor","bd_material_vendor",
        "wm_material_stock","wm_warehouse","wm_storage_area","wm_storage_location",
        "prd_material_requirement","prd_material_requirement_detail","pu_purchase_requisition","pu_purchase_requisition_line",
        "pu_purchase_order","pu_purchase_order_line","pu_requirement_material_guard","pu_requirement_allocation_ledger","pu_requirement_allocation_event");
    // 核验的是实际原生列，不是 Java BigDecimal 类型；换算源沿现行安装合同采用 DECIMAL(18,6)。
    private static final Map<String, Integer> QUANTITY_SCALES = Map.ofEntries(
        Map.entry("bd_material.safety_stock", 4),
        Map.entry("bd_unit_of_measure.conversion_rate", 6),
        Map.entry("bd_material_vendor.verified_moq_qty", 4),
        Map.entry("bd_material_vendor.order_multiple", 4),
        Map.entry("bd_material_vendor.units_per_pack", 4),
        Map.entry("wm_material_stock.stock_quantity", 4),
        Map.entry("wm_material_stock.reserved_quantity", 4),
        Map.entry("prd_material_requirement_detail.required_quantity", 4),
        Map.entry("prd_material_requirement_detail.issued_quantity", 4),
        Map.entry("pu_purchase_requisition_line.apply_quantity", 4),
        Map.entry("pu_purchase_requisition_line.approved_quantity", 4),
        Map.entry("pu_purchase_order_line.order_quantity", 4),
        Map.entry("pu_purchase_order_line.received_quantity", 4),
        Map.entry("pu_purchase_order_line.returned_quantity", 4),
        Map.entry("pu_requirement_allocation_ledger.covered_demand_base_qty", 4),
        Map.entry("pu_requirement_allocation_ledger.occupied_base_qty", 4),
        Map.entry("pu_requirement_allocation_ledger.fulfilled_base_qty", 4),
        Map.entry("pu_requirement_allocation_ledger.released_base_qty", 4),
        Map.entry("pu_requirement_allocation_event.occupied_base_before", 4),
        Map.entry("pu_requirement_allocation_event.occupied_base_after", 4));
    private final ErpPurchaseSourceSchemaMapper mapper;
    private final PuRequirementErpTransactionQualification qualification;
    public ErpPurchaseSourceSchemaVerifier(ErpPurchaseSourceSchemaMapper mapper, PuRequirementErpTransactionQualification qualification) {
        this.mapper = Objects.requireNonNull(mapper); this.qualification = Objects.requireNonNull(qualification);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireTransactionalSources() {
        qualification.requireSourceChainTransaction();
        // 实际表定义必须先在同事务读取并持锁，不能仅查询信息模式后留出 DDL 更换引擎的窗口。
        var pins = query(mapper::pinTableDefinitions);
        require(pins != null && pins.isEmpty(), "表定义固定空结果读取失败");
        var rows = query(mapper::selectTableEngines);
        require(rows != null, "来源表元数据缺失");
        Set<String> observed = new HashSet<>();
        for (var row : rows) {
            require(row != null && TABLES.contains(row.tableName()) && observed.add(row.tableName())
                && row.engine() != null && "INNODB".equals(row.engine().toUpperCase(Locale.ROOT)),
                "来源表缺失、重复或不是已核事务性存储引擎");
        }
        require(observed.equals(TABLES), "完整来源表全集缺失");
        require("REPEATABLE-READ".equals(query(mapper::selectSessionIsolation)), "实际服务器会话不是可重复读");
        String version = query(mapper::selectServerVersion);
        require(version != null && !version.toLowerCase(Locale.ROOT).contains("mariadb"), "服务器版本未知或未支持");
        if (version.matches("5\\.7\\.[0-9]+(?:[-+].*)?")) {
            String patch = version.substring(4).split("[-+]",2)[0];
            try { require(Integer.parseInt(patch) >= 20, "服务器隔离变量版本未支持"); }
            catch (NumberFormatException invalid) { throw new ServiceException("采购来源服务器版本未核验"); }
            BigDecimal unsafe = query(mapper::selectUnsafeGapLockFlag57);
            require(unsafe != null && unsafe.compareTo(BigDecimal.ZERO) == 0, "来源范围锁安全模式未核验");
        } else {
            require(version.matches("8\\.(?:0|4)\\.[0-9]+(?:[-+].*)?"), "服务器锁语义版本未支持");
        }
        requireNativeQuantities();
    }

    private void requireNativeQuantities() {
        var rows = query(mapper::selectNativeQuantityColumns);
        require(rows != null, "数量原生列元数据缺失");
        Set<String> observed = new HashSet<>();
        for (var row : rows) {
            require(row != null, "数量原生列元数据未知");
            String key = row.tableName() + "." + row.columnName();
            require(QUANTITY_SCALES.containsKey(key) && observed.add(key)
                && "decimal".equalsIgnoreCase(row.dataType()) && Integer.valueOf(18).equals(row.numericPrecision())
                && QUANTITY_SCALES.get(key).equals(row.numericScale()),
                "数量来源列缺失、重复或不是已核原生精确类型");
        }
        require(observed.equals(QUANTITY_SCALES.keySet()), "完整数量来源列全集缺失");
    }

    private static <T> T query(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException failed) { throw new ServiceException("采购来源表元数据读取失败"); }
    }
    private static void require(boolean value,String reason) {
        if (!value) throw new ServiceException("采购来源表认证拒绝：" + reason);
    }
}

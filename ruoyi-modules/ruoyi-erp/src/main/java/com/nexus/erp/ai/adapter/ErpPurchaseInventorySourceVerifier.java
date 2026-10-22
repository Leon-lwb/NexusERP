package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper.DatabaseClock;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper.QuantityColumn;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 在已证明物理 ERP 归属的当前写事务内认证实际库存与完整仓储父链。
 * 规格见 doc/ai/plans/2026-10-02-可信采购来源与统一事务续接实施计划.md。
 * 不接收外部原始事实、数量、时间或核验布尔，不借连接、新开事务或补绑资源。
 * 调用方须经服务代理进入，并先取得单物料 guard；行锁只随该事务持有。
 *
 * 仓库状态采用现有 WmStorageAreaServiceImpl 的 status=1；冻结采用现有
 * WmProductionIssueServiceImpl 的 0=未冻结、1=冻结，父节点停用仍整池拒绝。
 * 数量必须实际存储为 DECIMAL(18,4)，旧 DOUBLE 库不能靠 CAST 或本机演示数据升级。
 * DATETIME 只支持已明确为 +08:00 的数据库会话；不证明历史存储口径已人工核实。
 *
 * 当前仅支持真实、已核验的根基本单位。派生基本单位没有在本类独立核验根链，
 * 故保守拒绝，不因名称、当前转换率或调用方标记猜测单位身份。
 * 入库时间与有效期均必须明确：NULL/零日期/未来入库/已过期库存整池拒绝，
 * 包括冻结行；不从保质期、零数量或历史记录猜测有效期。生产日期若存在须不晚于入库。
 * 等待全部来源锁后重新取数据库时钟，避免等待期间过期仍被当作及时供给。
 * 本结果不是跨事务授权、流水对账、全局额度或采购生命周期已经认证的证据。
 */
@Service
@DS("erp")
public class ErpPurchaseInventorySourceVerifier {
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private static final Set<String> QUANTITY_COLUMNS = Set.of("bd_material.safety_stock",
        "wm_material_stock.stock_quantity", "wm_material_stock.reserved_quantity");

    private final ErpCertifiedSourceBirthReader birthReader;
    private final ErpPurchaseInventorySourceMapper mapper;
    private final PuRequirementErpTransactionQualification transactionQualification;

    public ErpPurchaseInventorySourceVerifier(ErpPurchaseInventorySourceMapper mapper,
                                              PuRequirementErpTransactionQualification transactionQualification) {
        this(mapper,transactionQualification,null);
    }

    @Autowired
    public ErpPurchaseInventorySourceVerifier(
            ErpPurchaseInventorySourceMapper mapper,
            PuRequirementErpTransactionQualification transactionQualification,
            ErpCertifiedSourceBirthReader birthReader) {
        this.birthReader=birthReader;
        this.mapper = Objects.requireNonNull(mapper);
        this.transactionQualification = Objects.requireNonNull(transactionQualification);
    }

    /**
     * 自行当前读并认证一个物料的全量未明确删除库存；任一未知来源拒绝整个物料池。
     * 租户和物理 ERP 写事务资格先于任何 Mapper，绝不自行开启、补绑或升级事务。
     *
     * @param tenantId 必须与已启用的当前请求租户完全一致
     * @param materialId 必须是实际物料的完整正数主键
     * @return 仅供当前持锁事务消费的不可变来源快照，不代表已授权采购
     */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public InventorySnapshot verifyLocked(String tenantId, Long materialId) {
        require(TenantHelper.isEnable() && tenantId != null && !tenantId.isBlank()
            && tenantId.equals(TenantHelper.getTenantId()), "租户未启用或与请求租户不符");
        transactionQualification.requireParticipatingWritableErpTransaction();
        require(positive(materialId), "物料身份无效");

        DatabaseClock initialClock = select(mapper::selectDatabaseClock);
        Instant startedAt = databaseInstant(initialClock);
        Map<String, String> versions = new LinkedHashMap<>();

        BdMaterial material = select(() -> mapper.lockMaterial(tenantId, materialId));
        require(material != null && tenantId.equals(material.getTenantId())
            && materialId.equals(material.getMaterialId()) && enabled(material.getStatus())
            && "0".equals(material.getDelFlag()) && positive(material.getBaseUomId()), "物料主数据缺失或无效");
        Long baseUomId = material.getBaseUomId();
        BdUnitOfMeasure unit = select(() -> mapper.lockUnit(tenantId, baseUomId));
        require(unit != null && tenantId.equals(unit.getTenantId()) && baseUomId.equals(unit.getUomId())
            && enabled(unit.getStatus()) && "0".equals(unit.getDelFlag()), "基本单位缺失或无效");
        Integer scale = unit.getQuantityScale();
        require(scale != null && scale >= 0 && scale <= 4, "基本单位数量精度未知或不支持");
        require(enabled(unit.getIsBase()) && (unit.getPrimaryId() == null || baseUomId.equals(unit.getPrimaryId()))
            && unit.getCategory() != null && !unit.getCategory().isBlank()
            && unit.getConversionRate() != null && unit.getConversionRate().compareTo(BigDecimal.ONE) == 0,
            "基本单位不是已独立支持的根单位，禁止猜测派生根链");
        require(unit.getConversionVersion() != null && !unit.getConversionVersion().isBlank(), "单位换算版本未知");
        Instant verifiedAt = requiredDate(unit.getConversionVerifiedAt(), "单位核验时间未知");
        BigDecimal safety = quantity(material.getSafetyStock(), scale, "安全库存未知或不精确");

        List<WmMaterialStock> stocks = select(() -> mapper.lockStocks(tenantId, materialId));
        require(stocks != null, "库存来源读取未知，不能作为可信零");
        if(stocks.isEmpty()) {
            require(birthReader!=null,"库存来源缺失且没有持久出生读取器，空行不能作为可信零");
            var born=birthReader.verifyInitialEmptyLocked(tenantId,materialId);
            require(baseUomId.equals(born.baseUomId())&&scale.equals(born.quantityScale())
                &&unit.getConversionVersion().equals(born.conversionVersion())&&verifiedAt.equals(Instant.ofEpochMilli(born.unitVerifiedAt())),
                "当前库存单位与持久出生版本不符");
            versions.put("quantity-schema",quantitySchema(select(mapper::selectQuantityColumns)));
            versions.put("erp_certified_source_birth:"+born.birthId(),born.birthFingerprint());
            versions.put("bd_material:"+materialId,fingerprint(tenantId,materialId,baseUomId,safety,material.getStatus(),material.getDelFlag()));
            versions.put("bd_unit_of_measure:"+baseUomId,"conversionVersion="+unit.getConversionVersion()+";"+fingerprint(unit.getTenantId(),baseUomId,
                unit.getIsBase(),unit.getPrimaryId(),unit.getCategory(),unit.getConversionRate(),scale,unit.getConversionVersion(),verifiedAt,unit.getStatus(),unit.getDelFlag()));
            var finalClock=select(mapper::selectDatabaseClock);var snapshotAt=databaseInstant(finalClock);
            require(!snapshotAt.isBefore(startedAt)&&!verifiedAt.isAfter(snapshotAt),"出生库存数据库时点或单位核验时点不符");
            // 数量零来自同事务永久出生与完整空来源观察，不能把无出生的空表补零。
            return new InventorySnapshot(tenantId,materialId,baseUomId,scale,snapshotAt,finalClock.databaseTimeZone(),ZERO,ZERO,safety,ZERO,Set.of(),versions,born);
        }
        Set<Long> stockIds = new TreeSet<>();
        Set<Long> warehouseIds = new TreeSet<>();
        Set<Long> areaIds = new TreeSet<>();
        Set<Long> locationIds = new TreeSet<>();
        for (WmMaterialStock stock : stocks) {
            require(stock != null && tenantId.equals(stock.getTenantId()) && materialId.equals(stock.getMaterialId())
                && positive(stock.getStockId()) && stockIds.add(stock.getStockId())
                && "0".equals(stock.getDelFlag()) && stock.getVersion() != null && stock.getVersion() >= 0,
                "库存身份、删除状态或版本未知");
            require(baseUomId.toString().equals(stock.getBaseUomId()), "库存单位不是物料基本单位的规范身份");
            BigDecimal amount = quantity(stock.getStockQuantity(), scale, "在库数量未知或不精确");
            BigDecimal reservation = quantity(stock.getReservedQuantity(), scale, "预留数量未知或不精确");
            require(reservation.compareTo(amount) <= 0 && binary(stock.getFrozenFlag()), "库存预留超在库或冻结值未知");
            require(positive(stock.getWarehouseId()) && positive(stock.getAreaId())
                && (stock.getLocationId() == null || positive(stock.getLocationId())), "库存仓储父链身份缺失或无效");
            warehouseIds.add(stock.getWarehouseId());
            areaIds.add(stock.getAreaId());
            if (stock.getLocationId() != null) locationIds.add(stock.getLocationId());
        }

        // 固定表序，且每次传入去重后的升序主键，SQL 同样按主键升序锁定。
        List<Long> warehouseOrder = List.copyOf(warehouseIds);
        Map<Long, WmWarehouse> warehouses = nodes(select(() -> mapper.lockWarehouses(tenantId, warehouseOrder)),
            warehouseIds, tenantId, WmWarehouse::getWarehouseId, WmWarehouse::getTenantId, WmWarehouse::getDelFlag, "仓库");
        List<Long> areaOrder = List.copyOf(areaIds);
        Map<Long, WmStorageArea> areas = nodes(select(() -> mapper.lockAreas(tenantId, areaOrder)), areaIds,
            tenantId, WmStorageArea::getAreaId, WmStorageArea::getTenantId, WmStorageArea::getDelFlag, "库区");
        List<Long> locationOrder = List.copyOf(locationIds);
        Map<Long, WmStorageLocation> locations = nodes(locationOrder.isEmpty() ? List.of()
            : select(() -> mapper.lockLocations(tenantId, locationOrder)), locationIds, tenantId,
            WmStorageLocation::getLocationId, WmStorageLocation::getTenantId, WmStorageLocation::getDelFlag, "储位");

        for (WmMaterialStock stock : stocks) {
            WmWarehouse warehouse = warehouses.get(stock.getWarehouseId());
            WmStorageArea area = areas.get(stock.getAreaId());
            require(enabled(warehouse.getStatus()), "仓库未启用或状态未知");
            require(enabled(area.getEnableFlag()) && binary(area.getFrozenFlag()) && binary(area.getLocationFlag())
                && stock.getWarehouseId().equals(area.getWarehouseId()), "库区未启用、旗号未知或父仓库不符");
            require(!enabled(area.getLocationFlag()) || stock.getLocationId() != null, "启用储位管理的库区缺少储位");
            if (stock.getLocationId() != null) {
                WmStorageLocation location = locations.get(stock.getLocationId());
                require(enabled(location.getEnableFlag()) && binary(location.getFrozenFlag())
                    && stock.getWarehouseId().equals(location.getWarehouseId())
                    && stock.getAreaId().equals(location.getAreaId()), "储位未启用、旗号未知或父链不符");
            }
        }

        // 业务表当前读已持锁后再核验实际列，避免锁前元数据与实际读表类型之间的变更窗口。
        versions.put("quantity-schema", quantitySchema(select(mapper::selectQuantityColumns)));
        DatabaseClock finalClock = select(mapper::selectDatabaseClock);
        Instant snapshotAt = databaseInstant(finalClock);
        require(!snapshotAt.isBefore(startedAt) && !verifiedAt.isAfter(snapshotAt), "数据库时钟倒退或单位核验时间在未来");
        BigDecimal availableStock = ZERO;
        BigDecimal availableReserved = ZERO;
        BigDecimal frozenStock = ZERO;
        for (WmMaterialStock stock : stocks) {
            stockDates(stock, snapshotAt);
            WmStorageArea area = areas.get(stock.getAreaId());
            WmStorageLocation location = stock.getLocationId() == null ? null : locations.get(stock.getLocationId());
            boolean frozen = enabled(stock.getFrozenFlag()) || enabled(area.getFrozenFlag())
                || (location != null && enabled(location.getFrozenFlag()));
            BigDecimal amount = quantity(stock.getStockQuantity(), scale, "在库数量无效");
            BigDecimal reservation = quantity(stock.getReservedQuantity(), scale, "预留数量无效");
            if (frozen) {
                frozenStock = frozenStock.add(amount);
            } else {
                availableStock = availableStock.add(amount);
                availableReserved = availableReserved.add(reservation);
            }
            versions.put("wm_material_stock:" + stock.getStockId(), "version=" + stock.getVersion() + ";" + fingerprint(
                stock.getTenantId(), stock.getStockId(), stock.getMaterialId(), stock.getBaseUomId(), stock.getWarehouseId(),
                stock.getAreaId(), stock.getLocationId(), amount, reservation, stock.getFrozenFlag(), stock.getVersion(),
                stock.getDelFlag(), instant(stock.getProductionDate()), instant(stock.getRecptDate()), instant(stock.getExpireDate())));
        }
        versions.put("bd_material:" + materialId, fingerprint(tenantId, materialId, baseUomId, safety,
            material.getStatus(), material.getDelFlag()));
        versions.put("bd_unit_of_measure:" + baseUomId, "conversionVersion=" + unit.getConversionVersion() + ";"
            + fingerprint(unit.getTenantId(), baseUomId, unit.getIsBase(), unit.getPrimaryId(), unit.getCategory(),
                unit.getConversionRate(), scale, unit.getConversionVersion(), verifiedAt, unit.getStatus(), unit.getDelFlag()));
        warehouses.forEach((id, row) -> versions.put("wm_warehouse:" + id,
            fingerprint(row.getTenantId(), id, row.getStatus(), row.getDelFlag())));
        areas.forEach((id, row) -> versions.put("wm_storage_area:" + id, fingerprint(row.getTenantId(), id,
            row.getWarehouseId(), row.getEnableFlag(), row.getFrozenFlag(), row.getLocationFlag(), row.getDelFlag())));
        locations.forEach((id, row) -> versions.put("wm_storage_location:" + id, fingerprint(row.getTenantId(), id,
            row.getWarehouseId(), row.getAreaId(), row.getEnableFlag(), row.getFrozenFlag(), row.getDelFlag())));
        return new InventorySnapshot(tenantId, materialId, baseUomId, scale, snapshotAt, finalClock.databaseTimeZone(),
            quantity(availableStock, scale, "非冻结库存合计溢出"), quantity(availableReserved, scale, "非冻结预留合计溢出"),
            safety, quantity(frozenStock, scale, "冻结库存合计溢出"), stockIds, versions);
    }

    private static String quantitySchema(List<QuantityColumn> columns) {
        require(columns != null, "实际数量列元数据缺失");
        Map<String, QuantityColumn> actual = new TreeMap<>();
        for (QuantityColumn column : columns) {
            require(column != null, "数量列元数据含未知行");
            String key = column.tableName() + "." + column.columnName();
            require(QUANTITY_COLUMNS.contains(key) && actual.putIfAbsent(key, column) == null
                && "decimal".equals(column.dataType()) && Integer.valueOf(18).equals(column.numericPrecision())
                && Integer.valueOf(4).equals(column.numericScale()), "数量列不是实际 DECIMAL(18,4) 或元数据重复");
        }
        require(actual.keySet().equals(QUANTITY_COLUMNS), "实际数量列元数据不完整");
        return fingerprint(actual.values().toArray());
    }

    private static <T> Map<Long, T> nodes(List<T> rows, Set<Long> expected, String tenantId, Function<T, Long> id,
                                          Function<T, String> tenant, Function<T, String> deleted, String source) {
        require(rows != null, source + "来源缺失");
        Map<Long, T> actual = new LinkedHashMap<>();
        for (T row : rows) {
            require(row != null, source + "含未知行");
            Long key = id.apply(row);
            require(positive(key) && expected.contains(key) && tenantId.equals(tenant.apply(row))
                && "0".equals(deleted.apply(row)) && actual.putIfAbsent(key, row) == null,
                source + "身份、租户、删除状态或完整性不符");
        }
        require(actual.keySet().equals(expected), source + "来源不完整");
        return actual;
    }

    private static void stockDates(WmMaterialStock stock, Instant snapshotAt) {
        Instant received = requiredDate(stock.getRecptDate(), "库存入库时间未知");
        Instant expires = requiredDate(stock.getExpireDate(), "库存有效期未知");
        require(!received.isAfter(snapshotAt) && expires.isAfter(snapshotAt), "库存未来入库或已过期，整池拒绝");
        if (stock.getProductionDate() != null) {
            Instant produced = requiredDate(stock.getProductionDate(), "库存生产日期无效");
            require(!produced.isAfter(received), "库存生产日期晚于入库");
        }
    }

    private static BigDecimal quantity(BigDecimal value, int scale, String reason) {
        require(value != null && value.signum() >= 0 && value.compareTo(MAX_QUANTITY) <= 0, reason);
        try {
            value.setScale(scale, RoundingMode.UNNECESSARY);
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalid) {
            throw rejected(reason);
        }
    }

    private static Instant databaseInstant(DatabaseClock clock) {
        require(clock != null && "+08:00".equals(clock.databaseTimeZone())
            && clock.epochSeconds() != null && clock.epochSeconds().signum() > 0, "数据库时钟或明确 +08:00 会话时区未知");
        try {
            BigDecimal seconds = clock.epochSeconds();
            long whole = seconds.setScale(0, RoundingMode.DOWN).longValueExact();
            int nanos = seconds.subtract(BigDecimal.valueOf(whole)).movePointRight(9).intValueExact();
            return Instant.ofEpochSecond(whole, nanos);
        } catch (ArithmeticException | DateTimeException invalid) {
            throw rejected("数据库时间戳无效或不精确");
        }
    }

    private static Instant requiredDate(Date date, String reason) {
        require(date != null && date.getTime() > 0, reason);
        return instant(date);
    }

    private static Instant instant(Date date) {
        return date == null ? null : Instant.ofEpochMilli(date.getTime());
    }

    private static boolean enabled(Long value) {
        return Long.valueOf(1L).equals(value);
    }

    private static boolean binary(Long value) {
        return Long.valueOf(0L).equals(value) || enabled(value);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    /** 对固定源字段作有边界的内容指纹；无版本列的表不冒充持久版本或历史核验。 */
    private static String fingerprint(Object... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object field : fields) {
                if (field == null) {
                    digest.update((byte) 0);
                } else {
                    digest.update((byte) 1);
                    byte[] bytes = (field.getClass().getName() + ":" + field).getBytes(StandardCharsets.UTF_8);
                    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                    digest.update((byte) ':');
                    digest.update(bytes);
                }
            }
            return "sha256=" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("运行环境缺少标准 SHA-256 支持");
        }
    }

    /** 不保留底层异常为 cause，防止 Mapper/JDBC 故障泄露地址、凭据或业务 SQL。 */
    private static <T> T select(Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException failure) {
            throw rejected("ERP 库存来源读取失败");
        }
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw rejected(reason);
    }

    private static ServiceException rejected(String reason) {
        return new ServiceException("采购库存来源未核验：" + reason);
    }

    /**
     * quantity 为非冻结在库总量，reserved 仅为相同非冻结行的预留，均不重复扣减。 frozenQuantity 含库存行、库区或储位冻结导致排除的在库量；该行预留一并排除。
     * safety 为真实物料安全库存；所有数量统一为基本单位的精确四位小数。 stockIds 覆盖全部被读库存，含冻结行；sourceVersions 含真实库存版本、单位换算版本
     * 及其他来源内容指纹，不把无版本表的哈希当历史核验、完整性或跨事务有效证书。 不携带 Mapper 实体、Date 或可变集合，调用方不能通过自造此类型取得采购授权。
     */
    public record InventorySnapshot(
            String tenantId,
            Long materialId,
            Long baseUomId,
            Integer quantityScale,
            Instant snapshotAt,
            String databaseTimeZone,
            BigDecimal quantity,
            BigDecimal reserved,
            BigDecimal safety,
            BigDecimal frozenQuantity,
            Set<Long> stockIds,
            Map<String, String> sourceVersions,
            ErpCertifiedSourceBirthMapper.StoredBirth sourceBirth) {
        public InventorySnapshot(String tenantId,Long materialId,Long baseUomId,Integer quantityScale,Instant snapshotAt,
                String databaseTimeZone,BigDecimal quantity,BigDecimal reserved,BigDecimal safety,BigDecimal frozenQuantity,
                Set<Long> stockIds,Map<String,String> sourceVersions){
            this(tenantId,materialId,baseUomId,quantityScale,snapshotAt,databaseTimeZone,quantity,reserved,safety,frozenQuantity,stockIds,sourceVersions,null);
        }
        public InventorySnapshot {
            stockIds = Set.copyOf(stockIds);
            sourceVersions = Map.copyOf(sourceVersions);
        }
    }
}

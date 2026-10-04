package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseInventorySourceVerifier.InventorySnapshot;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 当前物理ERP写事务内，认证库存原始余额、完整流水及预留的具体MRP需求所有权。
 * 不接收调用方的库存、数量、时钟、原始行、需求子集或verified布尔；调用方须已锁单物料guard，
 * 并由既有专属ERP入口拥有可重复读事务；通用可写连接不是完整来源范围锁的归属证明。
 * 快照和行锁仅属于当前事务，不能保存为跨事务采购授权。
 *
 * 支持域：从RECEIVE_IN/WM_MATERIAL_RECEIVE明确零起点开始的原始入库头/行/批次分配链；
 * 出库及预留来源须为WM_PRODUCTION_ISSUE，经原始领料行→MRP明细/头→工单核对计划/BOM/母物料归属。
 * 入库/领料单必须单物料、根基本单位、非倒冲、原始数量及履约可完全复算；混物料单据、其他流水类型、
 * 销售预留、未知/删除原始行和缺可证明起点的历史库存一律拒绝。
 * CONSUME_RESERVE须有related_tx_id，一对一关联相邻、同库存/原始领料/时点/前后余额的物理出库；
 * 不根据净额相等、备注、当前单位或显示名称猜测历史消费/所有权。
 *
 * 只读原生DECIMAL原字段，不采信影子exact列或迁移标记。单位身份/版本/核验时点须可证明，
 * 所有数量用BigDecimal精确校验，日期使用数据库epoch和既有+08合同，不使用JVM默认时区。
 * 原始来源锁等待结束后重新调用完整库存认证，重核层级、有效期和末端时钟；初末事实变化整池拒绝。
 * 冻结库存（含父层级冻结）及其预留保持原始可审计行，但从可用供给/所有权一并排除。
 * 所有权map不是allocator已实现逐需求分配的证明，也不是MySQL现场并发、部署或人工验收。
 */
@Service
@DS("erp")
public class ErpPurchaseStockLineageVerifier {
    private static final String ERROR = "采购库存流水来源未核验：";
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal MAX = new BigDecimal("99999999999999.9999");
    private static final Set<String> SOURCE_TYPES = Set.of("PROCESS", "PROCUREMENT", "SUBCONTRACT", "PHANTOM", "CFM", "TRANSFER");
    private static final Set<String> SOURCE_TABLES = Set.of(
        "bd_material", "bd_unit_of_measure", "wm_material_stock", "wm_warehouse", "wm_storage_area", "wm_storage_location",
        "wm_material_stock_tx", "wm_material_receive", "wm_material_receive_line", "wm_material_receive_detail",
        "wm_production_issue", "wm_production_issue_line", "prd_material_requirement", "prd_material_requirement_detail", "prd_work_order");
    private static final Set<String> QUANTITY_COLUMNS = Set.of(
        "wm_material_stock.stock_quantity", "wm_material_stock.reserved_quantity",
        "wm_material_stock_tx.tx_quantity", "wm_material_stock_tx.before_quantity", "wm_material_stock_tx.after_quantity",
        "wm_material_receive_line.quantity_received", "wm_material_receive_detail.quantity", "wm_production_issue.total_qty",
        "wm_production_issue_line.quantity", "wm_production_issue_line.required_qty", "wm_production_issue_line.issued_qty",
        "wm_production_issue_line.short_qty", "prd_material_requirement_detail.required_quantity",
        "prd_material_requirement_detail.issued_quantity", "bd_unit_of_measure.conversion_rate");

    private final ErpCertifiedSourceBirthReader birthReader;
    private final ErpPurchaseStockLineageMapper mapper;
    private final ErpPurchaseInventorySourceVerifier inventoryVerifier;
    private final PuRequirementErpTransactionQualification transactionQualification;

    public ErpPurchaseStockLineageVerifier(ErpPurchaseStockLineageMapper mapper,
                                         ErpPurchaseInventorySourceVerifier inventoryVerifier,
                                         PuRequirementErpTransactionQualification transactionQualification) {
        this(mapper,inventoryVerifier,transactionQualification,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ErpPurchaseStockLineageVerifier(ErpPurchaseStockLineageMapper mapper,ErpPurchaseInventorySourceVerifier inventoryVerifier,
            PuRequirementErpTransactionQualification transactionQualification,ErpCertifiedSourceBirthReader birthReader){
        this.birthReader=birthReader;
        this.mapper = Objects.requireNonNull(mapper);
        this.inventoryVerifier = Objects.requireNonNull(inventoryVerifier);
        this.transactionQualification = Objects.requireNonNull(transactionQualification);
    }

    /** 完整物料来源池或拒绝；不返回部分认证余额。 */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public StockLineageSnapshot verifyLocked(String tenantId, Long materialId) {
        require(TenantHelper.isEnable() && text(tenantId) && tenantId.equals(TenantHelper.getTenantId()), "租户未启用或请求租户不符");
        transactionQualification.requireParticipatingWritableErpTransaction();
        require(positive(materialId), "物料身份无效");
        transactionQualification.requireSourceChainTransaction();
        // 独立namespace不能依赖旧17表集合；先固定实际定义，再核本读取器的完整事务表全集。
        String tableEngines = requireTransactionalOriginalTables();
        InventorySnapshot inventory = select(() -> inventoryVerifier.verifyLocked(tenantId, materialId));
        require(inventory != null && tenantId.equals(inventory.tenantId()) && materialId.equals(inventory.materialId())
            && positive(inventory.baseUomId()) && inventory.quantityScale() != null && "+08:00".equals(inventory.databaseTimeZone())
            && inventory.snapshotAt() != null, "当前库存认证事实缺失");
        int scale = inventory.quantityScale();
        require(scale >= 0 && scale <= 4, "基本单位精度无效");
        String unitId = inventory.baseUomId().toString();
        UnitRow unit = select(() -> mapper.lockUnit(tenantId, inventory.baseUomId()));
        validateUnit(unit, inventory);
        Map<Long, StockRow> stocks = indexed(select(() -> mapper.lockStocks(tenantId, materialId)), StockRow::stockId, "库存");
        require(stocks.keySet().equals(inventory.stockIds()),"库存身份覆盖不完整");
        if(stocks.isEmpty()) {
            require(birthReader!=null&&inventory.sourceBirth()!=null,"空库存缺少持久新物料出生，不能作为可信零");
            // 独立重新读取永久出生和全部原始库存/流水，不信任库存层传入的出生声明。
            var born=birthReader.verifyInitialEmptyLocked(tenantId,materialId);
            require(born.equals(inventory.sourceBirth()),"库存层与流水层的持久出生不一致");
            var versions=new TreeMap<>(inventory.sourceVersions());versions.put("stock-lineage-table-engines",tableEngines);
            versions.put("stock-lineage-quantity-schema",quantitySchema(select(mapper::selectQuantityColumns)));
            versions.put("erp_certified_source_birth:"+born.birthId(),born.birthFingerprint());
            var current=select(()->inventoryVerifier.verifyLocked(tenantId,materialId));
            require(current!=null&&current.stockIds().isEmpty()&&born.equals(current.sourceBirth())
                &&inventory.sourceVersions().equals(current.sourceVersions())&&!current.snapshotAt().isBefore(inventory.snapshotAt())
                &&current.quantity().signum()==0&&current.reserved().signum()==0&&current.frozenQuantity().signum()==0,
                "末端出生空范围变更，必须重走原始来源认证");
            return new StockLineageSnapshot(current,Map.of(),Map.of(),List.of(),Set.of(),versions);
        }
        for (StockRow row : stocks.values()) validateStock(row, inventory, unitId, scale);
        Set<Dimension> dimensions = new TreeSet<>(Comparator.comparing(Dimension::toString));
        for (StockRow row : stocks.values()) require(dimensions.add(dimension(row)), "同维度多库存不能证明入库明细分配");
        List<Long> areaIds = ids(stocks.values().stream().map(StockRow::areaId).toList());
        List<Long> locationIds = ids(stocks.values().stream().map(StockRow::locationId).filter(Objects::nonNull).toList());
        Map<Long, AreaRow> areas = indexed(select(() -> mapper.lockAreas(tenantId, areaIds)), AreaRow::areaId, "库区");
        Map<Long, LocationRow> locations = indexed(locationIds.isEmpty() ? List.of()
            : select(() -> mapper.lockLocations(tenantId, locationIds)), LocationRow::locationId, "储位");
        require(areas.keySet().equals(Set.copyOf(areaIds)) && locations.keySet().equals(Set.copyOf(locationIds)), "冻结父链不完整");
        for (StockRow row : stocks.values()) validateParents(row, areas.get(row.areaId()), row.locationId() == null ? null : locations.get(row.locationId()), tenantId);
        List<Long> stockIds = List.copyOf(stocks.keySet());
        Map<Long, TransactionRow> transactions = indexed(select(() -> mapper.lockTransactions(tenantId, materialId, stockIds)), TransactionRow::txId, "库存流水");
        require(!transactions.isEmpty(), "缺少原始流水，历史库存不能作为可信余额或可信零");
        for (TransactionRow row : transactions.values()) validateTransaction(row, stocks, tenantId, materialId, unitId, scale);
        require(birthReader!=null,"非空库存缺少全物料身份覆盖读取依赖");
        select(()->{
            birthReader.requireGlobalCoverageLocked(tenantId,materialId,stocks.keySet(),transactions.keySet());
            return Boolean.TRUE;
        });
        Originals originals = lockOriginals(tenantId, transactions);
        Map<String, String> versions = new TreeMap<>(inventory.sourceVersions());
        versions.put("stock-lineage-table-engines", tableEngines);
        versions.put("stock-lineage-quantity-schema", quantitySchema(select(mapper::selectQuantityColumns)));
        // 时钟读取位于全部原始来源锁之后；锁等待不能沿用开始时的业务日。
        DatabaseClock clock = select(mapper::selectDatabaseClock);
        Instant allLocksAt = clockInstant(clock);
        require(!allLocksAt.isBefore(inventory.snapshotAt()), "数据库时钟倒退");
        // 不能只替换时钟：原始来源锁等待后重新走完整库存、父链、效期认证。
        InventorySnapshot finalInventory = select(() -> inventoryVerifier.verifyLocked(tenantId, materialId));
        require(finalInventory != null && inventory.tenantId().equals(finalInventory.tenantId())
            && inventory.materialId().equals(finalInventory.materialId()) && inventory.baseUomId().equals(finalInventory.baseUomId())
            && inventory.quantityScale().equals(finalInventory.quantityScale()) && inventory.stockIds().equals(finalInventory.stockIds())
            && inventory.sourceVersions().equals(finalInventory.sourceVersions()) && "+08:00".equals(finalInventory.databaseTimeZone())
            && finalInventory.snapshotAt() != null && !finalInventory.snapshotAt().isBefore(allLocksAt), "末端库存来源改变、不完整或时钟倒退");
        eq(inventory.quantity(), finalInventory.quantity(), "末端在库原余额改变");
        eq(inventory.reserved(), finalInventory.reserved(), "末端预留原余额改变");
        eq(inventory.safety(), finalInventory.safety(), "末端安全库存改变");
        eq(inventory.frozenQuantity(), finalInventory.frozenQuantity(), "末端冻结原余额改变");
        Instant snapshotAt = finalInventory.snapshotAt();
        Instant unitVerifiedAt = millisInstant(unit.verifiedAtEpochMillis());
        require(!unitVerifiedAt.isAfter(snapshotAt), "单位核验时间在未来");
        for (StockRow row : stocks.values()) {
            require(!millisInstant(row.recptAtEpochMillis()).isAfter(snapshotAt)
                && millisInstant(row.expireAtEpochMillis()).isAfter(snapshotAt), "库存入库时间未知、未来或锁等待后已过期");
        }
        for (TransactionRow row : transactions.values()) {
            Instant at = millisInstant(row.txAtEpochMillis());
            require(!at.isBefore(unitVerifiedAt) && !at.isAfter(snapshotAt), "原始流水时间未知、未来或先于可证明单位版本");
        }
        authenticateOriginals(originals, transactions, stocks, tenantId, materialId, unitId, scale, snapshotAt, versions);
        Map<Long, BigDecimal> usableOwnership = new TreeMap<>(), frozenOwnership = new TreeMap<>(), totalByIssueLine = new TreeMap<>();
        List<CertifiedStockLineage> lineages = new ArrayList<>();
        BigDecimal usableQuantity = ZERO, usableReserved = ZERO, frozenQuantity = ZERO;
        for (StockRow stock : stocks.values()) {
            List<TransactionRow> history = transactions.values().stream().filter(row -> stock.stockId().equals(row.stockId())).toList();
            Map<Long, BigDecimal> byIssueLine = replay(stock, history, transactions, scale);
            Map<Long, BigDecimal> byRequirement = new TreeMap<>();
            byIssueLine.forEach((id, amount) -> {
                IssueLine line = originals.issueLines().get(id);
                require(line != null, "预留来源领料行缺失");
                add(byRequirement, line.requirementDetailId(), amount); add(totalByIssueLine, id, amount);
            });
            boolean frozen = stock.frozenFlag() == 1L || areas.get(stock.areaId()).frozenFlag() == 1L
                || (stock.locationId() != null && locations.get(stock.locationId()).frozenFlag() == 1L);
            byRequirement.forEach((id, amount) -> add(frozen ? frozenOwnership : usableOwnership, id, amount));
            if (frozen) frozenQuantity = frozenQuantity.add(stock.stockQuantity());
            else { usableQuantity = usableQuantity.add(stock.stockQuantity()); usableReserved = usableReserved.add(stock.reservedQuantity()); }
            lineages.add(new CertifiedStockLineage(stock, frozen, history, byRequirement));
            versions.put("stock-lineage:wm_material_stock:" + stock.stockId(), fingerprint(stock));
        }
        eq(quantity(usableQuantity, scale), inventory.quantity(), "非冻结原始余额与库存认证不一致");
        eq(quantity(usableReserved, scale), inventory.reserved(), "非冻结预留净额与库存认证不一致");
        eq(quantity(frozenQuantity, scale), inventory.frozenQuantity(), "冻结原始余额与库存认证不一致");
        eq(total(usableOwnership), usableReserved, "非零可用预留未完全对应MRP需求");
        validateOwnerCapacity(originals, totalByIssueLine, usableOwnership, frozenOwnership, scale);
        transactions.forEach((id, row) -> versions.put("wm_material_stock_tx:" + id, fingerprint(row)));
        versions.put("stock-lineage:bd_unit_of_measure:" + unit.uomId(), fingerprint(unit));
        InventorySnapshot current = new InventorySnapshot(finalInventory.tenantId(), finalInventory.materialId(), finalInventory.baseUomId(),
            scale, snapshotAt, finalInventory.databaseTimeZone(), quantity(usableQuantity, scale), quantity(usableReserved, scale),
            finalInventory.safety(), quantity(frozenQuantity, scale), finalInventory.stockIds(), versions);
        return new StockLineageSnapshot(current, usableOwnership, frozenOwnership, lineages, transactions.keySet(), versions);
    }

    private String requireTransactionalOriginalTables() {
        List<Long> pins = select(mapper::pinTableDefinitions);
        require(pins != null && pins.isEmpty(), "完整原始表定义不能固定，禁止在未固定定义上认证引擎");
        List<TableEngine> engines = select(mapper::selectTableEngines);
        require(engines != null, "原始表引擎元数据缺失");
        Map<String, TableEngine> observed = new TreeMap<>();
        for (TableEngine row : engines) {
            require(row != null && text(row.tableName()) && SOURCE_TABLES.contains(row.tableName())
                && "InnoDB".equalsIgnoreCase(row.engine()) && observed.putIfAbsent(row.tableName(), row) == null,
                "原始来源表缺失、重复、未知引擎或不是InnoDB，FOR UPDATE不能作为可靠来源锁");
        }
        require(observed.keySet().equals(SOURCE_TABLES), "本读取器完整原始来源InnoDB表全集不齐");
        return fingerprint(observed.toString());
    }

    private static void validateUnit(UnitRow unit, InventorySnapshot inventory) {
        require(unit != null && inventory.tenantId().equals(unit.tenantId()) && inventory.baseUomId().equals(unit.uomId())
            && inventory.quantityScale().equals(unit.quantityScale()) && Long.valueOf(1L).equals(unit.status()) && "0".equals(unit.delFlag()), "基本单位身份、状态或精度不符");
        require(Long.valueOf(1L).equals(unit.isBase()) && (unit.primaryId() == null || unit.uomId().equals(unit.primaryId()))
            && text(unit.category()) && unit.conversionRate() != null && unit.conversionRate().compareTo(BigDecimal.ONE) == 0,
            "仅支持明确根基本单位，禁止猜测历史派生换算");
        require(text(unit.conversionVersion()) && unit.verifiedAtEpochMillis() != null, "基本单位版本或核验时间缺失");
        String version = inventory.sourceVersions().get("bd_unit_of_measure:" + unit.uomId());
        require(version != null && version.startsWith("conversionVersion=" + unit.conversionVersion() + ";"), "单位版本与已锁库存来源不一致");
    }

    private static void validateStock(StockRow row, InventorySnapshot inventory, String unitId, int scale) {
        require(inventory.tenantId().equals(row.tenantId()) && inventory.materialId().equals(row.materialId())
            && unitId.equals(row.baseUomId()) && "0".equals(row.delFlag()) && row.version() != null && row.version() >= 0,
            "库存租户、物料、基本单位、删除状态或版本无效");
        require(positive(row.warehouseId()) && positive(row.areaId()) && nullablePositive(row.locationId())
            && nullablePositive(row.batchId()) && nullablePositive(row.snId()) && binary(row.frozenFlag()), "库存原始维度或冻结标志无效");
        require(quantity(row.reservedQuantity(), scale).compareTo(quantity(row.stockQuantity(), scale)) <= 0, "库存预留超余额");
        millisInstant(row.recptAtEpochMillis()); millisInstant(row.expireAtEpochMillis());
        String version = inventory.sourceVersions().get("wm_material_stock:" + row.stockId());
        require(version != null && version.startsWith("version=" + row.version() + ";"), "库存版本与已锁认证事实不一致");
    }

    private static void validateParents(StockRow stock, AreaRow area, LocationRow location, String tenant) {
        require(area != null && tenant.equals(area.tenantId()) && stock.warehouseId().equals(area.warehouseId())
            && Long.valueOf(1L).equals(area.enableFlag()) && binary(area.frozenFlag()) && binary(area.locationFlag())
            && "0".equals(area.delFlag()), "库区原始冻结父链无效");
        require(area.locationFlag() == 0L || stock.locationId() != null, "储位管理缺少原始储位");
        if (stock.locationId() != null) require(location != null && tenant.equals(location.tenantId())
            && stock.warehouseId().equals(location.warehouseId()) && stock.areaId().equals(location.areaId())
            && Long.valueOf(1L).equals(location.enableFlag()) && binary(location.frozenFlag()) && "0".equals(location.delFlag()), "储位原始冻结父链无效");
    }

    private static void validateTransaction(TransactionRow row, Map<Long, StockRow> stocks, String tenant,
                                             Long material, String unitId, int scale) {
        require(tenant.equals(row.tenantId()) && material.equals(row.materialId()) && positive(row.stockId()) && stocks.containsKey(row.stockId())
            && unitId.equals(row.baseUomId()) && "0".equals(row.delFlag()) && positive(row.sourceDocId())
            && positive(row.sourceDocLineId()), "流水租户、物料、库存、单位、原始来源或删除状态不完整");
        require(quantity(row.txQuantity(), scale).signum() > 0, "原始流水数量必须明确为正");
        quantity(row.beforeQuantity(), scale); quantity(row.afterQuantity(), scale);
        millisInstant(row.txAtEpochMillis());
        boolean receipt = "RECEIVE_IN".equals(row.txType());
        boolean issue = "PRODUCTION_ISSUE".equals(row.txType());
        boolean reservation = member(row.txType(), Set.of("RESERVE", "UNRESERVE", "CONSUME_RESERVE"));
        require(receipt || issue || reservation, "该原始库存流水类型尚未支持，不能补造来源");
        require(receipt ? "WM_MATERIAL_RECEIVE".equals(row.sourceDocType()) && Integer.valueOf(1).equals(row.txFlag())
            : "WM_PRODUCTION_ISSUE".equals(row.sourceDocType()) && Integer.valueOf(issue ? -1 : 0).equals(row.txFlag()),
            "流水方向或原始单据类型不能证明需求归属");
        require("CONSUME_RESERVE".equals(row.txType()) ? positive(row.relatedTxId()) : row.relatedTxId() == null,
            "消耗预留必须明确关联原始出库，其他关联类型尚未认证");
    }

    /** 固定表序，每个IN参数集合均按原始主键去重升序。 */
    private Originals lockOriginals(String tenant, Map<Long, TransactionRow> transactions) {
        List<Long> receiveIds = ids(transactions.values().stream().filter(t -> "RECEIVE_IN".equals(t.txType())).map(TransactionRow::sourceDocId).toList());
        List<Long> issueIds = ids(transactions.values().stream().filter(t -> !"RECEIVE_IN".equals(t.txType())).map(TransactionRow::sourceDocId).toList());
        Map<Long, ReceiveHead> receiveHeads = indexed(receiveIds.isEmpty() ? List.of() : select(() -> mapper.lockReceiveHeads(tenant, receiveIds)), ReceiveHead::receiveId, "入库头");
        Map<Long, ReceiveLine> receiveLines = indexed(receiveIds.isEmpty() ? List.of() : select(() -> mapper.lockReceiveLines(tenant, receiveIds)), ReceiveLine::lineId, "入库行");
        Map<Long, ReceiveDetail> receiveDetails = indexed(receiveIds.isEmpty() ? List.of() : select(() -> mapper.lockReceiveDetails(tenant, receiveIds)), ReceiveDetail::detailId, "入库分配明细");
        Map<Long, IssueHead> issueHeads = indexed(issueIds.isEmpty() ? List.of() : select(() -> mapper.lockIssueHeads(tenant, issueIds)), IssueHead::issueId, "领料头");
        Map<Long, IssueLine> issueLines = indexed(issueIds.isEmpty() ? List.of() : select(() -> mapper.lockIssueLines(tenant, issueIds)), IssueLine::lineId, "领料行");
        require(receiveHeads.keySet().equals(Set.copyOf(receiveIds)) && issueHeads.keySet().equals(Set.copyOf(issueIds)), "原始入库或领料头覆盖不完整");
        List<Long> requirementIds = ids(issueLines.values().stream().map(IssueLine::requirementDetailId).toList());
        Map<Long, RequirementDetail> requirements = indexed(requirementIds.isEmpty() ? List.of()
            : select(() -> mapper.lockRequirementDetails(tenant, requirementIds)), RequirementDetail::detailId, "MRP明细");
        require(requirements.keySet().equals(Set.copyOf(requirementIds)), "预留对应MRP明细覆盖不完整");
        List<Long> headIds = ids(requirements.values().stream().map(RequirementDetail::requirementId).toList());
        Map<Long, RequirementHead> requirementHeads = indexed(headIds.isEmpty() ? List.of()
            : select(() -> mapper.lockRequirementHeads(tenant, headIds)), RequirementHead::requirementId, "MRP头");
        List<Long> workOrderIds = ids(issueHeads.values().stream().map(IssueHead::woId).toList());
        Map<Long, WorkOrder> workOrders = indexed(workOrderIds.isEmpty() ? List.of()
            : select(() -> mapper.lockWorkOrders(tenant, workOrderIds)), WorkOrder::woId, "工单");
        require(requirementHeads.keySet().equals(Set.copyOf(headIds)) && workOrders.keySet().equals(Set.copyOf(workOrderIds)), "MRP头或工单原始归属覆盖不完整");
        return new Originals(receiveHeads, receiveLines, receiveDetails, issueHeads, issueLines, requirements, requirementHeads, workOrders);
    }

    private static void authenticateOriginals(Originals o, Map<Long, TransactionRow> transactions, Map<Long, StockRow> stocks,
                                              String tenant, Long material, String unit, int scale, Instant at, Map<String, String> versions) {
        Map<ReceiptAllocation, BigDecimal> allocated = new TreeMap<>(Comparator.comparing(ReceiptAllocation::toString));
        Map<ReceiptAllocation, BigDecimal> posted = new TreeMap<>(Comparator.comparing(ReceiptAllocation::toString));
        Map<Long, BigDecimal> receivedByLine = new TreeMap<>(), issuedByLine = new TreeMap<>(), requiredByHead = new TreeMap<>(), issuedByRequirement = new TreeMap<>();
        for (ReceiveHead head : o.receiveHeads().values()) {
            require(tenant.equals(head.tenantId()) && "0".equals(head.delFlag()) && text(head.receiveCode()) && "FINISHED".equals(head.status())
                && positive(head.warehouseId()) && positive(head.areaId()) && nullablePositive(head.locationId()), "入库单身份、状态或原始仓储来源无效");
            require(!millisInstant(head.receiveAtEpochMillis()).isAfter(millisInstant(head.shelfAtEpochMillis()))
                && !millisInstant(head.shelfAtEpochMillis()).isAfter(at), "原始入库/上架时间未知或不一致");
        }
        for (ReceiveLine line : o.receiveLines().values()) {
            require(tenant.equals(line.tenantId()) && material.equals(line.materialId()) && unit.equals(line.baseUomId())
                && "0".equals(line.delFlag()) && positive(line.receiveId()) && o.receiveHeads().containsKey(line.receiveId())
                && quantity(line.quantityReceived(), scale).signum() > 0, "入库行身份/单位/数量无效或混物料单据尚未支持");
        }
        for (ReceiveDetail detail : o.receiveDetails().values()) {
            require(positive(detail.receiveId()) && positive(detail.lineId()), "入库明细原始头/行身份未知");
            ReceiveHead head = o.receiveHeads().get(detail.receiveId()); ReceiveLine line = o.receiveLines().get(detail.lineId());
            require(head != null && line != null && line.receiveId().equals(detail.receiveId()) && tenant.equals(detail.tenantId())
                && material.equals(detail.materialId()) && unit.equals(detail.baseUomId()) && "0".equals(detail.delFlag())
                && head.warehouseId().equals(detail.warehouseId()) && head.areaId().equals(detail.areaId())
                && (head.locationId() == null || head.locationId().equals(detail.locationId())), "入库分配明细归属、单位或原始库存维度无效");
            BigDecimal amount = quantity(detail.quantity(), scale); require(amount.signum() > 0, "原始入库分配数量无效");
            add(allocated, new ReceiptAllocation(detail.lineId(), dimension(detail)), amount); add(receivedByLine, detail.lineId(), amount);
        }
        for (IssueHead head : o.issueHeads().values()) {
            require(tenant.equals(head.tenantId()) && "0".equals(head.delFlag()) && text(head.issueCode()) && positive(head.warehouseId())
                && "NORMAL".equals(head.issueType()) && member(head.status(), Set.of("CREATED", "PARTIAL", "COMPLETED", "EXECUTED")), "领料头身份、状态或领料类型未知");
            require(!millisInstant(head.issueAtEpochMillis()).isAfter(at), "领料原始日期未知或在未来");
            quantity(head.totalQty(), scale);
        }
        for (RequirementDetail detail : o.requirements().values()) {
            require(tenant.equals(detail.tenantId()) && material.equals(detail.materialId()) && unit.equals(detail.unit()) && "0".equals(detail.delFlag())
                && Long.valueOf(1L).equals(detail.status()) && Integer.valueOf(0).equals(detail.backflushFlag())
                && member(detail.sourceType(), SOURCE_TYPES) && o.requirementHeads().containsKey(detail.requirementId()), "MRP明细身份、单位、状态或来源未知");
            require(quantity(detail.issuedQuantity(), scale).compareTo(quantity(detail.requiredQuantity(), scale)) <= 0, "MRP已领超需求");
        }
        for (RequirementHead head : o.requirementHeads().values()) {
            require(tenant.equals(head.tenantId()) && "0".equals(head.delFlag()) && text(head.requirementNo()) && positive(head.planId())
                && positive(head.bomId()) && positive(head.materialId()) && member(head.sourceType(), SOURCE_TYPES)
                && member(head.status(), Set.of("CONFIRMED", "ISSUED")), "MRP头原始归属或状态未知");
        }
        for (WorkOrder wo : o.workOrders().values()) {
            require(tenant.equals(wo.tenantId()) && "0".equals(wo.delFlag()) && text(wo.woNo()) && positive(wo.planId()) && positive(wo.bomId())
                && positive(wo.materialId()) && member(wo.woStatus(), Set.of("RELEASED", "IN_PROGRESS", "COMPLETED")), "工单原始归属或状态不支持");
        }
        for (IssueLine line : o.issueLines().values()) {
            require(positive(line.issueId()), "领料行原始头身份未知");
            IssueHead head = o.issueHeads().get(line.issueId()); RequirementDetail detail = o.requirements().get(line.requirementDetailId());
            require(head != null && detail != null && tenant.equals(line.tenantId()) && material.equals(line.materialId()) && unit.equals(line.baseUomId())
                && "0".equals(line.delFlag()) && head.warehouseId().equals(line.warehouseId()) && nullablePositive(line.locationId()) && nullablePositive(line.batchId()), "领料原始行租户/物料/单位/需求归属未知");
            RequirementHead requirement = o.requirementHeads().get(detail.requirementId()); WorkOrder wo = o.workOrders().get(head.woId());
            require(requirement != null && wo != null && requirement.planId().equals(wo.planId()) && requirement.bomId().equals(wo.bomId())
                && requirement.materialId().equals(wo.materialId()), "领料行MRP需求不属于原始工单计划/BOM/母物料");
            BigDecimal required = quantity(line.requiredQty(), scale), issued = quantity(line.issuedQty(), scale);
            eq(required, quantity(line.quantity(), scale), "该领料行原数量与应领数量口径尚未支持");
            require(required.signum() > 0 && issued.compareTo(required) <= 0, "领料行原始数量无效");
            eq(required.subtract(issued), quantity(line.shortQty(), scale), "领料行欠领余额不一致");
            String state = issued.signum() == 0 ? "PENDING" : issued.compareTo(required) == 0 ? "ISSUED" : "PARTIAL";
            require(state.equals(line.lineStatus()), "领料行未知状态或数量状态不一致");
            add(requiredByHead, line.issueId(), required); add(issuedByRequirement, line.requirementDetailId(), issued);
        }
        Map<Long, Instant> latestReceiptByStock = new TreeMap<>();
        for (TransactionRow row : transactions.values()) {
            if ("RECEIVE_IN".equals(row.txType())) {
                ReceiveHead head = o.receiveHeads().get(row.sourceDocId()); ReceiveLine line = o.receiveLines().get(row.sourceDocLineId());
                require(head != null && line != null && head.receiveId().equals(line.receiveId()), "原始入库流水不属于所引用的入库头/行");
                require(row.sourceDocCode() == null || head.receiveCode().equals(row.sourceDocCode()), "入库流水原始单号不符");
                require(!millisInstant(head.shelfAtEpochMillis()).isAfter(millisInstant(row.txAtEpochMillis())), "库存流水早于原始上架");
                add(posted, new ReceiptAllocation(line.lineId(), dimension(stocks.get(row.stockId()))), quantity(row.txQuantity(), scale));
                latestReceiptByStock.merge(row.stockId(), millisInstant(row.txAtEpochMillis()), (a, b) -> a.isAfter(b) ? a : b);
            } else {
                IssueHead head = o.issueHeads().get(row.sourceDocId()); IssueLine line = o.issueLines().get(row.sourceDocLineId());
                require(head != null && line != null && head.issueId().equals(line.issueId()), "原始领料/预留流水不属于所引用的头/行");
                require(row.sourceDocCode() == null || head.issueCode().equals(row.sourceDocCode()), "领料流水原始单号不符");
                StockRow stock = stocks.get(row.stockId());
                require(line.warehouseId().equals(stock.warehouseId()) && (line.locationId() == null || line.locationId().equals(stock.locationId()))
                    && (line.batchId() == null || line.batchId().equals(stock.batchId())), "领料原始行与引用库存分配维度不符");
                require(!millisInstant(head.issueAtEpochMillis()).isAfter(millisInstant(row.txAtEpochMillis())), "流水早于原始领料单日期");
                if ("PRODUCTION_ISSUE".equals(row.txType())) add(issuedByLine, line.lineId(), quantity(row.txQuantity(), scale));
            }
        }
        require(allocated.keySet().equals(posted.keySet()), "入库原始分配明细/流水维度全集不一致");
        allocated.forEach((key, amount) -> eq(amount, posted.get(key), "入库原始分配数量与全量流水不一致"));
        for (ReceiveLine line : o.receiveLines().values()) eq(quantity(line.quantityReceived(), scale), receivedByLine.get(line.lineId()), "原始入库行与完整批次明细数量不一致");
        for (ReceiveHead head : o.receiveHeads().values()) require(o.receiveLines().values().stream().anyMatch(line -> head.receiveId().equals(line.receiveId())), "原始入库头缺完整行");
        for (StockRow stock : stocks.values()) eqInstant(latestReceiptByStock.get(stock.stockId()), millisInstant(stock.recptAtEpochMillis()), "库存原入库时点与最后实际入库流水不一致");
        for (IssueLine line : o.issueLines().values()) eq(quantity(line.issuedQty(), scale), issuedByLine.getOrDefault(line.lineId(), ZERO), "原始领料已领数量未由全量真实出库证明");
        for (RequirementDetail detail : o.requirements().values()) eq(quantity(detail.issuedQuantity(), scale), issuedByRequirement.getOrDefault(detail.detailId(), ZERO), "MRP原已领数量不能完整归属真实领料流水");
        for (IssueHead head : o.issueHeads().values()) {
            eq(quantity(head.totalQty(), scale), requiredByHead.get(head.issueId()), "原领料单头总量与完整单据行不一致");
            List<IssueLine> lines = o.issueLines().values().stream().filter(line -> head.issueId().equals(line.issueId())).toList();
            boolean noneIssued = lines.stream().allMatch(line -> line.issuedQty().signum() == 0);
            boolean allIssued = lines.stream().allMatch(line -> line.issuedQty().compareTo(line.requiredQty()) == 0);
            require(noneIssued ? "CREATED".equals(head.status()) : allIssued ? Set.of("COMPLETED", "EXECUTED").contains(head.status())
                : "PARTIAL".equals(head.status()), "原始领料头状态与完整行履约不一致");
        }
        o.receiveHeads().forEach((id, row) -> versions.put("wm_material_receive:" + id, fingerprint(row)));
        o.receiveLines().forEach((id, row) -> versions.put("wm_material_receive_line:" + id, fingerprint(row)));
        o.receiveDetails().forEach((id, row) -> versions.put("wm_material_receive_detail:" + id, fingerprint(row)));
        o.issueHeads().forEach((id, row) -> versions.put("wm_production_issue:" + id, fingerprint(row)));
        o.issueLines().forEach((id, row) -> versions.put("wm_production_issue_line:" + id, fingerprint(row)));
        o.requirements().forEach((id, row) -> versions.put("prd_material_requirement_detail:" + id, fingerprint(row)));
        o.requirementHeads().forEach((id, row) -> versions.put("prd_material_requirement:" + id, fingerprint(row)));
        o.workOrders().forEach((id, row) -> versions.put("prd_work_order:" + id, fingerprint(row)));
    }

    /** 只按稳定流水ID顺序复算；消耗流水和物理出库是同一次变化，不重复扣在库。 */
    private static Map<Long, BigDecimal> replay(StockRow stock, List<TransactionRow> history,
                                               Map<Long, TransactionRow> transactions, int scale) {
        require(!history.isEmpty() && "RECEIVE_IN".equals(history.get(0).txType())
            && history.get(0).beforeQuantity().signum() == 0, "库存必须从有原始入库来源的明确零起点证明，不能接纳未知历史余额");
        Map<Long, TransactionRow> consumptionByIssue = new TreeMap<>();
        for (int i = 0; i < history.size(); i++) {
            TransactionRow consume = history.get(i);
            if (!"CONSUME_RESERVE".equals(consume.txType())) continue;
            TransactionRow issue = transactions.get(consume.relatedTxId());
            require(issue != null && "PRODUCTION_ISSUE".equals(issue.txType()) && consume.stockId().equals(issue.stockId())
                && consume.sourceDocId().equals(issue.sourceDocId()) && consume.sourceDocLineId().equals(issue.sourceDocLineId())
                && millisInstant(consume.txAtEpochMillis()).equals(millisInstant(issue.txAtEpochMillis()))
                && consume.txQuantity().compareTo(issue.txQuantity()) <= 0, "消耗预留缺少同租户库存/原始领料/时点的真实出库配对");
            eq(consume.beforeQuantity(), issue.beforeQuantity(), "消耗与物理出库原前余额不同");
            eq(consume.afterQuantity(), issue.afterQuantity(), "消耗与物理出库原后余额不同");
            boolean adjacent = i > 0 && history.get(i - 1).txId().equals(issue.txId())
                || i + 1 < history.size() && history.get(i + 1).txId().equals(issue.txId());
            require(adjacent && consumptionByIssue.putIfAbsent(issue.txId(), consume) == null, "消耗预留配对不相邻或重复冒用同一真实出库");
        }
        BigDecimal balance = ZERO;
        Map<Long, BigDecimal> reservations = new TreeMap<>();
        Instant previousAt = null;
        for (TransactionRow row : history) {
            Instant at = millisInstant(row.txAtEpochMillis());
            require(previousAt == null || !at.isBefore(previousAt), "稳定流水ID与原始业务时间顺序不一致");
            previousAt = at;
            if ("CONSUME_RESERVE".equals(row.txType())) continue; // 已由其配对物理出库作原子核销
            BigDecimal amount = quantity(row.txQuantity(), scale), before = quantity(row.beforeQuantity(), scale), after = quantity(row.afterQuantity(), scale);
            eq(balance, before, "库存原始流水链前余额断裂");
            switch (row.txType()) {
                case "RECEIVE_IN" -> { eq(before.add(amount), after, "原始入库前后余额与数量不一致"); balance = after; }
                case "PRODUCTION_ISSUE" -> {
                    TransactionRow consume = consumptionByIssue.get(row.txId());
                    if (consume != null) subtractOwner(reservations, row.sourceDocLineId(), quantity(consume.txQuantity(), scale));
                    eq(before.subtract(amount), after, "原始领料前后余额与数量不一致"); balance = after;
                }
                case "RESERVE" -> { eq(before, after, "预留不能改变原始在库余额"); add(reservations, row.sourceDocLineId(), amount); }
                case "UNRESERVE" -> { eq(before, after, "释放预留不能改变原始在库余额"); subtractOwner(reservations, row.sourceDocLineId(), amount); }
                default -> throw reject("未知原始库存流水不能补零");
            }
            require(balance.signum() >= 0 && quantity(total(reservations), scale).compareTo(balance) <= 0, "原始链在某一时点预留超库存或在库为负");
        }
        eq(balance, quantity(stock.stockQuantity(), scale), "库存原余额与完整原始流水末余额不同");
        eq(quantity(total(reservations), scale), quantity(stock.reservedQuantity(), scale), "库存原预留与各原始领料行预留净额不同");
        reservations.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return reservations;
    }

    private static void subtractOwner(Map<Long, BigDecimal> reservations, Long lineId, BigDecimal amount) {
        BigDecimal current = reservations.get(lineId);
        require(current != null && current.compareTo(amount) >= 0, "释放/消耗超过该原始领料行自己的预留，不能借他人预留冲平");
        reservations.put(lineId, current.subtract(amount));
    }

    private static void validateOwnerCapacity(Originals o, Map<Long, BigDecimal> byLine, Map<Long, BigDecimal> usable,
                                              Map<Long, BigDecimal> frozen, int scale) {
        byLine.forEach((id, amount) -> {
            IssueLine line = o.issueLines().get(id);
            require(line != null && quantity(amount, scale).compareTo(line.requiredQty().subtract(line.issuedQty())) <= 0,
                "原始领料行预留净额超过尚未履约需求");
        });
        Map<Long, BigDecimal> all = new TreeMap<>(usable); frozen.forEach((id, amount) -> add(all, id, amount));
        all.forEach((id, amount) -> {
            RequirementDetail detail = o.requirements().get(id);
            require(detail != null && quantity(amount, scale).compareTo(detail.requiredQuantity().subtract(detail.issuedQuantity())) <= 0,
                "多个库存/领料行的预留合计超MRP剩余需求或没有具体MRP明细归属");
        });
    }

    private static boolean positive(Long id) { return id != null && id > 0; }
    private static boolean nullablePositive(Long id) { return id == null || id > 0; }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static boolean binary(Long value) { return Long.valueOf(0L).equals(value) || Long.valueOf(1L).equals(value); }
    private static boolean member(String value, Set<String> values) { return value != null && values.contains(value); }
    private static void require(boolean condition, String reason) { if (!condition) throw reject(reason); }
    private static ServiceException reject(String reason) { return new ServiceException(ERROR + reason); }

    private static <T> T select(Supplier<T> read) {
        try { return read.get(); }
        catch (RuntimeException ignored) { throw reject("当前读来源不可用，不能返回部分事实或补零"); }
    }

    private static BigDecimal quantity(BigDecimal amount, int scale) {
        require(amount != null && amount.signum() >= 0 && amount.compareTo(MAX) <= 0, "原始数量未知、为负或超原生精度范围");
        try { return amount.setScale(scale, RoundingMode.UNNECESSARY).setScale(4, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ignored) { throw reject("原始数量超基本单位或DECIMAL18,4精度，禁止舍入升级"); }
    }

    private static void eq(BigDecimal first, BigDecimal second, String reason) {
        require(first != null && second != null && first.compareTo(second) == 0, reason);
    }
    private static void eqInstant(Instant first, Instant second, String reason) { require(first != null && first.equals(second), reason); }
    private static <K> void add(Map<K, BigDecimal> map, K key, BigDecimal amount) { map.merge(key, amount, BigDecimal::add); }
    private static BigDecimal total(Map<?, BigDecimal> amounts) { return amounts.values().stream().reduce(ZERO, BigDecimal::add); }

    private static List<Long> ids(List<Long> values) {
        require(values != null && values.stream().allMatch(ErpPurchaseStockLineageVerifier::positive), "原始父身份缺失或无效");
        return List.copyOf(new TreeSet<>(values));
    }

    private static <T> Map<Long, T> indexed(List<T> rows, Function<T, Long> identity, String name) {
        require(rows != null, name + "查询未返回完整集合");
        Map<Long, T> result = new TreeMap<>();
        for (T row : rows) {
            require(row != null && positive(identity.apply(row)), name + "原始身份未知");
            require(result.putIfAbsent(identity.apply(row), row) == null, name + "原始身份重复");
        }
        return result;
    }

    private static Instant clockInstant(DatabaseClock clock) {
        require(clock != null && "+08:00".equals(clock.databaseTimeZone()), "数据库时区未明确为现有+08合同");
        return instant(clock.epochSeconds(), new BigDecimal("1000000000"));
    }
    private static Instant millisInstant(BigDecimal millis) { return instant(millis, new BigDecimal("1000000")); }
    private static Instant instant(BigDecimal epoch, BigDecimal nanosPerUnit) {
        require(epoch != null && epoch.signum() > 0, "原始数据库业务时间未知或零日期");
        try {
            long nanos = epoch.multiply(nanosPerUnit).longValueExact();
            return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
        } catch (ArithmeticException | DateTimeException ignored) { throw reject("数据库时间精度或范围不支持，不能使用JVM默认时间"); }
    }

    private static String quantitySchema(List<QuantityColumn> columns) {
        require(columns != null && columns.size() == QUANTITY_COLUMNS.size(), "原始数量列元数据缺失或不完整");
        Map<String, QuantityColumn> exact = new TreeMap<>();
        for (QuantityColumn column : columns) {
            require(column != null, "数量列元数据未知");
            String key = column.tableName() + "." + column.columnName();
            int scale = "bd_unit_of_measure.conversion_rate".equals(key) ? 6 : 4;
            require(QUANTITY_COLUMNS.contains(key) && "decimal".equalsIgnoreCase(column.dataType())
                && Integer.valueOf(18).equals(column.numericPrecision()) && Integer.valueOf(scale).equals(column.numericScale())
                && exact.putIfAbsent(key, column) == null, "原始数量必须原生DECIMAL(18,4)，换算率须18,6，不能由影子列或CAST认证旧DOUBLE");
        }
        require(exact.keySet().equals(QUANTITY_COLUMNS), "原始数量列覆盖不完整");
        return fingerprint(exact.toString());
    }

    /** 长度前缀保留原字段边界，避免单号/版本文本中分隔符造成等值摘要。 */
    private static String fingerprint(Object row) {
        StringBuilder input = new StringBuilder();
        if (row.getClass().isRecord()) {
            input.append(row.getClass().getName()).append(';');
            try {
                for (var field : row.getClass().getRecordComponents()) {
                    Object value = field.getAccessor().invoke(row);
                    String text = value == null ? null : value.toString();
                    input.append(field.getName()).append(':').append(text == null ? -1 : text.length()).append(':');
                    if (text != null) input.append(text);
                    input.append(';');
                }
            } catch (ReflectiveOperationException ignored) { throw reject("原始类型摘要不能复核"); }
        } else input.append(row);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private static Dimension dimension(StockRow stock) {
        return new Dimension(stock.warehouseId(), stock.areaId(), stock.locationId(), stock.batchId(), stock.snId());
    }
    private static Dimension dimension(ReceiveDetail detail) {
        return new Dimension(detail.warehouseId(), detail.areaId(), detail.locationId(), detail.batchId(), detail.snId());
    }
    private record Dimension(Long warehouseId, Long areaId, Long locationId, Long batchId, Long snId) {}
    private record ReceiptAllocation(Long lineId, Dimension dimension) {}
    private record Originals(Map<Long, ReceiveHead> receiveHeads, Map<Long, ReceiveLine> receiveLines,
                             Map<Long, ReceiveDetail> receiveDetails, Map<Long, IssueHead> issueHeads,
                             Map<Long, IssueLine> issueLines, Map<Long, RequirementDetail> requirements,
                             Map<Long, RequirementHead> requirementHeads, Map<Long, WorkOrder> workOrders) {}

    /** 保留全量冻结/非冻结原始库存及其流水，按真实MRP明细给出净预留。 */
    public record CertifiedStockLineage(StockRow stock, boolean frozen,
                                        List<TransactionRow> transactions,
                                        Map<Long, BigDecimal> reservedByRequirementDetail) {
        public CertifiedStockLineage {
            transactions = List.copyOf(transactions);
            reservedByRequirementDetail = Map.copyOf(reservedByRequirementDetail);
        }
    }

    /** 两个预留map以真实MRP detail_id为键，均为基本单位；冻结量不可冲抵可采购需求。 */
    public record StockLineageSnapshot(InventorySnapshot inventory,
                                       Map<Long, BigDecimal> reservedByRequirementDetail,
                                       Map<Long, BigDecimal> frozenReservedByRequirementDetail,
                                       List<CertifiedStockLineage> stocks, Set<Long> transactionIds,
                                       Map<String, String> sourceVersions) {
        public StockLineageSnapshot {
            reservedByRequirementDetail = Map.copyOf(reservedByRequirementDetail);
            frozenReservedByRequirementDetail = Map.copyOf(frozenReservedByRequirementDetail);
            stocks = List.copyOf(stocks);
            transactionIds = Set.copyOf(transactionIds);
            sourceVersions = Map.copyOf(sourceVersions);
        }
    }
}

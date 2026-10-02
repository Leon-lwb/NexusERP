package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Material;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.SupplierTerms;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.TrustedUnit;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.DatabaseClock;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 从当前租户 ERP 主数据冻结一个物料与明确供应商的采购目录来源事实。
 * read 经独立只读、可重复读新事务代理调用；readLocked 仅加入已核验的可写 ERP 事务。
 * 两入口不接收外部事实、核验布尔或时间；锁定结果只对调用方当前事务有效。
 * NULL、失效、身份不符或未知换算链均拒绝，不继承旧 MOQ、名称或历史关系日期。
 * 本新增 P1 片段只支持 ERP 业务时区 Asia/Shanghai 的墙上 DATETIME 存储合同：
 * 后续人工确认新条款/换算日期及版本时，读写均须遵守此口径；读取会话必须明确
 * 为 +08:00，UTC、SYSTEM、其它偏移或未知存储口径不得升级为可信目录来源。
 * 该合同不自行证明历史数据的存储语义，不补填 NULL 或确认业务资料，不修改用户、
 * 会话或数据库全局配置；不满足合同的来源继续拒绝。
 * 结果只证明本次目录读取及其单位/条款，不证明需求明细、库存完整性、全局额度、
 * 采购授权或动作可执行；执行前仍须在同 ERP 统一写事务内重新读取和完整核验。
 */
@Service
@DS("erp")
public class ErpPurchaseCatalogSourceReader {
    private final ErpPurchaseCatalogSourceMapper mapper;
    private final PuRequirementErpTransactionQualification transactionQualification;

    public ErpPurchaseCatalogSourceReader(ErpPurchaseCatalogSourceMapper mapper) {
        this(mapper, null);
    }

    /** 仅保存固定来源 Mapper 和事务资格校验；不借连接、补绑或授予业务资格。 */
    @Autowired
    public ErpPurchaseCatalogSourceReader(ErpPurchaseCatalogSourceMapper mapper,
        PuRequirementErpTransactionQualification transactionQualification) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transactionQualification = transactionQualification;
    }

    /**
     * 仅读取明确身份，不允许调用方通过事实、verified 旗号或历史快照时间自证。
     * 事务与数据源切面必须作用于实际服务代理，当前租户校验先于任何 Mapper 查询。
     *
     * @param requestTenantId 请求租户，必须与启用的当前租户完全一致
     * @param materialId 实际物料主键，保留完整 64 位身份
     * @param vendorId 实际供应商主键；多个未删除关系不能自动择优
     * @return 仅包含冻结目录事实与实际来源版本的不可变快照
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public CatalogSnapshot read(String requestTenantId, Long materialId, Long vendorId) {
        checkTenant(requestTenantId);
        require(id(materialId) && id(vendorId), "物料或供应商身份无效");
        checkReadTransaction();
        return readSources(requestTenantId, materialId, vendorId, false);
    }

    /** 两种入口共用全部来源认证；模式只选择固定 Mapper，不是调用方认证旗号。 */
    private CatalogSnapshot readSources(String requestTenantId, Long materialId, Long vendorId, boolean locked) {
        DatabaseClock clock = selectMapper(locked ? mapper::lockDatabaseClock : mapper::selectDatabaseClock);
        Instant snapshotAt = databaseInstant(clock);
        MaterialSource material = freeze(selectMapper(() -> locked ? mapper.lockMaterial(requestTenantId, materialId) : mapper.selectMaterial(requestTenantId, materialId)));
        require(requestTenantId.equals(material.tenantId()) && materialId.equals(material.id())
            && active(material.status(), material.delFlag()) && id(material.baseUomId()), "物料主数据无效");
        VendorSource vendor = freeze(selectMapper(() -> locked ? mapper.lockVendor(requestTenantId, vendorId) : mapper.selectVendor(requestTenantId, vendorId)));
        require(requestTenantId.equals(vendor.tenantId()) && vendorId.equals(vendor.id())
            && "ACTIVE".equals(vendor.status()) && "0".equals(vendor.delFlag()), "供应商主数据无效");

        List<BdMaterialVendor> relations = selectMapper(() -> locked ? mapper.lockRelations(requestTenantId, materialId, vendorId) : mapper.selectRelations(requestTenantId, materialId, vendorId));
        require(relations != null && relations.size() == 1, "供应关系缺失或重复");
        TermsSource terms = freeze(relations.get(0));
        checkTerms(terms, requestTenantId, materialId, vendorId, snapshotAt);

        List<Long> seedIds = List.copyOf(new LinkedHashSet<>(List.of(
            material.baseUomId(), terms.orderUomId(), terms.moqUomId())));
        Map<Long, UnitSource> units = readUnits(requestTenantId, seedIds, snapshotAt, locked);
        Set<Long> roots = new LinkedHashSet<>();
        for (Long unitId : seedIds) roots.add(units.get(unitId).primaryId());
        require(roots.size() == 1, "单位没有明确共同主单位");
        Long rootId = roots.iterator().next();
        if (!units.containsKey(rootId)) {
            units.putAll(readUnits(requestTenantId, List.of(rootId), snapshotAt, locked));
        }
        UnitSource root = units.get(rootId);
        require(Long.valueOf(1).equals(root.isBase()) && rootId.equals(root.primaryId())
            && BigDecimal.ONE.compareTo(root.rate()) == 0, "共同主单位未核验或属于未知多级链");
        for (Long unitId : seedIds) {
            UnitSource unit = units.get(unitId);
            require(rootId.equals(unit.primaryId()) && root.dimension().equals(unit.dimension())
                && root.version().equals(unit.version()), "单位维度或换算版本不一致");
        }

        UnitSource base = units.get(material.baseUomId());
        Map<Long, TrustedUnit> trusted = new LinkedHashMap<>();
        for (Long unitId : seedIds) {
            UnitSource unit = units.get(unitId);
            BigDecimal relativeRate;
            try {
                relativeRate = unit.rate().divide(base.rate());
            } catch (ArithmeticException nonExact) {
                throw rejected("相对物料基本单位的比例不能精确相除");
            }
            require(decimal(relativeRate, 6), "相对换算率超出可信范围或精度");
            trusted.put(unitId, new TrustedUnit(requestTenantId, Long.toString(unitId),
                Long.toString(material.baseUomId()), root.dimension(), relativeRate, unit.version(),
                unit.scale(), unit.verifiedAt(), true));
        }
        TrustedUnit baseUnit = trusted.get(material.baseUomId());
        TrustedUnit orderUnit = trusted.get(terms.orderUomId());
        TrustedUnit moqUnit = trusted.get(terms.moqUomId());
        checkTermQuantities(terms, baseUnit, orderUnit, moqUnit);

        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("terms:" + terms.id(), terms.version());
        versions.put("material:"+material.id(),contentVersion("material",material.tenantId(),material.id().toString(),
            material.baseUomId().toString(),material.status().toString(),material.delFlag()));
        versions.put("vendor:"+vendor.id(),contentVersion("vendor",vendor.tenantId(),vendor.id().toString(),vendor.status(),vendor.delFlag()));
        units.forEach((unitId, unit) -> versions.put("unit:" + unitId, unit.version()));
        if (locked) {
            // 行锁可能等待；最终时钟与时区仍由同连接数据库重读，不能沿用等待前已过期的条款。
            clock = selectMapper(mapper::lockDatabaseClock);
            snapshotAt = databaseInstant(clock);
            checkTerms(terms, requestTenantId, materialId, vendorId, snapshotAt);
            for (UnitSource unit : units.values()) {
                require(!unit.verifiedAt().isAfter(snapshotAt), "单位业务核验字段、精度或换算率无效");
            }
        }
        checkTenant(requestTenantId);
        return new CatalogSnapshot(requestTenantId, Long.toString(vendor.id()), Long.toString(terms.id()),
            snapshotAt, clock.databaseTimeZone(),
            new Material(requestTenantId, Long.toString(material.id()), Long.toString(material.baseUomId()), true),
            new SupplierTerms(requestTenantId, Long.toString(material.id()), Long.toString(terms.id()),
                Long.toString(terms.orderUomId()), Long.toString(terms.moqUomId()), terms.moqQty(), terms.multiple(),
                terms.allowSplit(), terms.packSize(), terms.version(), terms.effectiveAt(), terms.expiresAt(),
                terms.verifiedAt(), true), baseUnit, orderUnit, moqUnit, versions);
    }

    /**
     * 在调用方已核验的同连接可写 ERP 事务内当前读；不自建事务、补绑或接受外部资格。
     * 上层须先锁定单物料 guard 和竞争池；本结果不得带出事务作为持久采购授权。
     */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public CatalogSnapshot readLocked(String requestTenantId, Long materialId, Long vendorId) {
        checkTenant(requestTenantId);
        require(id(materialId) && id(vendorId), "物料或供应商身份无效");
        require(transactionQualification != null, "锁定读取缺少真实 ERP 事务资格校验依赖");
        transactionQualification.requireParticipatingWritableErpTransaction();
        return readSources(requestTenantId, materialId, vendorId, true);
    }

    /** 所有输出组成均不可变；版本映射再次复制，不能保留内部集合或实体别名。 */
    public record CatalogSnapshot(String tenantId, String vendorId, String relationId, Instant snapshotAt,
                                  String databaseTimeZone, Material material, SupplierTerms terms,
                                  TrustedUnit baseUnit, TrustedUnit orderUnit, TrustedUnit moqUnit,
                                  Map<String, String> sourceVersions) {
        public CatalogSnapshot {
            sourceVersions = Map.copyOf(sourceVersions);
        }
    }

    /**
     * 仅包围 Mapper 调用，所有 Runtime（含 ServiceException）均视为不可信查询故障，
     * 生成新的固定诊断，不复制原 message、detail、cause 或 suppressed。
     * 查询外的 reader 受控业务校验不进入此边界；任何 Error（含 VM/ThreadDeath）
     * 均原样向调用方传播，不按异常类名推断来源可信度。
     */
    private static <T> T selectMapper(Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException queryFailure) {
            throw new ServiceException("采购目录来源读取失败");
        }
    }

    private static void checkTenant(String tenantId) {
        require(TenantHelper.isEnable() && numeric(tenantId) && tenantId.equals(TenantHelper.getTenantId()),
            "当前租户未启用、缺失或与请求不一致");
    }

    /** 上下文仅用于拒绝绕过只读代理的调用，不赋予连接、来源或采购执行资格。 */
    private static void checkReadTransaction() {
        require("erp".equals(DynamicDataSourceContextHolder.peek())
            && TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            && Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()),
            "必须通过 ERP 只读可重复读新事务服务代理调用");
    }

    /**
     * P1 DATETIME 本身不含时区，仅支持按 ERP Asia/Shanghai 业务墙上时间确认的
     * 新日期/版本；必须先核对原始 session 为精确 +08:00，再读取任何目录字段。
     * 可解析的 UTC 或其它 offset 不能证明存储语义一致，不能据此移动有效期。
     * 这里不设置时区、不回填 NULL；快照仍由数据库绝对时间戳精确生成。
     */
    private static Instant databaseInstant(DatabaseClock clock) {
        require(clock != null && clock.epochSeconds() != null && !blank(clock.databaseTimeZone()),
            "数据库业务时钟或时区缺失");
        require("+08:00".equals(clock.databaseTimeZone()),
            "数据库会话时区不符合 ERP Asia/Shanghai 日期存储合同，必须明确为 +08:00");
        try {
            BigDecimal epoch = clock.epochSeconds();
            require(epoch.signum() > 0 && epoch.stripTrailingZeros().scale() <= 6, "数据库时间戳无效或精度未知");
            BigDecimal seconds = epoch.divideToIntegralValue(BigDecimal.ONE);
            long nanos = epoch.subtract(seconds).movePointRight(9).longValueExact();
            return Instant.ofEpochSecond(seconds.longValueExact(), nanos);
        } catch (DateTimeException | ArithmeticException invalid) {
            throw rejected("数据库业务时钟不能精确解释");
        }
    }

    private static void checkTerms(TermsSource terms, String tenantId, Long materialId, Long vendorId,
                                   Instant snapshotAt) {
        require(tenantId.equals(terms.tenantId()) && materialId.equals(terms.materialId())
            && vendorId.equals(terms.vendorId()) && id(terms.id()) && active(terms.status(), terms.delFlag()),
            "供应关系身份或状态无效");
        require(id(terms.orderUomId()) && id(terms.moqUomId()) && !blank(terms.version())
            && terms.moqQty() != null && terms.multiple() != null && terms.allowSplit() != null
            && terms.packSize() != null && terms.effectiveAt() != null && terms.expiresAt() != null
            && terms.verifiedAt() != null, "新供应条款核验字段不完整");
        require(!terms.verifiedAt().isAfter(snapshotAt) && !terms.effectiveAt().isAfter(terms.expiresAt())
            && !terms.effectiveAt().isAfter(snapshotAt) && !terms.expiresAt().isBefore(snapshotAt),
            "新供应条款未生效、已过期或核验时间在未来");
    }

    /** 每次仅读明确 ID：至多三个业务单位和一次共同根补读，不递归猜测多级链。 */
    private Map<Long, UnitSource> readUnits(String tenantId, List<Long> ids, Instant snapshotAt, boolean locked) {
        Set<Long> expected = new LinkedHashSet<>(ids);
        List<Long> selectedIds = locked ? ids.stream().sorted().toList() : List.copyOf(ids);
        List<BdUnitOfMeasure> rows = selectMapper(() -> locked ? mapper.lockUnits(tenantId, selectedIds) : mapper.selectUnits(tenantId, selectedIds));
        require(rows != null && rows.size() == expected.size(), "单位来源缺失或重复");
        Map<Long, UnitSource> units = new LinkedHashMap<>();
        for (BdUnitOfMeasure row : rows) {
            UnitSource unit = freeze(row);
            require(tenantId.equals(unit.tenantId()) && id(unit.id()) && expected.contains(unit.id())
                && active(unit.status(), unit.delFlag()), "单位身份、租户或状态无效");
            require(id(unit.primaryId()) && !blank(unit.dimension()) && !blank(unit.version())
                && unit.scale() != null && unit.scale() >= 0 && unit.scale() <= 4
                && unit.verifiedAt() != null && !unit.verifiedAt().isAfter(snapshotAt)
                && (Long.valueOf(0).equals(unit.isBase()) || Long.valueOf(1).equals(unit.isBase()))
                && decimal(unit.rate(), 6), "单位业务核验字段、精度或换算率无效");
            if (Long.valueOf(1).equals(unit.isBase())) {
                require(unit.id().equals(unit.primaryId()) && unit.rate().compareTo(BigDecimal.ONE) == 0,
                    "主单位必须有明确自映射及核验后的恒等换算");
            } else {
                require(!unit.id().equals(unit.primaryId()), "非主单位存在循环自映射");
            }
            require(units.putIfAbsent(unit.id(), unit) == null, "单位来源重复");
        }
        require(units.keySet().equals(expected), "单位来源身份不完整");
        return units;
    }

    private static void checkTermQuantities(TermsSource terms, TrustedUnit base, TrustedUnit order, TrustedUnit moq) {
        require(decimal(terms.moqQty(), 4) && atScale(terms.moqQty(), moq.quantityScale())
            && decimal(terms.multiple(), 4) && atScale(terms.multiple(), order.quantityScale())
            && decimal(terms.packSize(), 4) && atScale(terms.packSize(), base.quantityScale()),
            "供应条款数量非正、超限或不符合核验单位精度");
        BigDecimal moqBase = terms.moqQty().multiply(moq.basePerUnit());
        require(decimal(moqBase, 4) && atScale(moqBase, base.quantityScale()), "MOQ 基本单位换算超限或不精确");
    }

    /** 数据库存储范围和精度分别检查，不使用 double 或舍入，也不自动提升 MOQ。 */
    private static boolean decimal(BigDecimal value, int scale) {
        if (value == null || value.signum() <= 0) return false;
        BigDecimal normalized = value.stripTrailingZeros();
        return (long) normalized.precision() - normalized.scale() <= 18L - scale && normalized.scale() <= scale;
    }

    private static boolean atScale(BigDecimal value, int scale) {
        return value.stripTrailingZeros().scale() <= scale;
    }

    private static boolean active(Long status, String delFlag) {
        return Long.valueOf(1).equals(status) && "0".equals(delFlag);
    }

    private static boolean id(Long id) {
        return id != null && id > 0;
    }

    private static boolean numeric(String value) {
        if (blank(value)) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw rejected(reason);
    }

    private static ServiceException rejected(String reason) {
        return new ServiceException("采购目录来源未核验：" + reason);
    }

    /** 日期在进入私有事实结构时即冻结，不保留 Mapper 的可变 Date 引用。 */
    private static Instant freeze(Date value) {
        return value == null ? null : Instant.ofEpochMilli(value.getTime());
    }

    private static MaterialSource freeze(BdMaterial row) {
        require(row != null, "物料主数据缺失");
        return new MaterialSource(row.getTenantId(), row.getMaterialId(), row.getBaseUomId(), row.getStatus(), row.getDelFlag());
    }

    private static VendorSource freeze(BdVendor row) {
        require(row != null, "供应商主数据缺失");
        return new VendorSource(row.getTenantId(), row.getVendorId(), row.getStatus(), row.getDelFlag());
    }

    private static TermsSource freeze(BdMaterialVendor row) {
        require(row != null, "供应关系缺失");
        return new TermsSource(row.getTenantId(), row.getRelationId(), row.getMaterialId(), row.getVendorId(),
            row.getOrderUomId(), row.getMoqUomId(), row.getVerifiedMoqQty(), row.getOrderMultiple(), row.getAllowSplit(),
            row.getUnitsPerPack(), row.getTermsVersion(), freeze(row.getTermsEffectiveAt()), freeze(row.getTermsExpiresAt()),
            freeze(row.getTermsVerifiedAt()), row.getStatus(), row.getDelFlag());
    }

    private static UnitSource freeze(BdUnitOfMeasure row) {
        require(row != null, "单位来源缺失");
        return new UnitSource(row.getTenantId(), row.getUomId(), row.getPrimaryId(), row.getIsBase(), row.getCategory(),
            row.getConversionRate(), row.getQuantityScale(), row.getConversionVersion(), freeze(row.getConversionVerifiedAt()),
            row.getStatus(), row.getDelFlag());
    }

    /** 采购来源内容摘要，不是完整ERP主数据修订号；仅冻结实际认证过的身份/单位/有效状态。 */
    private static String contentVersion(String... fields) {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(String field:fields) { byte[] value=field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());digest.update(value); }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException unavailable) { throw rejected("采购来源内容版本不可生成"); }
    }

    private record MaterialSource(String tenantId, Long id, Long baseUomId, Long status, String delFlag) { }

    private record VendorSource(String tenantId, Long id, String status, String delFlag) { }

    private record TermsSource(String tenantId, Long id, Long materialId, Long vendorId, Long orderUomId,
                               Long moqUomId, BigDecimal moqQty, BigDecimal multiple, Boolean allowSplit,
                               BigDecimal packSize, String version, Instant effectiveAt, Instant expiresAt,
                               Instant verifiedAt, Long status, String delFlag) { }

    private record UnitSource(String tenantId, Long id, Long primaryId, Long isBase, String dimension,
                              BigDecimal rate, Integer scale, String version, Instant verifiedAt,
                              Long status, String delFlag) { }
}

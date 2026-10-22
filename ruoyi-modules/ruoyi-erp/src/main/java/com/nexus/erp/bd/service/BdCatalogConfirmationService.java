package com.nexus.erp.bd.service;

import static com.nexus.erp.bd.service.BdCatalogConfirmationSupport.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorTermsConfirmBo;
import com.nexus.erp.bd.domain.bo.BdUnitQuantityConfirmBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVendorVo;
import com.nexus.erp.bd.domain.vo.BdUnitOfMeasureVo;
import com.nexus.erp.bd.mapper.BdCatalogConfirmationMapper;
import com.nexus.erp.bd.service.BdCatalogConfirmationSupport.ActorContext;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.DatabaseClock;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 两类实际资料的本地确认入口：只在独立 ERP 可写 RR 事务内核验、锁定与 CAS。
 * 不加入外部事务，不补认旧库存；证据引用与登录操作者由控制器操作日志留痕。
 * 单位换算族共享版本；成员事实确认时换代已核验成员版本但不改其原核验时间，
 * 历史空认证成员不参与换代，以保证同根版本合同及陈旧版本拒绝同时成立。
 */
@Service
public class BdCatalogConfirmationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final BdCatalogConfirmationMapper mapper;
    private final PuRequirementErpTransactionExecutor executor;

    public BdCatalogConfirmationService(BdCatalogConfirmationMapper mapper, PuRequirementErpTransactionExecutor executor) {
        this.mapper = Objects.requireNonNull(mapper);
        this.executor = Objects.requireNonNull(executor);
    }

    public BdUnitOfMeasureVo confirmQuantity(Long id, BdUnitQuantityConfirmBo request) {
        ActorContext actor = currentActor();
        require(id(id) && request != null, "单位确认身份或资料缺失");
        checkReference(request.expectedVersion(), request.reference());
        require(id(request.primaryId()) && decimal(request.conversionRate(), 6)
            && request.quantityScale() != null && request.quantityScale() >= 0 && request.quantityScale() <= 4,
            "单位换算比例或数量精度无效");
        return executor.executeRepeatableRead(actor.tenantId(), () -> {
            checkActor(actor);
            BdUnitOfMeasure initial = mapper.lockUnit(actor.tenantId(), id);
            checkUnitIdentity(initial, actor.tenantId(), id);
            boolean rootTarget = Long.valueOf(1).equals(initial.getIsBase());
            require(rootTarget || Long.valueOf(0).equals(initial.getIsBase()), "单位基本标识无效");
            require(!blank(initial.getCategory()), "单位维度缺失");
            Long rootId = rootTarget ? id : initial.getPrimaryId();
            require(id(rootId) && rootId.equals(request.primaryId()), "确认根身份必须与实际单位映射一致");
            require(!rootTarget || request.conversionRate().compareTo(BigDecimal.ONE) == 0,
                "根单位必须显式自映射且换算比例为一");
            Map<Long, BdUnitOfMeasure> family = identities(mapper.lockUnitFamily(actor.tenantId(), rootId), actor.tenantId());
            BdUnitOfMeasure row = family.get(id), root = family.get(rootId);
            checkUnitIdentity(row, actor.tenantId(), id);
            require(Objects.equals(initial.getConversionVersion(), row.getConversionVersion())
                && Objects.equals(initial.getPrimaryId(), row.getPrimaryId())
                && Objects.equals(initial.getIsBase(), row.getIsBase()), "锁内单位身份发生变化");
            require(Objects.equals(request.expectedVersion(), row.getConversionVersion()), "单位原版本已改变，请重新读取");
            Instant now = databaseNow();
            if (!rootTarget) {
                checkCertifiedRoot(root, actor.tenantId(), rootId, now);
                require(root.getCategory().equals(row.getCategory()), "单位与根单位维度不一致");
            }
            String previousFamilyVersion = root == null ? null : root.getConversionVersion();
            String version = "Q-" + UUID.randomUUID();
            if (!blank(previousFamilyVersion)) {
                long certifiedSiblings = family.values().stream().filter(member -> !id.equals(member.getUomId()))
                    .filter(member -> active(member.getStatus(), member.getDelFlag()))
                    .filter(member -> previousFamilyVersion.equals(member.getConversionVersion())
                        && member.getConversionVerifiedAt() != null).count();
                if (certifiedSiblings > 0) {
                    require(mapper.rotateCertifiedUnitVersions(actor.tenantId(), rootId, id, previousFamilyVersion,
                        version, actor.actorId()) == certifiedSiblings, "单位族原版本已改变");
                }
            }
            row.setPrimaryId(rootId);
            row.setPrimaryName(rootTarget ? row.getUomName() : root.getUomName());
            row.setConversionRate(request.conversionRate()); row.setQuantityScale(request.quantityScale());
            row.setConversionVersion(version); row.setConversionVerifiedAt(Date.from(now));
            row.setUpdateBy(actor.actorId()); row.setUpdateTime(Date.from(now));
            checkActor(actor);
            require(mapper.confirmUnit(row, request.expectedVersion(), sqlTime(now)) == 1, "单位原版本 CAS 失败");
            return MapstructUtils.convert(row, BdUnitOfMeasureVo.class);
        });
    }

    public BdMaterialVendorVo confirmTerms(Long id, BdMaterialVendorTermsConfirmBo request) {
        ActorContext actor = currentActor();
        require(id(id) && request != null, "条款确认身份或资料缺失");
        checkReference(request.expectedVersion(), request.reference());
        require(id(request.materialId()) && id(request.vendorId()) && id(request.orderUomId()) && id(request.moqUomId())
            && decimal(request.verifiedMoqQty(), 4) && decimal(request.orderMultiple(), 4)
            && decimal(request.unitsPerPack(), 4) && request.allowSplit() != null,
            "必须明确提交条款单位、起订量、订购倍数、拆零与包装资料");
        require(validDate(request.termsEffectiveAt()) && validDate(request.termsExpiresAt()), "条款日期缺失或精度无效");
        return executor.executeRepeatableRead(actor.tenantId(), () -> {
            checkActor(actor);
            BdMaterialVendor row = mapper.lockRelation(actor.tenantId(), id);
            require(row != null && actor.tenantId().equals(row.getTenantId()) && id.equals(row.getRelationId())
                && request.materialId().equals(row.getMaterialId()) && request.vendorId().equals(row.getVendorId())
                && active(row.getStatus(), row.getDelFlag()), "实际供应关系身份、租户或状态不符");
            require(Objects.equals(request.expectedVersion(), row.getTermsVersion()), "条款原版本已改变，请重新读取");
            BdMaterial material = mapper.lockMaterial(actor.tenantId(), row.getMaterialId());
            require(material != null && actor.tenantId().equals(material.getTenantId())
                && row.getMaterialId().equals(material.getMaterialId()) && active(material.getStatus(), material.getDelFlag())
                && id(material.getBaseUomId()), "关联物料身份或基本单位无效");
            BdVendor vendor = mapper.lockVendor(actor.tenantId(), row.getVendorId());
            require(vendor != null && actor.tenantId().equals(vendor.getTenantId())
                && row.getVendorId().equals(vendor.getVendorId()) && "ACTIVE".equals(vendor.getStatus())
                && "0".equals(vendor.getDelFlag()), "关联供应商必须处于当前租户 ACTIVE 状态");
            List<Long> relations = mapper.lockRelationIds(actor.tenantId(), row.getMaterialId(), row.getVendorId());
            require(relations != null && relations.equals(List.of(id)), "供应关系缺失或重复");
            Set<Long> seeds = new LinkedHashSet<>(List.of(material.getBaseUomId(), request.orderUomId(), request.moqUomId()));
            Map<Long, BdUnitOfMeasure> units = identities(mapper.lockUnits(actor.tenantId(), seeds.stream().sorted().toList()), actor.tenantId());
            require(units.keySet().equals(seeds), "关联单位身份不完整");
            Long rootId = units.get(material.getBaseUomId()).getPrimaryId();
            require(id(rootId) && units.values().stream().allMatch(unit -> rootId.equals(unit.getPrimaryId())),
                "关联单位必须有明确共同根单位");
            if (!units.containsKey(rootId)) {
                Map<Long, BdUnitOfMeasure> roots = identities(mapper.lockUnits(actor.tenantId(), List.of(rootId)), actor.tenantId());
                require(roots.size() == 1 && roots.containsKey(rootId), "关联根单位缺失");
                units.putAll(roots);
            }
            Instant now = databaseNow();
            BdUnitOfMeasure root = units.get(rootId);
            checkCertifiedRoot(root, actor.tenantId(), rootId, now);
            for (BdUnitOfMeasure unit : units.values()) {
                checkCertifiedUnit(unit, actor.tenantId(), unit.getUomId(), now);
                require(rootId.equals(unit.getPrimaryId()) && root.getCategory().equals(unit.getCategory())
                    && root.getConversionVersion().equals(unit.getConversionVersion()), "关联单位维度或换算版本不一致");
            }
            checkTermQuantities(request, units.get(material.getBaseUomId()), units.get(request.orderUomId()), units.get(request.moqUomId()));
            LocalDateTime localNow = LocalDateTime.ofInstant(now, BUSINESS_ZONE);
            require(!request.termsEffectiveAt().isAfter(request.termsExpiresAt())
                && !request.termsEffectiveAt().isAfter(localNow) && !request.termsExpiresAt().isBefore(localNow),
                "条款尚未生效、已经过期或日期倒置");
            row.setOrderUomId(request.orderUomId()); row.setMoqUomId(request.moqUomId());
            row.setVerifiedMoqQty(request.verifiedMoqQty()); row.setOrderMultiple(request.orderMultiple());
            row.setAllowSplit(request.allowSplit()); row.setUnitsPerPack(request.unitsPerPack());
            row.setTermsEffectiveAt(Date.from(request.termsEffectiveAt().atZone(BUSINESS_ZONE).toInstant()));
            row.setTermsExpiresAt(Date.from(request.termsExpiresAt().atZone(BUSINESS_ZONE).toInstant()));
            row.setTermsVersion("T-" + UUID.randomUUID()); row.setTermsVerifiedAt(Date.from(now));
            row.setUpdateBy(actor.actorId()); row.setUpdateTime(Date.from(now));
            checkActor(actor);
            require(mapper.confirmTerms(row, request.expectedVersion(), SQL_DATE.format(request.termsEffectiveAt()),
                SQL_DATE.format(request.termsExpiresAt()), sqlTime(now)) == 1, "条款原版本 CAS 失败");
            return MapstructUtils.convert(row, BdMaterialVendorVo.class);
        });
    }

    private Map<Long, BdUnitOfMeasure> identities(List<BdUnitOfMeasure> rows, String tenantId) {
        require(rows != null, "单位来源缺失");
        Map<Long, BdUnitOfMeasure> units = new LinkedHashMap<>();
        for (BdUnitOfMeasure row : rows) {
            require(row != null && id(row.getUomId()) && tenantId.equals(row.getTenantId()) && "0".equals(row.getDelFlag()),
                "锁内单位来源租户或身份不符");
            require(units.putIfAbsent(row.getUomId(), row) == null, "锁内单位来源重复");
        }
        return units;
    }

    private void checkCertifiedRoot(BdUnitOfMeasure root, String tenantId, Long rootId, Instant now) {
        checkCertifiedUnit(root, tenantId, rootId, now);
        require(Long.valueOf(1).equals(root.getIsBase()) && rootId.equals(root.getPrimaryId())
            && BigDecimal.ONE.compareTo(root.getConversionRate()) == 0, "根单位未核验恒等自映射或存在多级链");
    }

    private void checkCertifiedUnit(BdUnitOfMeasure unit, String tenantId, Long expectedId, Instant now) {
        checkUnitIdentity(unit, tenantId, expectedId);
        require(!blank(unit.getCategory()) && !blank(unit.getConversionVersion()) && decimal(unit.getConversionRate(), 6)
            && id(unit.getPrimaryId()) && unit.getQuantityScale() != null && unit.getQuantityScale() >= 0
            && unit.getQuantityScale() <= 4 && unit.getConversionVerifiedAt() != null
            && !unit.getConversionVerifiedAt().toInstant().isAfter(now), "关联单位数量事实未核验或已失效");
        require(Long.valueOf(1).equals(unit.getIsBase())
            ? unit.getUomId().equals(unit.getPrimaryId()) && unit.getConversionRate().compareTo(BigDecimal.ONE) == 0
            : Long.valueOf(0).equals(unit.getIsBase()) && !unit.getUomId().equals(unit.getPrimaryId()),
            "关联单位基本标识或映射无效");
    }

    private void checkUnitIdentity(BdUnitOfMeasure unit, String tenantId, Long expectedId) {
        require(unit != null && expectedId.equals(unit.getUomId()) && tenantId.equals(unit.getTenantId())
            && active(unit.getStatus(), unit.getDelFlag()), "实际单位身份、租户或状态不符");
    }

    private void checkTermQuantities(BdMaterialVendorTermsConfirmBo request, BdUnitOfMeasure base,
            BdUnitOfMeasure order, BdUnitOfMeasure moq) {
        require(atScale(request.verifiedMoqQty(), moq.getQuantityScale())
            && atScale(request.orderMultiple(), order.getQuantityScale())
            && atScale(request.unitsPerPack(), base.getQuantityScale()), "条款数量不符合对应核验单位精度");
        BigDecimal moqRate = exactRate(moq.getConversionRate(), base.getConversionRate());
        exactRate(order.getConversionRate(), base.getConversionRate());
        BigDecimal moqBase = request.verifiedMoqQty().multiply(moqRate);
        require(decimal(moqBase, 4) && atScale(moqBase, base.getQuantityScale()), "起订量换算超限或不符合物料基本单位精度");
    }

    private BigDecimal exactRate(BigDecimal numerator, BigDecimal denominator) {
        try {
            BigDecimal rate = numerator.divide(denominator);
            require(decimal(rate, 6), "相对物料基本单位的换算比例超限");
            return rate;
        } catch (ArithmeticException invalid) {
            throw new ServiceException("相对物料基本单位的换算比例不能精确相除");
        }
    }

    private Instant databaseNow() {
        DatabaseClock clock = mapper.databaseClock();
        require(clock != null && "+08:00".equals(clock.databaseTimeZone()) && clock.epochSeconds() != null
            && clock.epochSeconds().signum() > 0 && clock.epochSeconds().stripTrailingZeros().scale() <= 0,
            "ERP 数据库会话必须明确为 +08:00 且提供有效业务时钟");
        try { return Instant.ofEpochSecond(clock.epochSeconds().longValueExact());
        } catch (ArithmeticException | DateTimeException invalid) {
            throw new ServiceException("ERP 数据库业务时钟无效");
        }
    }

    private static void checkReference(String expectedVersion, String reference) {
        require(expectedVersion == null || !expectedVersion.isBlank() && expectedVersion.length() <= 64, "原版本格式无效");
        require(reference != null && !reference.isBlank() && reference.length() <= 256, "确认必须有明确证据引用");
    }

    private static boolean validDate(LocalDateTime value) {
        return value != null && value.getYear() >= 1000 && value.getYear() <= 9999 && value.getNano() == 0;
    }

    private static boolean decimal(BigDecimal value, int scale) {
        if (value == null || value.signum() <= 0) return false;
        BigDecimal normalized = value.stripTrailingZeros();
        return (long) normalized.precision() - normalized.scale() <= 18L - scale && normalized.scale() <= scale;
    }

    private static boolean atScale(BigDecimal value, int scale) { return value.stripTrailingZeros().scale() <= scale; }
    private static boolean id(Long value) { return value != null && value > 0; }
    private static boolean active(Long status, String delFlag) { return Long.valueOf(1).equals(status) && "0".equals(delFlag); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String sqlTime(Instant value) { return SQL_DATE.format(LocalDateTime.ofInstant(value, BUSINESS_ZONE)); }
}

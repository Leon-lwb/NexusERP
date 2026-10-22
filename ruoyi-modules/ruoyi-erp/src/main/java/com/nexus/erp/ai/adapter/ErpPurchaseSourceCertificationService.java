package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.AllocationResult;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Stock;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Demand;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Input;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader.RawLockedFacts;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.HexFormat;

/** 同ERP物理事务的全来源认证、全局分配及精确采购数量链，不接受外部核验旗号。 */
@Service
@DS("erp")
public class ErpPurchaseSourceCertificationService {
    private final ErpCertifiedSourceBirthReader birthReader;
    private final ErpPurchaseCompetitionSourceReader previewPools;
    private final PuRequirementErpTransactionQualification qualification;
    private final ErpPurchaseSourceSchemaVerifier schemaVerifier;
    private final PuRequirementCompetitionPoolReader poolReader;
    private final ErpPurchaseCatalogSourceReader catalogReader;
    private final ErpPurchaseStockLineageVerifier inventoryVerifier;
    private final ErpPurchaseAllocationLineageReader lineageReader;

    public ErpPurchaseSourceCertificationService(PuRequirementErpTransactionQualification qualification,
            PuRequirementCompetitionPoolReader poolReader, ErpPurchaseCatalogSourceReader catalogReader,
            ErpPurchaseStockLineageVerifier inventoryVerifier, ErpPurchaseAllocationLineageReader lineageReader,
            ErpPurchaseSourceSchemaVerifier schemaVerifier) {
        this(qualification,poolReader,catalogReader,inventoryVerifier,lineageReader,schemaVerifier,null,null);
    }

    @Autowired
    public ErpPurchaseSourceCertificationService(
            PuRequirementErpTransactionQualification qualification,
            PuRequirementCompetitionPoolReader poolReader,
            ErpPurchaseCatalogSourceReader catalogReader,
            ErpPurchaseStockLineageVerifier inventoryVerifier,
            ErpPurchaseAllocationLineageReader lineageReader,
            ErpPurchaseSourceSchemaVerifier schemaVerifier,
            ErpPurchaseCompetitionSourceReader previewPools,
            ErpCertifiedSourceBirthReader birthReader) {
        this.birthReader=birthReader;this.previewPools=previewPools;
        this.qualification = Objects.requireNonNull(qualification); this.poolReader = Objects.requireNonNull(poolReader);
        this.catalogReader = Objects.requireNonNull(catalogReader); this.inventoryVerifier = Objects.requireNonNull(inventoryVerifier);
        this.lineageReader = Objects.requireNonNull(lineageReader);
        this.schemaVerifier = Objects.requireNonNull(schemaVerifier);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CertifiedPurchaseScope previewLocked(String tenantId,Long materialId,Long vendorId,LocalDate windowEnd,Set<Long> requestedDetailIds){
        require(previewPools!=null,"完整来源预览读取器缺失");
        return certifyCurrentSources(tenantId,materialId,vendorId,windowEnd,requestedDetailIds,true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CertifiedPurchaseScope prepareLocked(String tenantId, Long materialId, Long vendorId,
            LocalDate windowEnd, Set<Long> requestedDetailIds) {
        return certifyCurrentSources(tenantId,materialId,vendorId,windowEnd,requestedDetailIds,false);
    }

    /** 模式只决定是否允许创建技术guard，目录、库存、流水、需求及摘要认证全部共用。 */
    private CertifiedPurchaseScope certifyCurrentSources(String tenantId,Long materialId,Long vendorId,
            LocalDate windowEnd,Set<Long> requestedDetailIds,boolean preview){
        require(TenantHelper.isEnable() && tenantId != null && tenantId.matches("[0-9]{6,20}")
            && tenantId.equals(TenantHelper.getTenantId()), "请求租户不属于当前启用租户");
        require(materialId != null && materialId > 0 && vendorId != null && vendorId > 0
            && windowEnd != null && requestedDetailIds != null && !requestedDetailIds.isEmpty()
            && requestedDetailIds.size() <= 1000 && requestedDetailIds.stream().allMatch(id -> id != null && id > 0),
            "明确物料供应商窗口与逐需求身份缺失");
        Set<Long> requested = Set.copyOf(requestedDetailIds);
        qualification.requireSourceChainTransaction();
        schemaVerifier.requireTransactionalSources();
        var raw = preview ? previewPools.readLocked(tenantId,materialId,windowEnd) : poolReader.materialPool(tenantId, materialId, windowEnd);
        CatalogSnapshot catalog = catalogReader.readLocked(tenantId, materialId, vendorId);
        var lineage = lineageReader.readLocked(tenantId, materialId);
        // 库存有效期只能在其他等待式读取完成后认证；验证器最后一次时钟当前读必须是本链最新时点。
        var stockLineage = inventoryVerifier.verifyLocked(tenantId, materialId);
        var inventory = stockLineage == null ? null : stockLineage.inventory();
        require(raw != null && catalog != null && inventory != null && lineage != null, "完整当前读来源缺失");
        require(tenantId.equals(catalog.tenantId()) && materialId.toString().equals(catalog.material().materialId())
            && vendorId.toString().equals(catalog.vendorId()) && tenantId.equals(inventory.tenantId())
            && materialId.equals(inventory.materialId()) && catalog.material().baseUomId().equals(inventory.baseUomId().toString())
            && Objects.equals(catalog.baseUnit().quantityScale(), inventory.quantityScale())
            && "+08:00".equals(inventory.databaseTimeZone()), "库存目录身份、单位精度或时区不一致");
        LocalDate today = catalog.snapshotAt().atZone(ErpPurchasePoolCertification.DATABASE_ZONE).toLocalDate();
        require(inventory.snapshotAt() != null && today.equals(inventory.snapshotAt().atZone(ErpPurchasePoolCertification.DATABASE_ZONE).toLocalDate()),
            "独立库存业务日与目录不一致");
        Set<Long> stockIds = new HashSet<>();
        raw.stocks().forEach(row -> require(row != null && row.getStockId() != null && stockIds.add(row.getStockId()),
            "原始库存身份缺失或重复"));
        require(stockIds.equals(inventory.stockIds()),"库存全集在认证链中不一致");
        if(stockIds.isEmpty()) {
            require(birthReader!=null&&inventory.sourceBirth()!=null,"完整来源缺少持久出生，空集合不是可靠库存零");
            // 完整认证第三次独立观察，不能仅依赖上游快照布尔或一个出生摘要。
            var born=birthReader.verifyInitialEmptyLocked(tenantId,materialId);
            require(born.equals(inventory.sourceBirth())&&inventory.baseUomId().equals(born.baseUomId())
                &&Objects.equals(inventory.quantityScale(),born.quantityScale())
                &&catalog.baseUnit().conversionVersion().equals(born.conversionVersion())
                &&catalog.baseUnit().verifiedAt().equals(Instant.ofEpochMilli(born.unitVerifiedAt()))
                &&stockLineage.stocks().isEmpty()&&stockLineage.transactionIds().isEmpty()
                &&inventory.quantity().signum()==0&&inventory.reserved().signum()==0&&inventory.frozenQuantity().signum()==0,
                "完整来源与永久出生单位版本、空库存或原始流水证明不一致");
            // 第三层出生复读也可能等待锁；最后再完整认证库存及数据库时钟，不能沿用过期条款。
            var refreshed=inventoryVerifier.verifyLocked(tenantId,materialId);
            var latest=refreshed==null?null:refreshed.inventory();
            require(latest!=null&&born.equals(latest.sourceBirth())&&latest.stockIds().isEmpty()
                &&inventory.sourceVersions().equals(latest.sourceVersions())&&latest.quantity().signum()==0
                &&latest.reserved().signum()==0&&latest.frozenQuantity().signum()==0
                &&inventory.safety().compareTo(latest.safety())==0&&!latest.snapshotAt().isBefore(inventory.snapshotAt())
                &&today.equals(latest.snapshotAt().atZone(ErpPurchasePoolCertification.DATABASE_ZONE).toLocalDate()),
                "完整出生复读后库存范围、业务日或时钟改变");
            stockLineage=refreshed;inventory=latest;
        } else require(inventory.sourceBirth()==null,"已有库存不能使用空出生绕过原始来源链");
        Map<Long, PuRequirementCompetitionPoolMapper.RawStock> rawStocks = new HashMap<>();
        raw.stocks().forEach(row->rawStocks.put(row.getStockId(),row));
        Set<Long> provenStocks=new HashSet<>();BigDecimal originalReserved=BigDecimal.ZERO;
        for(var proof:stockLineage.stocks()) {
            require(proof!=null&&proof.stock()!=null&&proof.stock().stockId()!=null&&provenStocks.add(proof.stock().stockId()),
                "原始库存归属证明缺失或重复");
            var row=rawStocks.get(proof.stock().stockId());var original=proof.stock();
            require(row!=null&&Objects.equals(row.getTenantId(),original.tenantId())&&Objects.equals(row.getMaterialId(),original.materialId())
                &&Objects.equals(row.getBaseUomId(),original.baseUomId())&&Objects.equals(row.getVersion(),original.version())
                &&Objects.equals(row.getFrozenFlag(),original.frozenFlag())&&Objects.equals(row.getDelFlag(),original.delFlag())
                &&row.getStockQuantity()!=null&&original.stockQuantity()!=null&&row.getStockQuantity().compareTo(original.stockQuantity())==0
                &&row.getReservedQuantity()!=null&&original.reservedQuantity()!=null&&row.getReservedQuantity().compareTo(original.reservedQuantity())==0,
                "竞争池原库存与完整原始流水证明不一致");
            originalReserved=originalReserved.add(original.reservedQuantity());
        }
        require(provenStocks.equals(stockIds),"原始库存来源证明未覆盖整个物料库存");
        BigDecimal usableOwned=stockLineage.reservedByRequirementDetail().values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal frozenOwned=stockLineage.frozenReservedByRequirementDetail().values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        require(usableOwned.compareTo(inventory.reserved())==0&&usableOwned.add(frozenOwned).compareTo(originalReserved)==0,
            "完整原始预留所有权净额与库存余额不一致");
        require(lineage.snapshotAt() != null && !inventory.snapshotAt().isBefore(catalog.snapshotAt())
            && !inventory.snapshotAt().isBefore(lineage.snapshotAt()), "最终库存时钟早于其他来源认证时点");
        Instant assessedAt = inventory.snapshotAt();
        var terms = catalog.terms();
        require(terms != null && terms.verified() && terms.effectiveAt() != null && terms.expiresAt() != null
            && terms.verifiedAt() != null && !terms.effectiveAt().isAfter(assessedAt)
            && !terms.expiresAt().isBefore(assessedAt) && !terms.verifiedAt().isAfter(assessedAt),
            "最终认证时点的供应条款未知或过期");
        // 预留所有权必须由完整原始链产生；范围外owner仍扣共享供给，不能挪给本请求。
        var basicInput = ErpPurchasePoolCertification.certify(catalog, lineage, raw,
            new Stock(inventory.quantity(),inventory.reserved(),inventory.safety(),inventory.baseUomId().toString(),true),
            windowEnd, requested);
        Set<String> poolDemandIds=new HashSet<>();basicInput.demands().forEach(d->poolDemandIds.add(d.detailId()));
        Map<String,BigDecimal> ownedReservations=new TreeMap<>();
        stockLineage.reservedByRequirementDetail().forEach((id,amount)-> {
            require(id!=null&&id>0&&amount!=null&&amount.signum()>0,"已核预留归属身份或数量未知");
            if(poolDemandIds.contains(id.toString()))ownedReservations.put(id.toString(),amount);
        });
        var input=new ErpPurchaseDemandAllocator.AllocationInput(basicInput.scope(),basicInput.stock(),basicInput.demands(),
            basicInput.orders(),basicInput.commitments(),ownedReservations);
        AllocationResult allocation;
        try { allocation = new ErpPurchaseDemandAllocator().allocate(input); }
        catch (ErpPurchaseDemandAllocator.AllocationRejectedException invalid) {
            throw new ServiceException("采购全局额度认证拒绝：" + invalid.reason().name());
        }
        var quantityPolicy = new ErpPurchaseQuantityPolicy();
        List<CertifiedLine> lines = new ArrayList<>();
        for (var detail : allocation.allocations()) {
            if (detail.requestableBase().signum() == 0) continue;
            var demand = new Demand(tenantId,materialId.toString(),catalog.material().baseUomId(),detail.requestableBase(),
                detail.dueDate(),assessedAt,true);
            var evaluated = quantityPolicy.evaluate(new Input(demand,catalog.material(),catalog.terms(),catalog.baseUnit(),
                catalog.orderUnit(),catalog.moqUnit(),null,false));
            require(evaluated.allowed() && evaluated.excessBase() != null && evaluated.excessBase().signum() == 0,
                "MOQ、单位精确换算或包装条款不能满足当前额度");
            lines.add(new CertifiedLine(Long.valueOf(detail.detailId()),detail.dueDate(),detail.requestableBase(),
                evaluated.requestedOrderQty(),inventory.baseUomId(),Long.valueOf(catalog.terms().orderUomId())));
        }
        Map<String,String> versions = new TreeMap<>();
        catalog.sourceVersions().forEach((key,value) -> versions.put("catalog:" + key,value));
        inventory.sourceVersions().forEach((key,value) -> versions.put("inventory:" + key,value));
        stockLineage.sourceVersions().forEach((key,value)->versions.put("stock-lineage:"+key,value));
        lineage.sourceVersions().forEach((key,value) -> versions.put("lineage:" + key,value));
        // 不是外部时间/旗号：数量、需求窗口、期限、真实版本和整个竞争输入均进入稳定摘要。
        String fingerprint = fingerprint(input,catalog.material(),catalog.terms(),catalog.baseUnit(),catalog.orderUnit(),
            catalog.moqUnit(),versions,lines,freezeMrpSources(raw),stockLineage.reservedByRequirementDetail(),
            stockLineage.frozenReservedByRequirementDetail());
        return new CertifiedPurchaseScope(tenantId,materialId,vendorId,today,windowEnd,catalog,allocation,lines,versions,fingerprint,inventory.sourceBirth());
    }

    /** 同一采购目录的稳定冻结摘要；不含每次读取时钟，绝不充当跨事务写授权。 */
    public static String catalogFingerprint(CatalogSnapshot catalog) {
        require(catalog!=null&&catalog.material()!=null&&catalog.terms()!=null&&catalog.baseUnit()!=null
            &&catalog.orderUnit()!=null&&catalog.moqUnit()!=null&&catalog.sourceVersions()!=null,"冻结目录完整来源缺失");
        return fingerprint(catalog.tenantId(),catalog.vendorId(),catalog.relationId(),catalog.databaseTimeZone(),
            catalog.material(),catalog.terms(),catalog.baseUnit(),catalog.orderUnit(),catalog.moqUnit(),catalog.sourceVersions());
    }

    /** 将原始 MRP 数量、状态和归属冻结为不可变事实；净需求相等不表示来源未变。 */
    private static MrpSourceFacts freezeMrpSources(RawLockedFacts raw) {
        var details = raw.requirementDetails().stream().map(row -> new MrpDetailFact(
            row.getDetailId(), row.getTenantId(), row.getRequirementId(), row.getMaterialId(), row.getUnit(), row.getSourceType(),
            row.getRequiredQuantity(), row.getIssuedQuantity(), row.getStockQuantity(), row.getShortageQuantity(),
            epoch(row.getRequiredDate()), row.getStatus(), row.getDelFlag(), row.getBackflushFlag(), row.getActualConsumedQty(),
            epoch(row.getCreateTime()), epoch(row.getUpdateTime())))
            .sorted(Comparator.comparing(MrpDetailFact::detailId)).toList();
        var heads = raw.requirementHeads().stream().map(row -> new MrpHeadFact(
            row.getRequirementId(), row.getTenantId(), row.getRequirementNo(), row.getParentRequirementId(), row.getPlanId(),
            row.getPlanNo(), row.getBomId(), row.getMaterialId(), epoch(row.getRequirementDate()), row.getSourceType(), row.getStatus(),
            epoch(row.getConfirmedTime()), epoch(row.getIssuedTime()), epoch(row.getClosedTime()), row.getDelFlag(),
            epoch(row.getCreateTime()), epoch(row.getUpdateTime())))
            .sorted(Comparator.comparing(MrpHeadFact::requirementId)).toList();
        return new MrpSourceFacts(details, heads);
    }

    private static Instant epoch(Date value) { return value == null ? null : Instant.ofEpochMilli(value.getTime()); }
    private record MrpDetailFact(Long detailId, String tenantId, Long requirementId, Long materialId, String unit, String sourceType,
            BigDecimal requiredQuantity, BigDecimal issuedQuantity, BigDecimal stockQuantity, BigDecimal shortageQuantity,
            Instant requiredAt, Long status, String delFlag, Integer backflushFlag, BigDecimal actualConsumedQty,
            Instant createdAt, Instant updatedAt) { }
    private record MrpHeadFact(Long requirementId, String tenantId, String requirementNo, Long parentRequirementId,
            Long planId, String planNo, Long bomId, Long materialId, Instant requirementAt, String sourceType, String status,
            Instant confirmedAt, Instant issuedAt, Instant closedAt, String delFlag, Instant createdAt, Instant updatedAt) { }
    private record MrpSourceFacts(List<MrpDetailFact> details, List<MrpHeadFact> heads) { }

    private static void require(boolean value,String reason) {
        if (!value) throw new ServiceException("采购完整来源认证拒绝：" + reason);
    }

    private static String fingerprint(Object... facts) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (Object fact : facts) {
                byte[] bytes = canonical(fact).getBytes(StandardCharsets.UTF_8);
                // 长度前缀避免两个字符串分段不同却使用相同字节连接。
                hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                hash.update(bytes);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException unavailable) { throw new ServiceException("采购来源摘要算法不可用"); }
    }

    /** 固定类型、字段次序和集合排序；十进制等值及JVM集合顺序不能改变同一来源摘要。 */
    private static String canonical(Object value) {
        if (value == null) return "N;";
        if (value instanceof BigDecimal number) return "D:" + number.stripTrailingZeros().toPlainString() + ";";
        if (value instanceof CharSequence text) {
            StringBuilder encoded = new StringBuilder("S:").append(text.length()).append(':');
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < text.length(); i++) {
                int c = text.charAt(i);
                encoded.append(hex[c >>> 12]).append(hex[(c >>> 8) & 15]).append(hex[(c >>> 4) & 15]).append(hex[c & 15]);
            }
            return encoded.append(';').toString();
        }
        if (value instanceof Map<?,?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key,element) -> entries.add(canonical(key) + canonical(element)));
            Collections.sort(entries); return "M:" + canonical(entries);
        }
        if (value instanceof Set<?> set) {
            List<String> elements = set.stream().map(ErpPurchaseSourceCertificationService::canonical).sorted().toList();
            return "T:" + canonical(elements);
        }
        if (value instanceof List<?> list) {
            StringBuilder result = new StringBuilder("L:").append(list.size()).append(':');
            list.forEach(element -> result.append(canonical(element))); return result.append(';').toString();
        }
        if (value.getClass().isRecord() && value.getClass().getPackageName().equals("com.nexus.erp.ai.adapter")) {
            StringBuilder result = new StringBuilder("R:").append(canonical(value.getClass().getName()));
            try {
                for (var component : value.getClass().getRecordComponents()) {
                    result.append(canonical(component.getName())).append(canonical(component.getAccessor().invoke(value)));
                }
            } catch (ReflectiveOperationException inaccessible) { throw new ServiceException("采购来源摘要字段不可核验"); }
            return result.append(';').toString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof LocalDate || value instanceof Instant || value instanceof Enum<?>) {
            return canonical(value.getClass().getName()) + canonical(value.toString());
        }
        throw new ServiceException("采购来源摘要包含未支持类型");
    }

    /** 仅申请可见明细；不输出其他工单来源ID，也不在事务之外转授写入权限。 */
    public record CertifiedLine(Long detailId, LocalDate requiredDate, BigDecimal coveredDemandBase,
                                BigDecimal orderQuantity, Long baseUomId, Long orderUomId) { }

    /** 版本和来源摘要供同事务写入冻结；本对象不是controller接受的请求类型。 */
    public record CertifiedPurchaseScope(
            String tenantId,
            Long materialId,
            Long vendorId,
            LocalDate snapshotDate,
            LocalDate windowEnd,
            CatalogSnapshot catalog,
            AllocationResult allocation,
            List<CertifiedLine> lines,
            Map<String, String> sourceVersions,
            String sourceFingerprint,
            ErpCertifiedSourceBirthMapper.StoredBirth sourceBirth) {
        public CertifiedPurchaseScope(String tenantId,Long materialId,Long vendorId,LocalDate snapshotDate,LocalDate windowEnd,
                CatalogSnapshot catalog,AllocationResult allocation,List<CertifiedLine> lines,Map<String,String> sourceVersions,String sourceFingerprint){
            this(tenantId,materialId,vendorId,snapshotDate,windowEnd,catalog,allocation,lines,sourceVersions,sourceFingerprint,null);
        }
        public CertifiedPurchaseScope { lines = List.copyOf(lines); sourceVersions = Map.copyOf(sourceVersions); }
    }
}

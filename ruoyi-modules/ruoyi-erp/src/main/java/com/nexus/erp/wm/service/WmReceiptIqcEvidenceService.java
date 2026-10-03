package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.mq.iqc.ReceiptIqcSource;
import com.nexus.common.mq.iqc.ReceiptIqcSource.Inspection;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * IQC 授权仅来自当前 MES 真实读取，绝不来自收货单头或 MQ 中的结果、数量。
 * 先读取 MES，再进入 ERP 独立掌控的可重复读事务；这不是跨库 ACID。
 * 合格 IQC 认证保持关闭：qc_iqc 缺少原生原始数量单位及创建时来源范围契约。
 * 既有 V1 记录和当前 ERP 数值相等，绝不能升级缺失的历史来源证明。
 * 没有任何公开方法接收 Inspection、EvidenceRow 或已验证 DTO 作为持久化输入。
 */
@Service
public class WmReceiptIqcEvidenceService {
    private static final String SOURCE_KIND = "MES_QC_IQC_RECEIVE_MATERIAL_V1";
    private static final String SOURCE_VERSION_PREFIX = "QC_IQC_CURRENT_V1:";
    private final WmReceiptIqcEvidenceMapper mapper;
    private final ReceiptIqcSource source;
    private final PuRequirementErpTransactionExecutor transactions;
    private final PuRequirementErpTransactionQualification qualification;

    public WmReceiptIqcEvidenceService(WmReceiptIqcEvidenceMapper mapper, ReceiptIqcSource source,
        PuRequirementErpTransactionExecutor transactions, PuRequirementErpTransactionQualification qualification) {
        this.mapper = Objects.requireNonNull(mapper);
        this.source = Objects.requireNonNull(source);
        this.transactions = Objects.requireNonNull(transactions);
        this.qualification = Objects.requireNonNull(qualification);
    }

    /** 稳定接口；拒绝缺少原生原始数量单位或创建时来源证明的 V1 证据。 */
    public void requireLocked(String tenantId, Long receiveId, Long materialId, Long baseUomId, BigDecimal expectedQty) {
        requireLockedEvidence(tenantId, receiveId, materialId, baseUomId, expectedQty);
    }

    /** 同样执行封闭的 V1 核验；实现原生创建时来源采集之前不签发 EvidenceIdentity。 */
    public EvidenceIdentity requireLockedEvidence(String tenantId, Long receiveId, Long materialId,
                                                  Long baseUomId, BigDecimal expectedQty) {
        requireTenant(tenantId);
        qualification.requireSourceChainTransaction();
        positiveId(receiveId); positiveId(materialId); positiveId(baseUomId);
        requireNativeSchema();
        EvidenceRow evidence = mapper.selectReceiptMaterialForUpdate(tenantId, receiveId, materialId);
        if (evidence == null) throw fail("IQC_EVIDENCE_MISSING");
        requireIntegrity(evidence, tenantId);
        requireOriginalQuantityContract();
        Scope scope = requireScope(tenantId, receiveId, materialId);
        if (!"QUALIFIED".equals(evidence.getInspectResult())) throw fail("IQC_EVIDENCE_NOT_QUALIFIED");
        requireScopeMatches(evidence, scope);
        if (!baseUomId.equals(evidence.getBaseUomId())) throw fail("IQC_EVIDENCE_UNIT_MISMATCH");
        requireQuantity(expectedQty, 18, scope.quantityScale());
        // 主入库流程绑定真实来源行、明细及其恰好一次的台账身份。此核验
        // 不能将物料级 IQC 转化为某一收货行的分配或认证。
        if (expectedQty.compareTo(scope.quantity()) > 0) throw fail("IQC_EVIDENCE_QUANTITY_EXCEEDED");
        qualification.requireSourceChainTransaction();
        return new EvidenceIdentity(evidence.getInspectionId(), evidence.getSourceVersion(),
            evidence.getActualBodySha256(), evidence.getReceiptScopeSha256(), evidence.getEvidenceSha256(),
            evidence.getBaseUomId(), evidence.getInspectedQuantity());
    }

    /**
     * 只有 MES 的实际 UNQUALIFIED 结论才能进入基于身份的不合格隔离。不得推断数量或单位
     * 契约，不插入证据，也不允许合格放行。
     * 真实不合格消息重复到达时可幂等重施保护，包括已入账的收货单。
     * 实现原生数量单位及创建时来源采集之前，合格认证保持关闭。
     */
    public void consumeCompleted(IqcCompletedMessage message, Consumer<Completion> projection) {
        if (projection == null) throw fail("IQC_PROJECTION_REQUIRED");
        Inspection actual = readSource(message);
        if (!"UNQUALIFIED".equals(actual.result())) requireOriginalQuantityContract();
        transactions.executeRepeatableRead(actual.tenantId(), () -> {
            NegativeIdentity identity = requireUnqualifiedIdentity(actual);
            isolateOriginalTargets(actual, identity);
            // 仅用于隔离的投影不包含数量、单位、证书或放行权限。
            projection.accept(new Completion(actual.inspectionId(), actual.inspectionCode(), actual.receiveId(),
                actual.materialId(), "UNQUALIFIED", null));
            qualification.requireSourceChainTransaction();
            return null;
        });
    }

    /** 仅做核验；消费者预检通过 consumeCompleted 幂等重施安全保护。 */
    public void verifyReplay(IqcCompletedMessage message) {
        Inspection actual = readSource(message);
        if (!"UNQUALIFIED".equals(actual.result())) requireOriginalQuantityContract();
        transactions.executeRepeatableRead(actual.tenantId(), () -> { requireUnqualifiedIdentity(actual); return null; });
    }

    private NegativeIdentity requireUnqualifiedIdentity(Inspection actual) {
        qualification.requireSourceChainTransaction();
        IqcNativeSchema.requireErpIsolationDefinition(mapper.selectIsolationColumns(), mapper.selectIsolationIndexes());
        ReceiptHeader header = mapper.selectReceiveForUpdate(actual.tenantId(), actual.receiveId());
        if (header == null || !actual.tenantId().equals(header.getTenantId())
            || !actual.receiveId().equals(header.getReceiveId()) || !"0".equals(header.getDelFlag())) {
            throw fail("IQC_NEGATIVE_RECEIPT_IDENTITY_INVALID");
        }
        List<ReceiptLine> rows = mapper.selectMaterialIdentitiesForUpdate(actual.tenantId(), actual.receiveId(), actual.materialId());
        if (rows == null || rows.isEmpty()) throw fail("IQC_NEGATIVE_MATERIAL_IDENTITY_MISSING");
        Set<Long> ids = new HashSet<>(); boolean active = false;
        for (ReceiptLine row : rows) {
            if (row == null || row.getLineId() == null || row.getLineId() <= 0 || !ids.add(row.getLineId())
                || !actual.tenantId().equals(row.getTenantId()) || !actual.receiveId().equals(row.getReceiveId())
                || !actual.materialId().equals(row.getMaterialId())
                || !("0".equals(row.getDelFlag()) || "2".equals(row.getDelFlag()))) {
                throw fail("IQC_NEGATIVE_MATERIAL_IDENTITY_INVALID");
            }
            active |= "0".equals(row.getDelFlag());
        }
        if (!active) throw fail("IQC_NEGATIVE_MATERIAL_IDENTITY_MISSING");
        qualification.requireSourceChainTransaction();
        Set<Long> activeLines = new HashSet<>();
        for (ReceiptLine row : rows) if ("0".equals(row.getDelFlag())) activeLines.add(row.getLineId());
        return new NegativeIdentity(header, activeLines);
    }

    /** 任何安全更新之前都必须锁定原始生产方的完整目标集合；绝不依据数量确定目标。 */
    private void isolateOriginalTargets(Inspection actual, NegativeIdentity identity) {
        String tenant = actual.tenantId(); Long receive = actual.receiveId(); Long material = actual.materialId();
        List<IsolationDetail> rawDetails = mapper.selectIsolationDetails(tenant, receive);
        List<IsolationTx> rawTransactions = mapper.selectIsolationReceiptTransactions(tenant, receive);
        if (rawDetails == null || rawTransactions == null) throw fail("IQC_NEGATIVE_ORIGINAL_TARGET_MISSING");
        List<IsolationDetail> details = new ArrayList<>(); Set<Long> detailIds = new HashSet<>();
        Map<Long, Long> batchWarehouses = new TreeMap<>();
        for (IsolationDetail d : rawDetails) {
            if (d == null) throw fail("IQC_NEGATIVE_DETAIL_PARENT_INVALID");
            if (!material.equals(d.getMaterialId()) && !identity.lines().contains(d.getLineId())) continue;
            if (!tenant.equals(d.getTenantId()) || !receive.equals(d.getReceiveId()) || !material.equals(d.getMaterialId())
                || !identity.lines().contains(d.getLineId()) || d.getDetailId() == null || d.getDetailId() <= 0
                || !detailIds.add(d.getDetailId()) || !("0".equals(d.getDelFlag()) || "2".equals(d.getDelFlag()))) {
                throw fail("IQC_NEGATIVE_DETAIL_PARENT_INVALID");
            }
            if (!"0".equals(d.getDelFlag())) continue;
            positiveId(d.getBatchId()); positiveId(d.getWarehouseId());
            if (!nullableId(d.getAreaId()) || !nullableId(d.getLocationId()) || !nullableId(d.getSnId())) {
                throw fail("IQC_NEGATIVE_DETAIL_PARENT_INVALID");
            }
            Long previous = batchWarehouses.putIfAbsent(d.getBatchId(), d.getWarehouseId());
            if (previous != null && !previous.equals(d.getWarehouseId())) throw fail("IQC_NEGATIVE_BATCH_PARENT_INVALID");
            details.add(d);
        }
        if (details.isEmpty()) throw fail("IQC_NEGATIVE_ORIGINAL_TARGET_MISSING");
        Map<Long, IsolationTx> originalStocks = new TreeMap<>(); Set<Long> txIds = new HashSet<>(); Set<Long> covered = new HashSet<>();
        for (IsolationTx tx : rawTransactions) {
            if (tx == null) throw fail("IQC_NEGATIVE_TX_PARENT_INVALID");
            if (!material.equals(tx.getMaterialId()) && !identity.lines().contains(tx.getLineId())) continue;
            if (!tenant.equals(tx.getTenantId()) || !receive.equals(tx.getReceiveId()) || !material.equals(tx.getMaterialId())
                || !identity.lines().contains(tx.getLineId()) || tx.getTxId() == null || tx.getTxId() <= 0
                || !txIds.add(tx.getTxId()) || tx.getStockId() == null || tx.getStockId() <= 0
                || !"WM_MATERIAL_RECEIVE".equals(tx.getSourceDocType()) || !"RECEIVE_IN".equals(tx.getTxType())
                || !Integer.valueOf(1).equals(tx.getTxFlag()) || !"0".equals(tx.getDelFlag())) {
                throw fail("IQC_NEGATIVE_TX_PARENT_INVALID");
            }
            boolean matched = false;
            for (IsolationDetail d : details) if (Objects.equals(d.getLineId(), tx.getLineId())
                && dimension(d.getBatchId(), d.getWarehouseId(), d.getAreaId(), d.getLocationId(), d.getSnId())
                    .equals(dimension(tx.getBatchId(), tx.getWarehouseId(), tx.getAreaId(), tx.getLocationId(), tx.getSnId()))) {
                covered.add(d.getDetailId()); matched = true;
            }
            if (!matched) throw fail("IQC_NEGATIVE_TX_PARENT_INVALID");
            IsolationTx previous = originalStocks.putIfAbsent(tx.getStockId(), tx);
            if (previous != null && !txDimension(previous).equals(txDimension(tx))) throw fail("IQC_NEGATIVE_STOCK_PARENT_INVALID");
        }
        boolean posted = Set.of("FINISHED", "COMPLETED", "EXECUTING").contains(Objects.toString(identity.header().getStatus(), ""));
        if (posted && (originalStocks.isEmpty() || covered.size() != details.size())) {
            throw fail("IQC_NEGATIVE_ORIGINAL_TARGET_MISSING");
        }
        // 批次标记也是真实安全写入；没有原库存流水时仍须核验全部分配明细的仓储父链。
        for (IsolationDetail detail : details) {
            requireStorageParent(tenant, detail.getWarehouseId(), detail.getAreaId(), detail.getLocationId());
        }
        List<IsolationStock> stocks = new ArrayList<>(); List<IsolationBatch> batches = new ArrayList<>();
        for (var entry : originalStocks.entrySet()) {
            IsolationStock stock = mapper.selectIsolationStock(tenant, entry.getKey()); IsolationTx tx = entry.getValue();
            if (stock == null || !tenant.equals(stock.getTenantId()) || !entry.getKey().equals(stock.getStockId())
                || !material.equals(stock.getMaterialId()) || !"0".equals(stock.getDelFlag())
                || !stockDimension(stock).equals(txDimension(tx)) || stock.getVersion() == null || stock.getVersion() < 0
                || !(Integer.valueOf(0).equals(stock.getFrozenFlag()) || Integer.valueOf(1).equals(stock.getFrozenFlag()))) {
                throw fail("IQC_NEGATIVE_STOCK_PARENT_INVALID");
            }
            if (stock.getSourceHeadTxId() != null) {
                IsolationTx head = mapper.selectIsolationSourceHead(tenant, stock.getSourceHeadTxId());
                if (head == null || !tenant.equals(head.getTenantId()) || !stock.getSourceHeadTxId().equals(head.getTxId())
                    || !stock.getStockId().equals(head.getStockId()) || !material.equals(head.getMaterialId())
                    || !"0".equals(head.getDelFlag()) || !stockDimension(stock).equals(txDimension(head))) {
                    throw fail("IQC_NEGATIVE_STOCK_PARENT_INVALID");
                }
            }
            requireStorageParent(tenant, stock.getWarehouseId(), stock.getAreaId(), stock.getLocationId()); stocks.add(stock);
        }
        for (var entry : batchWarehouses.entrySet()) {
            IsolationBatch batch = mapper.selectIsolationBatch(tenant, entry.getKey());
            if (batch == null || !tenant.equals(batch.getTenantId()) || !entry.getKey().equals(batch.getBatchId())
                || !material.equals(batch.getMaterialId()) || !entry.getValue().equals(batch.getWarehouseId())
                || !"WM_MATERIAL_RECEIVE".equals(batch.getSourceDocType()) || !receive.equals(batch.getReceiveId())
                || !"0".equals(batch.getDelFlag())) throw fail("IQC_NEGATIVE_BATCH_PARENT_INVALID");
            batches.add(batch);
        }
        // 所有目标现已锁定，且已核验其真实身份及父级来源。
        for (IsolationStock stock : stocks) if (stock.getFrozenFlag() == 0) {
            if (mapper.freezeIsolationStock(tenant, stock) != 1) throw fail("IQC_NEGATIVE_STOCK_FREEZE_CONFLICT");
        }
        for (IsolationBatch batch : batches) if (!"UNQUALIFIED".equals(batch.getQcStatus())) {
            if (mapper.markIsolationBatch(tenant, batch) != 1) throw fail("IQC_NEGATIVE_BATCH_MARK_CONFLICT");
        }
        qualification.requireSourceChainTransaction();
    }

    private void requireStorageParent(String tenant, Long warehouse, Long area, Long location) {
        IsolationStorage s = mapper.selectIsolationStorage(warehouse, area, location);
        if (s == null || !tenant.equals(s.getWarehouseTenant()) || !warehouse.equals(s.getWarehouseId())
            || !"0".equals(s.getWarehouseDel()) || (area == null && location != null)
            || (area != null && (!tenant.equals(s.getAreaTenant()) || !area.equals(s.getAreaId())
                || !warehouse.equals(s.getAreaWarehouseId()) || !"0".equals(s.getAreaDel())))
            || (location != null && (!tenant.equals(s.getLocationTenant()) || !location.equals(s.getLocationId())
                || !warehouse.equals(s.getLocationWarehouseId()) || !area.equals(s.getLocationAreaId())
                || !"0".equals(s.getLocationDel())))) throw fail("IQC_NEGATIVE_STORAGE_PARENT_INVALID");
    }
    private static boolean nullableId(Long id) { return id == null || id > 0; }
    private static String dimension(Long batch, Long warehouse, Long area, Long location, Long sn) {
        return batch + "|" + warehouse + "|" + area + "|" + location + "|" + sn;
    }
    private static String txDimension(IsolationTx t) { return dimension(t.getBatchId(), t.getWarehouseId(), t.getAreaId(), t.getLocationId(), t.getSnId()); }
    private static String stockDimension(IsolationStock s) { return dimension(s.getBatchId(), s.getWarehouseId(), s.getAreaId(), s.getLocationId(), s.getSnId()); }
    private record NegativeIdentity(ReceiptHeader header, Set<Long> lines) { }

    private Inspection readSource(IqcCompletedMessage message) {
        if (message == null || message.getTenantId() == null || message.getTenantId() < 0
            || blank(message.getMessageId()) || message.getMessageId().length() > 128) {
            throw fail("IQC_MESSAGE_IDENTITY_INVALID");
        }
        String tenantId = String.format(java.util.Locale.ROOT, "%06d", message.getTenantId());
        requireTenant(tenantId);
        positiveId(message.getIqcId());
        requireOutsideTransaction();
        Inspection actual;
        try {
            actual = source.readCompletedIdentity(tenantId, message.getIqcId());
        } catch (RuntimeException unavailable) {
            // 不得泄露 JDBC 诊断、数据库目录名、连接地址或来源数据主体。
            throw fail("IQC_SOURCE_UNAVAILABLE");
        }
        requireOutsideTransaction();
        requireTenant(tenantId);
        if (actual == null || !tenantId.equals(actual.tenantId())
            || !message.getIqcId().equals(actual.inspectionId())
            || (message.getReceiveId() != null && !message.getReceiveId().equals(actual.receiveId()))
            || (message.getMaterialId() != null && !message.getMaterialId().equals(actual.materialId()))) {
            throw fail("IQC_SOURCE_IDENTITY_MISMATCH");
        }
        positiveId(actual.receiveId()); positiveId(actual.materialId()); positiveId(actual.inspectorId());
        if (!"COMPLETED".equals(actual.status()) || !result(actual.result()) || blank(actual.inspectionCode())
            || actual.inspectionCode().length() > 64 || !hash(actual.actualBodySha256())
            || !Objects.equals(actual.sourceVersion(), SOURCE_VERSION_PREFIX + actual.actualBodySha256())) {
            throw fail("IQC_SOURCE_NOT_COMPLETED");
        }
        requireTimes(actual.createdAt(), actual.completedAt(), actual.updatedAt(), actual.observedAt(),
            System.currentTimeMillis());
        // 只有真实的 UNQUALIFIED 身份才能进入不合格隔离。其原始数量
        // 特意不作为依据；合格观测仍然缺少创建时来源证明。
        if (!"UNQUALIFIED".equals(actual.result())) requireOriginalQuantityContract();
        return actual;
    }

    private void requireNativeSchema() {
        qualification.requireSourceChainTransaction();
        IqcNativeSchema.requireErpDefinition(mapper.selectNativeColumns(), mapper.selectNativeIndexes());
    }

    private Scope requireScope(String tenantId, Long receiveId, Long materialId) {
        requireNativeSchema();
        ReceiptHeader header = mapper.selectReceiveForUpdate(tenantId, receiveId);
        if (header == null || !tenantId.equals(header.getTenantId()) || !receiveId.equals(header.getReceiveId())
            || !"0".equals(header.getDelFlag()) || blank(header.getReceiveCode()) || header.getCreateTime() == null
            || header.getStatus() == null
            || !Set.of("APPROVED", "SHELVED", "EXECUTING", "FINISHED", "COMPLETED").contains(header.getStatus())) {
            throw fail("IQC_RECEIPT_SOURCE_INVALID");
        }
        List<ReceiptLine> rows = mapper.selectMaterialLinesForUpdate(tenantId, receiveId, materialId);
        MaterialUnit material = mapper.selectMaterialForUpdate(tenantId, materialId);
        if (material == null || !tenantId.equals(material.getTenantId()) || !materialId.equals(material.getMaterialId())
            || !"0".equals(material.getDelFlag())) throw fail("IQC_MATERIAL_UNIT_UNKNOWN");
        positiveId(material.getBaseUomId());
        NativeUnit unit = mapper.selectUnitForUpdate(tenantId, material.getBaseUomId());
        if (unit == null || !tenantId.equals(unit.getTenantId()) || !material.getBaseUomId().equals(unit.getUomId())
            || !"0".equals(unit.getDelFlag()) || !Integer.valueOf(1).equals(unit.getStatus())
            || unit.getQuantityScale() == null || unit.getQuantityScale() < 0 || unit.getQuantityScale() > 4) {
            throw fail("IQC_NATIVE_UNIT_UNKNOWN");
        }
        if (rows == null || rows.isEmpty() || rows.stream().anyMatch(Objects::isNull)) {
            throw fail("IQC_RECEIPT_LINES_MISSING");
        }
        List<ReceiptLine> lines = new ArrayList<>(rows);
        for (ReceiptLine line : lines) positiveId(line.getLineId());
        lines.sort(Comparator.comparing(ReceiptLine::getLineId));
        List<Object> body = new ArrayList<>(List.of(SOURCE_KIND, tenantId, receiveId, materialId,
            header.getReceiveCode(), header.getCreateTime(), material.getBaseUomId(), unit.getQuantityScale()));
        body.add(header.getNoticeId()); body.add(header.getPurId()); body.add(header.getVendorId());
        Set<Long> ids = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptLine line : lines) {
            if (!tenantId.equals(line.getTenantId()) || !receiveId.equals(line.getReceiveId())
                || !materialId.equals(line.getMaterialId()) || !ids.add(line.getLineId())
                || !("0".equals(line.getDelFlag()) || "2".equals(line.getDelFlag()))) {
                throw fail("IQC_RECEIPT_LINE_SCOPE_INVALID");
            }
            body.add(line.getLineId()); body.add(line.getSourceDocLineId()); body.add(line.getPurchaseLineId());
            body.add(line.getDelFlag()); body.add(line.getBaseUomId()); body.add(line.getQuantityReceived());
            if ("0".equals(line.getDelFlag())) {
                // 历史 varchar 单位仅在其为规范的原生基本单位标识时才可接受。
                // 不允许猜测显示名称、进行采购单位换算或混合单位求和。
                if (!material.getBaseUomId().toString().equals(line.getBaseUomId())) {
                    throw fail("IQC_RECEIPT_UNIT_MISMATCH");
                }
                requireQuantity(line.getQuantityReceived(), 18, unit.getQuantityScale());
                total = total.add(line.getQuantityReceived());
            }
        }
        requireQuantity(total, 14, unit.getQuantityScale());
        return new Scope(receiveId, materialId, material.getBaseUomId(), unit.getQuantityScale(), total,
            sha(body.toArray()), header.getCreateTime().getTime());
    }

    private void requireActualScope(Inspection actual, Scope scope) {
        // 历史数值与当前数值相等，不构成原始数量单位或范围契约。
        requireOriginalQuantityContract();
    }

    private EvidenceRow findExisting(IqcCompletedMessage message, Inspection actual) {
        EvidenceRow byMessage = mapper.selectMessageForUpdate(actual.tenantId(), message.getMessageId());
        EvidenceRow byInspection = mapper.selectInspectionForUpdate(actual.tenantId(), actual.inspectionId());
        EvidenceRow byReceipt = mapper.selectReceiptMaterialForUpdate(actual.tenantId(), actual.receiveId(), actual.materialId());
        EvidenceRow existing = null;
        for (EvidenceRow row : new EvidenceRow[] {byMessage, byInspection, byReceipt}) {
            if (row == null) continue;
            requireIntegrity(row, actual.tenantId());
            if (!actual.inspectionId().equals(row.getInspectionId()) || !actual.receiveId().equals(row.getReceiveId())
                || !actual.materialId().equals(row.getMaterialId())
                || (existing != null && !existing.getEvidenceId().equals(row.getEvidenceId()))) {
                throw fail("IQC_EVIDENCE_IDENTITY_CONFLICT");
            }
            existing = row;
        }
        return existing;
    }

    private void requireReplayMatches(EvidenceRow row, Inspection actual, Scope scope) {
        requireScopeMatches(row, scope);
        if (!actual.actualBodySha256().equals(row.getActualBodySha256())
            || !actual.sourceVersion().equals(row.getSourceVersion()) || !actual.result().equals(row.getInspectResult())
            || !actual.inspectionCode().equals(row.getInspectionCode()) || !actual.inspectorId().equals(row.getInspectorId())
            || !Objects.equals(actual.createdAt(), millis(row.getSourceCreatedAt()))
            || !Objects.equals(actual.completedAt(), millis(row.getCompletedAt()))
            || !Objects.equals(actual.updatedAt(), millis(row.getSourceUpdatedAt()))) {
            throw fail("IQC_EVIDENCE_IMMUTABLE_CONFLICT");
        }
    }

    private void requireScopeMatches(EvidenceRow row, Scope scope) {
        if (!scope.receiveId().equals(row.getReceiveId()) || !scope.materialId().equals(row.getMaterialId())
            || !scope.baseUomId().equals(row.getBaseUomId()) || scope.quantityScale() != row.getQuantityScale()
            || scope.quantity().compareTo(row.getInspectedQuantity()) != 0
            || !scope.sha256().equals(row.getReceiptScopeSha256())
            || row.getCompletedAt().getTime() < scope.receiptCreatedAt()) throw fail("IQC_EVIDENCE_RECEIPT_CHANGED");
    }

    private EvidenceRow evidence(IqcCompletedMessage message, Inspection actual, Scope scope) {
        // 绝不能将 scope.baseUomId() 赋给历史上未包含单位的 MES 数量。此工厂
        // 在实现原生创建时数量单位及来源采集之前保持关闭。
        requireOriginalQuantityContract();
        throw fail("UNIT_ORIGIN_UNPROVEN");
    }

    private static void requireOriginalQuantityContract() {
        // 这是 V1 的结构性限制，不是可配置或由 DTO 提供的已验证标记。既有 V1
        // 证据存储的是推断出的 ERP 单位，而非原生 MES 创建时来源证明，不能据此授权。
        throw fail("UNIT_ORIGIN_UNPROVEN");
    }

    private void requireIntegrity(EvidenceRow row, String tenantId) {
        positiveId(row.getEvidenceId()); positiveId(row.getInspectionId()); positiveId(row.getReceiveId());
        positiveId(row.getMaterialId()); positiveId(row.getBaseUomId()); positiveId(row.getInspectorId());
        if (!tenantId.equals(row.getTenantId()) || !SOURCE_KIND.equals(row.getSourceKind())
            || !result(row.getInspectResult()) || blank(row.getInspectionCode()) || blank(row.getFirstMessageId())
            || row.getQuantityScale() == null || row.getQuantityScale() < 0 || row.getQuantityScale() > 4
            || !hash(row.getActualBodySha256()) || !hash(row.getReceiptScopeSha256()) || !hash(row.getEvidenceSha256())
            || !Objects.equals(row.getSourceVersion(), SOURCE_VERSION_PREFIX + row.getActualBodySha256())
            || row.getObservedAt() == null || row.getCertifiedAt() == null) throw fail("IQC_EVIDENCE_INTEGRITY_INVALID");
        requireQuantity(row.getInspectedQuantity(), 14, row.getQuantityScale());
        requireTimes(millis(row.getSourceCreatedAt()), millis(row.getCompletedAt()), millis(row.getSourceUpdatedAt()),
            row.getObservedAt().getTime(), row.getCertifiedAt().getTime());
        if (row.getCertifiedAt().getTime() > System.currentTimeMillis()
            || !row.getEvidenceSha256().equals(evidenceSha(row))) throw fail("IQC_EVIDENCE_INTEGRITY_INVALID");
    }

    private static String evidenceSha(EvidenceRow row) {
        return sha(row.getEvidenceId(), row.getTenantId(), row.getInspectionId(), row.getInspectionCode(),
            row.getReceiveId(), row.getMaterialId(), row.getBaseUomId(), row.getQuantityScale(), row.getInspectedQuantity(),
            row.getInspectResult(), row.getInspectorId(), row.getSourceKind(), row.getSourceVersion(),
            row.getActualBodySha256(), row.getReceiptScopeSha256(), row.getFirstMessageId(), row.getSourceCreatedAt(),
            row.getCompletedAt(), row.getSourceUpdatedAt(), row.getObservedAt(), row.getCertifiedAt());
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || !tenantId.matches("[0-9]{6,20}") || !TenantHelper.isEnable()
            || !tenantId.equals(TenantHelper.getTenantId())) throw fail("IQC_TENANT_MISMATCH");
    }
    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.getResourceMap().isEmpty()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || TransactionContext.getXID() != null) {
            throw fail("IQC_SOURCE_REQUIRES_FRESH_THREAD");
        }
    }
    private static void requireTimes(Long created, Long completed, Long updated, long observed, long upperBound) {
        if (created == null || completed == null || created <= 0 || completed < created
            || observed < completed || observed > upperBound
            || (updated != null && (updated < created || updated > observed))) throw fail("IQC_SOURCE_TIME_INVALID");
    }
    private static void requireQuantity(BigDecimal qty, int precision, int scale) {
        if (qty == null || qty.signum() <= 0 || qty.stripTrailingZeros().scale() > scale
            || qty.compareTo(BigDecimal.TEN.pow(precision - 4)) >= 0) throw fail("IQC_NATIVE_QUANTITY_INVALID");
    }
    private static void positiveId(Long id) { if (id == null || id <= 0) throw fail("IQC_SOURCE_IDENTITY_INVALID"); }
    private static boolean result(String result) { return "QUALIFIED".equals(result) || "UNQUALIFIED".equals(result); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static boolean hash(String s) { return s != null && s.matches("[0-9a-f]{64}"); }
    private static Long millis(Date d) { return d == null ? null : d.getTime(); }
    private static ServiceException fail(String code) { return new ServiceException(code); }

    /** 使用长度分隔并区分空值的 SHA 摘要；保留精确十进制数值，不舍入，也不使用有损双精度浮点数。 */
    private static String sha(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                String text = value instanceof BigDecimal qty ? qty.stripTrailingZeros().toPlainString()
                    : value instanceof Date date ? Long.toString(date.getTime()) : Objects.toString(value, null);
                byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (text == null ? 0 : 1));
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':'); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw fail("IQC_DIGEST_UNAVAILABLE"); }
    }
    private record Scope(Long receiveId, Long materialId, Long baseUomId, int quantityScale,
                         BigDecimal quantity, String sha256, long receiptCreatedAt) { }
    public record Completion(Long inspectionId, String inspectionCode, Long receiveId, Long materialId,
                             String result, BigDecimal quantity) { }
    public record EvidenceIdentity(Long inspectionId, String sourceVersion, String actualBodySha256,
                                   String receiptScopeSha256, String evidenceSha256,
                                   Long baseUomId, BigDecimal inspectedQuantity) { }
}

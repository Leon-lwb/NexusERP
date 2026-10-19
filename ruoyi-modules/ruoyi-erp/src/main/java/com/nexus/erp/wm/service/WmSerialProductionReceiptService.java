package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper;
import com.nexus.erp.prd.service.impl.*;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import static com.nexus.erp.wm.service.WmSerialInventoryService.need;

/**
 * 调用方必须在ERP事务外完成MES分页读取；本入口不连接MES，不发布旧完工异步路径。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class WmSerialProductionReceiptService
        implements com.nexus.common.trace.port.ProductionManifestPostingPort {
    @org.springframework.beans.factory.annotation.Autowired
    private WmTraceMaterialConsumptionService actualConsumption;

    @org.springframework.beans.factory.annotation.Autowired
    private WmTraceNoGoodOutputService noGoodOutput;

    @org.springframework.beans.factory.annotation.Autowired
    private WmBatchProductionReceiptProofService batchProof;

    private final WmSerialInventoryRepository store;
    private final WmSerialInventoryService inventory;
    private final WmSerialInventoryProofService proofs;
    private final WmProductionRecptWriteGuard source;
    private final IWmProductionRecptService receipts;
    private final WmProductionRecptMapper receiptMapper;
    private final IWmBatchService batches;
    private final PrdWorkOrderServiceImpl workOrders;
    private final PrdBackflushServiceImpl backflush;
    private final ProductionCompletionPostingMapper completionMapper;
    private final IPutawayStrategyService putaway;

    /**
     * 本次业务执行的稳定结果，重放时返回原已确认事实。
     *
     * @param receiptId 原生产入库凭据标识；无良品时允许不存在。
     * @param postingGroupId 本次完整过账集合的稳定组标识。
     * @param contentHash 按稳定顺序计算的完整内容摘要。
     * @param replay 当前结果是否来自原命令重放。
     */
    public record Result(Long receiptId, Long postingGroupId, String contentHash, boolean replay) {}

    /**
     * 在库存接收事务内整体应用产品清单及实际用料，良品和报废数量分别来自明确身份集合。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @return 首次已提交的完整入库或无良品交付回执。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompletionReceipt post(CompletionManifest manifest, List<CompletionManifestLine> lines) {
        return post(manifest, lines, List.of());
    }

    /**
     * 在库存接收事务内整体应用产品清单及实际用料，良品和报废数量分别来自明确身份集合。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @param allocations 完整的实际组件用料分配集合。
     * @return 首次已提交的完整入库或无良品交付回执。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompletionReceipt post(
            CompletionManifest manifest,
            List<CompletionManifestLine> lines,
            List<CompletionMaterialAllocation> allocations) {
        Result result = postManifest(manifest, lines, allocations);
        return new CompletionReceipt(
                result.receiptId(),
                result.postingGroupId(),
                manifest.manifestId(),
                manifest.revision(),
                result.contentHash());
    }

    /**
     * 沿用一次最终生产入库策略，先核验完整清单再整体写入逐件库存。
     *
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @return 生产入库单、完整过账组、清单摘要及是否重放的结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result postManifest(CompletionManifest summary, List<CompletionManifestLine> lines) {
        return postManifest(summary, lines, List.of());
    }

    /**
     * 沿用一次最终生产入库策略，先核验完整清单再整体写入逐件库存。
     *
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @param allocations 完整的实际组件用料分配集合。
     * @return 生产入库单、完整过账组、清单摘要及是否重放的结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result postManifest(
            CompletionManifest summary,
            List<CompletionManifestLine> lines,
            List<CompletionMaterialAllocation> allocations) {
        store.requireWrite();
        need(summary != null && store.tenant().equals(summary.tenantId()), "最终清单租户与ERP认证上下文不符");
        validateManifest(summary, lines);
        if (summary.goodQty().signum() == 0) {
            var result = noGoodOutput.post(summary, lines, allocations);
            return new Result(null, null, summary.contentHash(), result.replay());
        }
        var wo = source.lockWorkOrder(summary.erpWoId());
        need(wo.getMaterialId().equals(summary.materialId()), "MES最终清单物料不属于原ERP工单");
        var old = source.receipts(wo);
        if (!old.isEmpty() && "EXECUTED".equals(old.get(0).getStatus())) {
            need(old.size() == 1, "原最终入库结果不完整，禁止另造收货");
            if (actualConsumption != null) {
                actualConsumption.verifyReplay(summary, allocations);
            }
            Long group = replayFinalReceipt(summary, lines, wo, old.get(0));
            return new Result(old.get(0).getRecptId(), group, summary.contentHash(), true);
        }
        var policy = store.policy(wo.getMaterialId());
        if (policy.stage() == TrackingStage.STRICT
                && policy.batchManaged()
                && !policy.serialManaged()) {
            WmBatchProductionReceiptProofService.frozenBatches(summary, lines);
        }
        need(
                old.isEmpty() || old.size() == 1 && "DRAFT".equals(old.get(0).getStatus()),
                "原最终入库不是明确无效果草稿");
        source.requireNoEffects(wo);
        need(
                completionMapper.countExistingBackflushIssues(store.tenant(), wo.getWoId()) == 0,
                "已有历史倒冲而无最终收货凭据，请先对账");
        BigDecimal reported =
                completionMapper.selectApprovedReportedQty(store.tenant(), wo.getWoId());
        need(
                reported != null
                        && wo.getCompletedQty() != null
                        && reported.compareTo(wo.getCompletedQty()) == 0,
                "历史完成量与原审核报工不一致，不能借清单补造来源");
        workOrders.completeFromManifest(summary);
        if (actualConsumption != null) {
            Set<Long> actual = actualConsumption.consume(summary, allocations);
            backflush.backflushFromManifest(wo.getWoId(), summary.goodQty(), actual);
        } else {
            backflush.backflushOnCompletion(wo.getWoId(), summary.goodQty());
        }
        if (noGoodOutput != null) {
            noGoodOutput.postScrapOutputs(summary, lines);
        }
        Long authorizedBatch =
                lines.stream()
                        .filter(l -> "GOOD".equals(l.result()) && l.batchId() != null)
                        .map(CompletionManifestLine::batchId)
                        .findFirst()
                        .orElse(null);
        PutawayLocation target =
                policy.stage() == TrackingStage.STRICT
                                && policy.batchManaged()
                                && authorizedBatch != null
                        ? store.productionLotTarget(
                                wo.getWoId(), summary.mesWoId(), authorizedBatch)
                        : putaway.resolvePutaway(summary.materialId(), summary.goodQty());
        need(
                target != null && target.getWarehouseId() != null && target.getLocationId() != null,
                "原上架策略未返回有效仓储位置");
        Long receipt =
                postFinalCompletion(
                        summary,
                        lines,
                        target.getWarehouseId(),
                        target.getLocationId(),
                        policy.version());
        Long group =
                policy.stage() == TrackingStage.STRICT && policy.serialManaged()
                        ? proofs.verifyPosted("PRD_WORK_ORDER", wo.getWoId(), null, "PRODUCTION_IN")
                        : (policy.stage() == TrackingStage.STRICT && policy.batchManaged()
                                ? batchProof.record(summary, lines, wo, receipt, policy.version())
                                : store.recordLegacyManifest(summary, receipt, policy.version()));
        return new Result(receipt, group, summary.contentHash(), false);
    }

    /**
     * 按原生产凭据、仓储目标和冻结策略完成最终逐件入库。
     *
     * @param manifest 当前修订的冻结完工清单。
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @param warehouseId 业务所属仓库标识。
     * @param locationId 实际作业储位标识。
     * @param policyVersion 本次业务冻结的追踪策略版本。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long postFinalCompletion(
            CompletionManifest manifest,
            List<CompletionManifestLine> lines,
            Long warehouseId,
            Long locationId,
            long policyVersion) {
        store.requireWrite();
        need(manifest != null && store.tenant().equals(manifest.tenantId()), "最终清单租户与ERP认证上下文不符");
        validateManifest(manifest, lines);
        need(manifest.goodQty().signum() > 0, "零良品清单必须走无收货完成入口");
        var wo = source.lockWorkOrder(manifest.erpWoId());
        need(
                wo.getMaterialId().equals(manifest.materialId())
                        && source.readyQuantity(wo).compareTo(manifest.goodQty()) == 0,
                "清单良品量不属于原工单本次真实待入库数量");
        var old = source.receipts(wo);
        if (!old.isEmpty() && "EXECUTED".equals(old.get(0).getStatus())) {
            need(old.size() == 1, "同工单只能有一张最终生产入库单");
            replayFinalReceipt(manifest, lines, wo, old.get(0));
            return old.get(0).getRecptId();
        }
        var policy = store.policy(wo.getMaterialId());
        need(policy.version() == policyVersion, "清单物料策略版本已改变");
        if (policy.stage() != TrackingStage.STRICT
                || !policy.serialManaged() && !policy.batchManaged()) {
            return receipts.createAndExecute(
                    wo.getWoId(),
                    wo.getWoNo(),
                    wo.getMaterialId(),
                    wo.getMaterialCode(),
                    wo.getMaterialName(),
                    wo.getMaterialSpec(),
                    manifest.goodQty(),
                    warehouseId,
                    null,
                    null,
                    locationId,
                    null,
                    null,
                    new Date());
        }
        if (policy.batchManaged() && !policy.serialManaged()) {
            WmBatchProductionReceiptProofService.frozenBatches(manifest, lines);
        }
        if (policy.serialManaged()) {
            need(
                    lines.stream()
                            .allMatch(
                                    l ->
                                            l.identityId() != null
                                                    && BigDecimal.ONE.compareTo(l.quantity()) == 0),
                    "STRICT单件清单必须具有完整正式SN");
        }
        WmProductionRecpt receipt;
        String command =
                "MES_FINAL/"
                        + manifest.manifestId()
                        + "/"
                        + manifest.revision()
                        + "/"
                        + manifest.contentHash();
        if (!old.isEmpty()) {
            need(old.size() == 1, "同工单只能有一张最终生产入库单");
            receipt = old.get(0);
            source.validateReady(wo, receipt);
        } else {
            var bo = new WmProductionRecptBo();
            bo.setWoId(wo.getWoId());
            bo.setMaterialId(wo.getMaterialId());
            bo.setQuantity(manifest.goodQty());
            bo.setWarehouseId(warehouseId);
            bo.setLocationId(locationId);
            bo.setRecptDate(new Date());
            bo.setStatus("DRAFT");
            need(receipts.insertByBo(bo) == 1, "无法创建唯一原来源入库草稿");
            receipt = source.lockReceipt(bo.getRecptId()).receipt();
        }
        need(
                warehouseId.equals(receipt.getWarehouseId())
                        && locationId.equals(receipt.getLocationId()),
                "入库目标与原单据承诺不符");
        if (receipt.getBatchId() == null) {
            Long supplied =
                    lines.stream()
                            .filter(l -> "GOOD".equals(l.result()) && l.batchId() != null)
                            .map(CompletionManifestLine::batchId)
                            .findFirst()
                            .orElse(null);
            if (policy.batchManaged()) {
                need(supplied != null, "批次追踪成品必须先取得真实ERP生产批次授权");
            }
            if (supplied != null) {
                receipt.setBatchId(supplied);
                receipt.setBatchCode(
                        store.authorizedProductionBatchCode(
                                wo.getWoId(), manifest.mesWoId(), supplied, warehouseId));
            } else {
                var batch =
                        batches.createBatchByMaterial(
                                wo.getMaterialId(),
                                "PRD_WORK_ORDER",
                                wo.getWoId(),
                                wo.getWoNo(),
                                warehouseId,
                                null);
                need(batch != null && batch.getBatchId() != null, "原工单批次来源缺失");
                receipt.setBatchId(batch.getBatchId());
                receipt.setBatchCode(batch.getBatchCode());
            }
            need(
                    receiptMapper.update(
                                    null,
                                    Wrappers.<WmProductionRecpt>lambdaUpdate()
                                            .eq(WmProductionRecpt::getRecptId, receipt.getRecptId())
                                            .eq(WmProductionRecpt::getTenantId, store.tenant())
                                            .eq(WmProductionRecpt::getStatus, "DRAFT")
                                            .set(
                                                    WmProductionRecpt::getBatchId,
                                                    receipt.getBatchId())
                                            .set(
                                                    WmProductionRecpt::getBatchCode,
                                                    receipt.getBatchCode()))
                            == 1,
                    "原收货批次冻结失败");
        }
        Long defaultBatch = receipt.getBatchId();
        if (policy.batchManaged()) {
            for (Long batch :
                    lines.stream()
                            .filter(l -> "GOOD".equals(l.result()))
                            .map(CompletionManifestLine::batchId)
                            .distinct()
                            .toList()) {
                need(batch != null, "成品批次冻结明细缺少真实ERP授权");
                if (!policy.serialManaged()) {
                    need(defaultBatch.equals(batch), "原唯一数量收货行已显式锁定成品批次");
                }
                store.authorizedProductionBatchCode(
                        wo.getWoId(), manifest.mesWoId(), batch, warehouseId);
            }
        }
        if (!policy.serialManaged()) {
            receipts.execute(receipt.getRecptId());
            return receipt.getRecptId();
        }
        var items =
                lines.stream()
                        .filter(l -> "GOOD".equals(l.result()))
                        .map(
                                l ->
                                        new SnItem(
                                                l.identityId(),
                                                null,
                                                locationId,
                                                null,
                                                l.batchId() == null ? defaultBatch : l.batchId()))
                        .toList();
        var request =
                WmSerialPostingRequest.in(
                        "PRD_WORK_ORDER",
                        wo.getWoId(),
                        null,
                        "PRODUCTION_IN",
                        wo.getMaterialId(),
                        manifest.goodQty(),
                        warehouseId,
                        locationId,
                        null,
                        null);
        inventory.freeze(request, command, manifest.revision(), policyVersion, items);
        receipts.execute(receipt.getRecptId());
        return receipt.getRecptId();
    }

    /**
     * 按原持久来源证明选择重放路径，保持原业务当时的追踪策略。
     */
    private Long replayFinalReceipt(
            CompletionManifest manifest,
            List<CompletionManifestLine> lines,
            com.nexus.erp.prd.domain.PrdWorkOrder wo,
            WmProductionRecpt receipt) {
        source.verifyExecuted(wo, manifest.goodQty(), receipt);
        if (batchProof != null && batchProof.exists(wo.getWoId())) {
            return batchProof.replay(manifest, lines, wo, receipt);
        }
        var request =
                WmSerialPostingRequest.in(
                        "PRD_WORK_ORDER",
                        wo.getWoId(),
                        null,
                        "PRODUCTION_IN",
                        wo.getMaterialId(),
                        manifest.goodQty(),
                        receipt.getWarehouseId(),
                        receipt.getLocationId(),
                        receipt.getBatchId(),
                        null);
        var original = store.group(request);
        if (original != null) {
            String command =
                    "MES_FINAL/"
                            + manifest.manifestId()
                            + "/"
                            + manifest.revision()
                            + "/"
                            + manifest.contentHash();
            need(
                    "POSTED".equals(original.status()) && command.equals(original.commandId()),
                    "原收货不属于同一冻结清单，禁止覆盖旧证明");
            return proofs.verifyPosted("PRD_WORK_ORDER", wo.getWoId(), null, "PRODUCTION_IN");
        }
        return store.recordLegacyManifest(manifest, receipt.getRecptId());
    }

    /**
     * 按稳定清单行序计算整份生产交付内容摘要。
     *
     * @param lines 按稳定行序读取的完整单件明细集合。
     * @return 按稳定顺序及原请求计算的完整内容摘要。
     */
    public static String manifestHash(List<CompletionManifestLine> lines) {
        return CompletionManifestIntegrity.hash(lines);
    }

    /**
     * 校验产品身份、合格报废集合及汇总数量，任何分页缺口均阻止整体入库。
     *
     * @param lines 按稳定行序读取的完整单件明细集合。
     */
    static void validateManifest(CompletionManifest m, List<CompletionManifestLine> lines) {
        need(
                m != null
                        && m.manifestId() != null
                        && m.revision() > 0
                        && m.erpWoId() != null
                        && m.mesWoId() != null
                        && m.materialId() != null
                        && Set.of("FROZEN", "APPLIED", "RECEIVED")
                                .contains(Objects.toString(m.status(), ""))
                        && lines != null
                        && lines.size() == m.lineCount()
                        && m.goodQty() != null
                        && m.goodQty().signum() >= 0
                        && m.scrapQty() != null
                        && m.scrapQty().signum() >= 0
                        && m.goodQty().add(m.scrapQty()).signum() > 0,
                "最终冻结清单不完整");
        long previous = 0;
        Set<Long> ids = new HashSet<>();
        BigDecimal good = BigDecimal.ZERO, scrap = BigDecimal.ZERO;
        for (var l : lines) {
            need(
                    l != null
                            && l.lineNo() > previous
                            && l.sourceVersion() > 0
                            && (l.identityId() == null || ids.add(l.identityId()))
                            && m.materialId().equals(l.materialId())
                            && l.quantity() != null
                            && l.quantity().signum() > 0
                            && (l.identityId() == null
                                    || BigDecimal.ONE.compareTo(l.quantity()) == 0)
                            && Set.of("GOOD", "SCRAP").contains(l.result()),
                    "最终清单行不完整、重复或未按冻结顺序读取");
            previous = l.lineNo();
            if ("GOOD".equals(l.result())) {
                good = good.add(l.quantity());
            } else {
                scrap = scrap.add(l.quantity());
            }
        }
        need(
                good.compareTo(m.goodQty()) == 0
                        && scrap.compareTo(m.scrapQty()) == 0
                        && manifestHash(lines).equals(m.contentHash()),
                "最终清单数量或冻结摘要不守恒");
    }
}

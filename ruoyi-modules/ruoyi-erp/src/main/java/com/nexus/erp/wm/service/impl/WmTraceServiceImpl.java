package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.vo.WmTraceNodeVo;
import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.IWmTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.List;
import java.util.Set;

/**
 * 追溯查询Service业务层处理
 *
 * @author nexus
 * @date 2026-06-02
 */
@DS("erp")
@Service
@RequiredArgsConstructor
@Slf4j
public class WmTraceServiceImpl implements IWmTraceService {

    /**
     * 物料库存流水数据访问映射器（Mapper）
     */
    private final WmMaterialStockTxMapper wmMaterialStockTxMapper;

    /**
     * 序列号数据访问映射器（Mapper）
     */
    private final WmSnMapper wmSnMapper;

    /**
     * 批次数据访问映射器（Mapper）
     */
    private final WmBatchMapper wmBatchMapper;

    /**
     * 最大递归深度，防止无限循环
     */
    private static final int MAX_DEPTH = 10;

    private static final int MAX_ROWS = 200;

    /**
     * 追溯处理（traceForward）
     *
     * @param materialId 物料 ID
     * @param batchCode 批次 编码
     * @param snCode 序列号 编码
     * @return 结果
     */
    @Override
    public WmTraceResultVo traceForward(Long materialId, String batchCode, String snCode) {
        WmSn sn = resolveRequestedSn(materialId, batchCode, snCode);
        String resolvedBatch = sn == null ? batchCode : sn.getBatchCode();
        WmTraceResultVo result = new WmTraceResultVo();
        result.setDirection("FORWARD");
        result.setTraceKey(buildTraceKey(materialId, resolvedBatch, snCode));

        List<WmTraceNodeVo> flatNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> notices = new LinkedHashSet<>();
        WmTraceNodeVo root =
                buildForwardTree(
                        materialId,
                        resolvedBatch,
                        sn == null ? null : sn.getSnId(),
                        snCode,
                        0,
                        visited,
                        flatNodes,
                        notices);
        if (sn != null && root != null) {
            if (StringUtils.isNotBlank(sn.getMaterialCode())) {
                root.setMaterialCode(sn.getMaterialCode());
            }
            if (StringUtils.isNotBlank(sn.getMaterialName())) {
                root.setMaterialName(sn.getMaterialName());
            }
            if (StringUtils.isNotBlank(sn.getWarehouseName())) {
                root.setWarehouseName(sn.getWarehouseName());
            }
        }
        if (!notices.isEmpty()) {
            result.setTraceKey(result.getTraceKey() + "/" + String.join("/", notices));
        }
        result.setIdentityId(sn == null ? null : sn.getSnId());
        result.setTraceScope(
                sn != null
                        ? "IDENTITY"
                        : StringUtils.isNotBlank(resolvedBatch) ? "BATCH" : "MATERIAL");
        var truncated = notices.stream().filter(n -> n.startsWith("TRUNCATED:")).toList();
        result.setTruncated(!truncated.isEmpty());
        result.setTruncationReason(truncated.isEmpty() ? null : String.join(",", truncated));
        result.setEvidenceGaps(notices.stream().filter(n -> !n.startsWith("TRUNCATED:")).toList());
        result.setNodes(flatNodes);
        result.setTree(root);
        return result;
    }

    /**
     * 追溯处理（traceBackward）
     *
     * @param materialId 物料 ID
     * @param batchCode 批次 编码
     * @param snCode 序列号 编码
     * @return 结果
     */
    @Override
    public WmTraceResultVo traceBackward(Long materialId, String batchCode, String snCode) {
        WmSn sn = resolveRequestedSn(materialId, batchCode, snCode);
        String resolvedBatch = sn == null ? batchCode : sn.getBatchCode();
        WmTraceResultVo result = new WmTraceResultVo();
        result.setDirection("BACKWARD");
        result.setTraceKey(buildTraceKey(materialId, resolvedBatch, snCode));

        List<WmTraceNodeVo> flatNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> notices = new LinkedHashSet<>();
        WmTraceNodeVo root =
                buildBackwardTree(
                        materialId,
                        resolvedBatch,
                        sn == null ? null : sn.getSnId(),
                        snCode,
                        0,
                        visited,
                        flatNodes,
                        notices);
        if (sn != null && root != null) {
            if (StringUtils.isNotBlank(sn.getMaterialCode())) {
                root.setMaterialCode(sn.getMaterialCode());
            }
            if (StringUtils.isNotBlank(sn.getMaterialName())) {
                root.setMaterialName(sn.getMaterialName());
            }
            if (StringUtils.isNotBlank(sn.getWarehouseName())) {
                root.setWarehouseName(sn.getWarehouseName());
            }
        }
        if (!notices.isEmpty()) {
            result.setTraceKey(result.getTraceKey() + "/" + String.join("/", notices));
        }
        result.setIdentityId(sn == null ? null : sn.getSnId());
        result.setTraceScope(
                sn != null
                        ? "IDENTITY"
                        : StringUtils.isNotBlank(resolvedBatch) ? "BATCH" : "MATERIAL");
        var truncated = notices.stream().filter(n -> n.startsWith("TRUNCATED:")).toList();
        result.setTruncated(!truncated.isEmpty());
        result.setTruncationReason(truncated.isEmpty() ? null : String.join(",", truncated));
        result.setEvidenceGaps(notices.stream().filter(n -> !n.startsWith("TRUNCATED:")).toList());
        result.setNodes(flatNodes);
        result.setTree(root);
        return result;
    }

    // ==================== 正向追溯 ====================

    /**
     * 递归构建正向追溯树（原材料 → 成品）
     * <p>
     * 逻辑：找到该物料的出库事务（txFlag=-1），然后找到该出库事务对应的单据（sourceDocType/sourceDocId），
     * 再从该单据的入库事务（txFlag=1）中找到产出的成品/半成品，递归追踪。
     */
    private WmTraceNodeVo buildForwardTree(
            Long materialId,
            String batchCode,
            Long snId,
            String snCode,
            int depth,
            Set<String> visited,
            List<WmTraceNodeVo> flatNodes,
            Set<String> notices) {
        if (depth >= MAX_DEPTH) {
            notices.add("TRUNCATED:DEPTH_" + MAX_DEPTH);
            log.warn("正向追溯超过最大深度 {}，停止递归", MAX_DEPTH);
            return null;
        }

        // 查找该物料的出库事务
        LambdaQueryWrapper<WmMaterialStockTx> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStockTx::getMaterialId, materialId);
        lqw.eq(WmMaterialStockTx::getTxFlag, -1); // 出库
        // 精确 SN 的历史不受当前批次归属限制；批次范围只用于非 SN 查询。
        if (snId == null && StringUtils.isBlank(snCode) && StringUtils.isNotBlank(batchCode)) {
            lqw.eq(WmMaterialStockTx::getBatchCode, batchCode);
        }
        applySnIdentity(lqw, snId, snCode);

        List<WmMaterialStockTx> outTxList = selectLimited(lqw, notices);

        // 构建根节点
        WmTraceNodeVo root = buildRootNode(materialId, batchCode, snId, snCode);
        String rootId = root.getNodeId();
        visited.add(rootId);
        flatNodes.add(root);

        List<WmTraceNodeVo> children = new ArrayList<>();

        for (WmMaterialStockTx outTx : outTxList) {
            String childNodeId =
                    outTx.getMaterialId()
                            + "_"
                            + outTx.getSourceDocType()
                            + "_"
                            + outTx.getSourceDocId();
            if (visited.contains(childNodeId)) {
                continue;
            }
            visited.add(childNodeId);

            // 找到同一单据的入库事务（产出成品/半成品）
            LambdaQueryWrapper<WmMaterialStockTx> inLqw = Wrappers.lambdaQuery();
            inLqw.eq(WmMaterialStockTx::getSourceDocType, outTx.getSourceDocType());
            inLqw.eq(WmMaterialStockTx::getSourceDocId, outTx.getSourceDocId());
            inLqw.eq(WmMaterialStockTx::getTxFlag, 1); // 入库
            // 排除自身物料（关注产出物）
            inLqw.ne(WmMaterialStockTx::getMaterialId, materialId);

            List<WmMaterialStockTx> inTxList = selectLimited(inLqw, notices);

            for (WmMaterialStockTx inTx : inTxList) {
                WmTraceNodeVo child = new WmTraceNodeVo();
                child.setNodeId(
                        inTx.getMaterialId()
                                + "_"
                                + inTx.getSourceDocType()
                                + "_"
                                + inTx.getSourceDocId());
                child.setParentNodeId(rootId);
                child.setMaterialId(inTx.getMaterialId());
                child.setMaterialCode(inTx.getMaterialCode());
                child.setMaterialName(inTx.getMaterialName());
                child.setBatchCode(inTx.getBatchCode());
                child.setSnCode(inTx.getSnCode());
                child.setQuantity(inTx.getTxQuantity());
                child.setDocType(inTx.getSourceDocType());
                child.setDocId(inTx.getSourceDocId());
                child.setDocNo(inTx.getSourceDocCode());
                child.setWarehouseName(inTx.getWarehouseName());
                child.setTransactionTime(inTx.getTxDate());

                flatNodes.add(child);

                if ((snId != null || StringUtils.isNotBlank(snCode))
                        && inTx.getSnId() == null
                        && StringUtils.isBlank(inTx.getSnCode())) {
                    notices.add(
                            "RELATED_SCOPE:"
                                    + (StringUtils.isNotBlank(inTx.getBatchCode())
                                            ? "BATCH"
                                            : "MATERIAL"));
                }

                // 递归追踪该成品的后续流转
                WmTraceNodeVo grandChild =
                        buildForwardTree(
                                inTx.getMaterialId(),
                                inTx.getBatchCode(),
                                inTx.getSnId(),
                                inTx.getSnCode(),
                                depth + 1,
                                visited,
                                flatNodes,
                                notices);
                if (grandChild != null
                        && grandChild.getChildren() != null
                        && !grandChild.getChildren().isEmpty()) {
                    child.setChildren(grandChild.getChildren());
                }

                children.add(child);
            }
        }

        root.setChildren(children.isEmpty() ? null : children);
        return root;
    }

    // ==================== 反向追溯 ====================

    /**
     * 递归构建反向追溯树（成品 → 原材料）
     * <p>
     * 逻辑：找到该物料的入库事务（txFlag=1），然后找到该入库事务对应的单据（sourceDocType/sourceDocId），
     * 再从该单据的出库事务（txFlag=-1）中找到消耗的原材料，递归追踪。
     */
    private WmTraceNodeVo buildBackwardTree(
            Long materialId,
            String batchCode,
            Long snId,
            String snCode,
            int depth,
            Set<String> visited,
            List<WmTraceNodeVo> flatNodes,
            Set<String> notices) {
        if (depth >= MAX_DEPTH) {
            notices.add("TRUNCATED:DEPTH_" + MAX_DEPTH);
            log.warn("反向追溯超过最大深度 {}，停止递归", MAX_DEPTH);
            return null;
        }

        // 查找该物料的入库事务
        LambdaQueryWrapper<WmMaterialStockTx> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStockTx::getMaterialId, materialId);
        lqw.eq(WmMaterialStockTx::getTxFlag, 1); // 入库
        // 精确 SN 的历史不受当前批次归属限制；批次范围只用于非 SN 查询。
        if (snId == null && StringUtils.isBlank(snCode) && StringUtils.isNotBlank(batchCode)) {
            lqw.eq(WmMaterialStockTx::getBatchCode, batchCode);
        }
        applySnIdentity(lqw, snId, snCode);

        List<WmMaterialStockTx> inTxList = selectLimited(lqw, notices);

        // 构建根节点
        WmTraceNodeVo root = buildRootNode(materialId, batchCode, snId, snCode);
        String rootId = root.getNodeId();
        visited.add(rootId);
        flatNodes.add(root);

        List<WmTraceNodeVo> children = new ArrayList<>();

        for (WmMaterialStockTx inTx : inTxList) {
            String childNodeId =
                    inTx.getMaterialId()
                            + "_"
                            + inTx.getSourceDocType()
                            + "_"
                            + inTx.getSourceDocId();
            if (visited.contains(childNodeId)) {
                continue;
            }
            visited.add(childNodeId);

            // 找到同一单据的出库事务（消耗的原材料）
            LambdaQueryWrapper<WmMaterialStockTx> outLqw = Wrappers.lambdaQuery();
            outLqw.eq(WmMaterialStockTx::getSourceDocType, inTx.getSourceDocType());
            outLqw.eq(WmMaterialStockTx::getSourceDocId, inTx.getSourceDocId());
            outLqw.eq(WmMaterialStockTx::getTxFlag, -1); // 出库
            // 排除自身物料（关注投入物）
            outLqw.ne(WmMaterialStockTx::getMaterialId, materialId);

            List<WmMaterialStockTx> outTxList = selectLimited(outLqw, notices);

            for (WmMaterialStockTx outTx : outTxList) {
                WmTraceNodeVo child = new WmTraceNodeVo();
                child.setNodeId(
                        outTx.getMaterialId()
                                + "_"
                                + outTx.getSourceDocType()
                                + "_"
                                + outTx.getSourceDocId());
                child.setParentNodeId(rootId);
                child.setMaterialId(outTx.getMaterialId());
                child.setMaterialCode(outTx.getMaterialCode());
                child.setMaterialName(outTx.getMaterialName());
                child.setBatchCode(outTx.getBatchCode());
                child.setSnCode(outTx.getSnCode());
                child.setQuantity(outTx.getTxQuantity());
                child.setDocType(outTx.getSourceDocType());
                child.setDocId(outTx.getSourceDocId());
                child.setDocNo(outTx.getSourceDocCode());
                child.setWarehouseName(outTx.getWarehouseName());
                child.setTransactionTime(outTx.getTxDate());

                flatNodes.add(child);

                if ((snId != null || StringUtils.isNotBlank(snCode))
                        && outTx.getSnId() == null
                        && StringUtils.isBlank(outTx.getSnCode())) {
                    notices.add(
                            "RELATED_SCOPE:"
                                    + (StringUtils.isNotBlank(outTx.getBatchCode())
                                            ? "BATCH"
                                            : "MATERIAL"));
                }

                // 递归追踪该原材料的来源
                WmTraceNodeVo grandChild =
                        buildBackwardTree(
                                outTx.getMaterialId(),
                                outTx.getBatchCode(),
                                outTx.getSnId(),
                                outTx.getSnCode(),
                                depth + 1,
                                visited,
                                flatNodes,
                                notices);
                if (grandChild != null
                        && grandChild.getChildren() != null
                        && !grandChild.getChildren().isEmpty()) {
                    child.setChildren(grandChild.getChildren());
                }

                children.add(child);
            }
        }

        root.setChildren(children.isEmpty() ? null : children);
        return root;
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建根节点
     */
    private WmTraceNodeVo buildRootNode(
            Long materialId, String batchCode, Long snId, String snCode) {
        WmTraceNodeVo root = new WmTraceNodeVo();
        root.setNodeId(
                buildTraceKey(materialId, batchCode, snCode)
                        + (snId == null ? "_ROOT" : "/ID:" + snId));
        root.setParentNodeId(null);
        root.setMaterialId(materialId);
        root.setBatchCode(batchCode);
        root.setSnCode(snCode);

        // 从批次表补充信息
        if (snId == null && StringUtils.isBlank(snCode) && StringUtils.isNotBlank(batchCode)) {
            LambdaQueryWrapper<WmBatch> batchLqw = Wrappers.lambdaQuery();
            batchLqw.eq(WmBatch::getBatchCode, batchCode);
            batchLqw.eq(WmBatch::getMaterialId, materialId);
            WmBatch batch = wmBatchMapper.selectOne(batchLqw);
            if (batch != null) {
                root.setMaterialCode(batch.getMaterialCode());
                root.setMaterialName(batch.getMaterialName());
                root.setWarehouseName(batch.getWarehouseName());
            }
        }

        // 从库存事务表补充信息
        if (StringUtils.isBlank(root.getMaterialCode())) {
            LambdaQueryWrapper<WmMaterialStockTx> txLqw = Wrappers.lambdaQuery();
            txLqw.eq(WmMaterialStockTx::getMaterialId, materialId);
            txLqw.eq(
                    snId == null
                            && StringUtils.isBlank(snCode)
                            && StringUtils.isNotBlank(batchCode),
                    WmMaterialStockTx::getBatchCode,
                    batchCode);
            applySnIdentity(txLqw, snId, snCode);
            txLqw.last("LIMIT 1");
            WmMaterialStockTx tx = wmMaterialStockTxMapper.selectOne(txLqw);
            if (tx != null) {
                root.setMaterialCode(tx.getMaterialCode());
                root.setMaterialName(tx.getMaterialName());
            }
        }

        return root;
    }

    /**
     * SN 不存在或请求范围冲突时明确失败，不能退化为批次/物料级查询。
     */
    private WmSn resolveRequestedSn(Long materialId, String batchCode, String snCode) {
        if (StringUtils.isBlank(snCode)) {
            return null;
        }
        WmSn sn = wmSnMapper.selectOne(Wrappers.<WmSn>lambdaQuery().eq(WmSn::getSnCode, snCode));
        if (sn == null || sn.getSnId() == null) {
            throw new ServiceException("序列号不存在");
        }
        if (!Objects.equals(materialId, sn.getMaterialId())
                || (StringUtils.isNotBlank(batchCode)
                        && !Objects.equals(batchCode, sn.getBatchCode()))) {
            throw new ServiceException("序列号与指定物料或批次不匹配");
        }
        return sn;
    }

    /**
     * 稳定 SN ID 优先；只对未记录 ID 的历史流水使用精确编码，不匹配其他非空 ID。
     */
    private void applySnIdentity(
            LambdaQueryWrapper<WmMaterialStockTx> query, Long snId, String snCode) {
        if (snId != null) {
            query.and(
                    identity -> {
                        identity.eq(WmMaterialStockTx::getSnId, snId);
                        if (StringUtils.isNotBlank(snCode)) {
                            identity.or(
                                    legacy ->
                                            legacy.isNull(WmMaterialStockTx::getSnId)
                                                    .eq(WmMaterialStockTx::getSnCode, snCode));
                        }
                    });
        } else if (StringUtils.isNotBlank(snCode)) {
            query.eq(WmMaterialStockTx::getSnCode, snCode);
        }
    }

    /**
     * 多读一条区分恰好 200 条与真实截断，提示写入现有 traceKey 字段。
     */
    private List<WmMaterialStockTx> selectLimited(
            LambdaQueryWrapper<WmMaterialStockTx> query, Set<String> notices) {
        query.orderByAsc(WmMaterialStockTx::getTxDate).orderByAsc(WmMaterialStockTx::getTxId);
        query.last("LIMIT " + (MAX_ROWS + 1));
        List<WmMaterialStockTx> transactions = wmMaterialStockTxMapper.selectList(query);
        if (transactions.size() > MAX_ROWS) {
            notices.add("TRUNCATED:ROWS_" + MAX_ROWS);
            return transactions.subList(0, MAX_ROWS);
        }
        return transactions;
    }

    /**
     * 构建追溯关键字
     */
    private String buildTraceKey(Long materialId, String batchCode, String snCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("SCOPE:")
                .append(
                        StringUtils.isNotBlank(snCode)
                                ? "EXACT_SN"
                                : StringUtils.isNotBlank(batchCode) ? "BATCH" : "MATERIAL");
        sb.append("/").append(materialId);
        if (StringUtils.isNotBlank(batchCode)) {
            sb.append("/B:").append(batchCode);
        }
        if (StringUtils.isNotBlank(snCode)) {
            sb.append("/SN:").append(snCode);
        }
        return sb.toString();
    }
}

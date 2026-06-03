package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
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

    private final WmMaterialStockTxMapper wmMaterialStockTxMapper;
    private final WmSnMapper wmSnMapper;
    private final WmBatchMapper wmBatchMapper;

    /** 最大递归深度，防止无限循环 */
    private static final int MAX_DEPTH = 10;

    @Override
    public WmTraceResultVo traceForward(Long materialId, String batchCode, String snCode) {
        WmTraceResultVo result = new WmTraceResultVo();
        result.setDirection("FORWARD");
        result.setTraceKey(buildTraceKey(materialId, batchCode, snCode));

        List<WmTraceNodeVo> flatNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        WmTraceNodeVo root = buildForwardTree(materialId, batchCode, snCode, 0, visited, flatNodes);

        result.setNodes(flatNodes);
        result.setTree(root);
        return result;
    }

    @Override
    public WmTraceResultVo traceBackward(Long materialId, String batchCode, String snCode) {
        WmTraceResultVo result = new WmTraceResultVo();
        result.setDirection("BACKWARD");
        result.setTraceKey(buildTraceKey(materialId, batchCode, snCode));

        List<WmTraceNodeVo> flatNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        WmTraceNodeVo root = buildBackwardTree(materialId, batchCode, snCode, 0, visited, flatNodes);

        result.setNodes(flatNodes);
        result.setTree(root);
        return result;
    }

    // ==================== 正向追溯 ====================

    /**
     * 递归构建正向追溯树（原材料 → 成品）
     * <p>
     * 逻辑：找到该物料的出库事务（txFlag=2），然后找到该出库事务对应的单据（sourceDocType/sourceDocId），
     * 再从该单据的入库事务（txFlag=1）中找到产出的成品/半成品，递归追踪。
     */
    private WmTraceNodeVo buildForwardTree(Long materialId, String batchCode, String snCode,
                                            int depth, Set<String> visited, List<WmTraceNodeVo> flatNodes) {
        if (depth >= MAX_DEPTH) {
            log.warn("正向追溯超过最大深度 {}，停止递归", MAX_DEPTH);
            return null;
        }

        // 查找该物料的出库事务
        LambdaQueryWrapper<WmMaterialStockTx> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStockTx::getMaterialId, materialId);
        lqw.eq(WmMaterialStockTx::getTxFlag, 2); // 出库
        if (StringUtils.isNotBlank(batchCode)) {
            lqw.eq(WmMaterialStockTx::getBatchCode, batchCode);
        }
        // SN过滤：当SN不为空时，通过SN表找到对应批次再过滤
        if (StringUtils.isNotBlank(snCode)) {
            LambdaQueryWrapper<WmSn> snLqw = Wrappers.lambdaQuery();
            snLqw.eq(WmSn::getSnCode, snCode);
            WmSn sn = wmSnMapper.selectOne(snLqw);
            if (sn != null) {
                if (sn.getBatchCode() != null) {
                    lqw.eq(WmMaterialStockTx::getBatchCode, sn.getBatchCode());
                }
                lqw.eq(WmMaterialStockTx::getMaterialId, sn.getMaterialId());
            }
        }
        lqw.orderByAsc(WmMaterialStockTx::getTxDate);
        lqw.last("LIMIT 200");

        List<WmMaterialStockTx> outTxList = wmMaterialStockTxMapper.selectList(lqw);

        // 构建根节点
        WmTraceNodeVo root = buildRootNode(materialId, batchCode, snCode);
        String rootId = root.getNodeId();
        visited.add(rootId);
        flatNodes.add(root);

        List<WmTraceNodeVo> children = new ArrayList<>();

        for (WmMaterialStockTx outTx : outTxList) {
            String childNodeId = outTx.getMaterialId() + "_" + outTx.getSourceDocType() + "_" + outTx.getSourceDocId();
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

            List<WmMaterialStockTx> inTxList = wmMaterialStockTxMapper.selectList(inLqw);

            for (WmMaterialStockTx inTx : inTxList) {
                WmTraceNodeVo child = new WmTraceNodeVo();
                child.setNodeId(inTx.getMaterialId() + "_" + inTx.getSourceDocType() + "_" + inTx.getSourceDocId());
                child.setParentNodeId(rootId);
                child.setMaterialId(inTx.getMaterialId());
                child.setMaterialCode(inTx.getMaterialCode());
                child.setMaterialName(inTx.getMaterialName());
                child.setBatchCode(inTx.getBatchCode());
                child.setQuantity(inTx.getTxQuantity());
                child.setDocType(inTx.getSourceDocType());
                child.setDocId(inTx.getSourceDocId());
                child.setDocNo(inTx.getSourceDocCode());
                child.setWarehouseName(inTx.getWarehouseName());
                child.setTransactionTime(inTx.getTxDate());

                flatNodes.add(child);

                // 递归追踪该成品的后续流转
                WmTraceNodeVo grandChild = buildForwardTree(inTx.getMaterialId(), inTx.getBatchCode(), null,
                    depth + 1, visited, flatNodes);
                if (grandChild != null && grandChild.getChildren() != null && !grandChild.getChildren().isEmpty()) {
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
     * 再从该单据的出库事务（txFlag=2）中找到消耗的原材料，递归追踪。
     */
    private WmTraceNodeVo buildBackwardTree(Long materialId, String batchCode, String snCode,
                                             int depth, Set<String> visited, List<WmTraceNodeVo> flatNodes) {
        if (depth >= MAX_DEPTH) {
            log.warn("反向追溯超过最大深度 {}，停止递归", MAX_DEPTH);
            return null;
        }

        // 查找该物料的入库事务
        LambdaQueryWrapper<WmMaterialStockTx> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStockTx::getMaterialId, materialId);
        lqw.eq(WmMaterialStockTx::getTxFlag, 1); // 入库
        if (StringUtils.isNotBlank(batchCode)) {
            lqw.eq(WmMaterialStockTx::getBatchCode, batchCode);
        }
        // SN过滤：当SN不为空时，通过SN表找到对应批次再过滤
        if (StringUtils.isNotBlank(snCode)) {
            LambdaQueryWrapper<WmSn> snLqw = Wrappers.lambdaQuery();
            snLqw.eq(WmSn::getSnCode, snCode);
            WmSn sn = wmSnMapper.selectOne(snLqw);
            if (sn != null) {
                if (sn.getBatchCode() != null) {
                    lqw.eq(WmMaterialStockTx::getBatchCode, sn.getBatchCode());
                }
                lqw.eq(WmMaterialStockTx::getMaterialId, sn.getMaterialId());
            }
        }
        lqw.orderByAsc(WmMaterialStockTx::getTxDate);
        lqw.last("LIMIT 200");

        List<WmMaterialStockTx> inTxList = wmMaterialStockTxMapper.selectList(lqw);

        // 构建根节点
        WmTraceNodeVo root = buildRootNode(materialId, batchCode, snCode);
        String rootId = root.getNodeId();
        visited.add(rootId);
        flatNodes.add(root);

        List<WmTraceNodeVo> children = new ArrayList<>();

        for (WmMaterialStockTx inTx : inTxList) {
            String childNodeId = inTx.getMaterialId() + "_" + inTx.getSourceDocType() + "_" + inTx.getSourceDocId();
            if (visited.contains(childNodeId)) {
                continue;
            }
            visited.add(childNodeId);

            // 找到同一单据的出库事务（消耗的原材料）
            LambdaQueryWrapper<WmMaterialStockTx> outLqw = Wrappers.lambdaQuery();
            outLqw.eq(WmMaterialStockTx::getSourceDocType, inTx.getSourceDocType());
            outLqw.eq(WmMaterialStockTx::getSourceDocId, inTx.getSourceDocId());
            outLqw.eq(WmMaterialStockTx::getTxFlag, 2); // 出库
            // 排除自身物料（关注投入物）
            outLqw.ne(WmMaterialStockTx::getMaterialId, materialId);

            List<WmMaterialStockTx> outTxList = wmMaterialStockTxMapper.selectList(outLqw);

            for (WmMaterialStockTx outTx : outTxList) {
                WmTraceNodeVo child = new WmTraceNodeVo();
                child.setNodeId(outTx.getMaterialId() + "_" + outTx.getSourceDocType() + "_" + outTx.getSourceDocId());
                child.setParentNodeId(rootId);
                child.setMaterialId(outTx.getMaterialId());
                child.setMaterialCode(outTx.getMaterialCode());
                child.setMaterialName(outTx.getMaterialName());
                child.setBatchCode(outTx.getBatchCode());
                child.setQuantity(outTx.getTxQuantity());
                child.setDocType(outTx.getSourceDocType());
                child.setDocId(outTx.getSourceDocId());
                child.setDocNo(outTx.getSourceDocCode());
                child.setWarehouseName(outTx.getWarehouseName());
                child.setTransactionTime(outTx.getTxDate());

                flatNodes.add(child);

                // 递归追踪该原材料的来源
                WmTraceNodeVo grandChild = buildBackwardTree(outTx.getMaterialId(), outTx.getBatchCode(), null,
                    depth + 1, visited, flatNodes);
                if (grandChild != null && grandChild.getChildren() != null && !grandChild.getChildren().isEmpty()) {
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
    private WmTraceNodeVo buildRootNode(Long materialId, String batchCode, String snCode) {
        WmTraceNodeVo root = new WmTraceNodeVo();
        root.setNodeId(materialId + "_ROOT");
        root.setParentNodeId(null);
        root.setMaterialId(materialId);
        root.setBatchCode(batchCode);
        root.setSnCode(snCode);

        // 从SN表补充信息
        if (StringUtils.isNotBlank(snCode)) {
            LambdaQueryWrapper<WmSn> snLqw = Wrappers.lambdaQuery();
            snLqw.eq(WmSn::getSnCode, snCode);
            WmSn sn = wmSnMapper.selectOne(snLqw);
            if (sn != null) {
                root.setMaterialCode(sn.getMaterialCode());
                root.setMaterialName(sn.getMaterialName());
                root.setBatchCode(sn.getBatchCode());
                root.setWarehouseName(sn.getWarehouseName());
            }
        }

        // 从批次表补充信息
        if (StringUtils.isNotBlank(batchCode) && StringUtils.isBlank(root.getMaterialCode())) {
            LambdaQueryWrapper<WmBatch> batchLqw = Wrappers.lambdaQuery();
            batchLqw.eq(WmBatch::getBatchCode, batchCode);
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
     * 构建追溯关键字
     */
    private String buildTraceKey(Long materialId, String batchCode, String snCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(materialId);
        if (StringUtils.isNotBlank(batchCode)) {
            sb.append("/B:").append(batchCode);
        }
        if (StringUtils.isNotBlank(snCode)) {
            sb.append("/SN:").append(snCode);
        }
        return sb.toString();
    }
}

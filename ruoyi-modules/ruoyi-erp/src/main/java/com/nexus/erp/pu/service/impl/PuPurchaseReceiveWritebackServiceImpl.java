package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.enums.PurchaseOrderLineReceiveStatus;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import com.nexus.erp.pu.service.IPuPurchaseReceiveWritebackService;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * 采购收货回写Service实现类，负责将收货数量回写到采购订单行并更新采购单状态
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuPurchaseReceiveWritebackServiceImpl implements IPuPurchaseReceiveWritebackService {

    private final PuCertifiedPurchaseReceiptBatchService certifiedReceipts;
    private final LegacyWriteback legacy;

    /**
     * 非事务调度器：认证来源优先进入ERP独占可重复读事务执行器。
     * 订单头标记缺失、已补偿命令、Redis 标记或提交结果未知的异常，都绝不能作为回退的理由。
     * 只有成功提交的显式旧路径路由，才会进入其独立 Bean。
     */
    @Override
    public void writeBackReceivedQty(Long receiveId,Long purId) {
        var result=certifiedReceipts.consumeReceipt(TenantHelper.getTenantId(),receiveId,purId);
        if(result==null||!Objects.equals(receiveId,result.receiveId())||result.orderId()==null
            ||(purId!=null&&!purId.equals(result.orderId()))) {
            throw new ServiceException("采购收货来源分支未确认，拒绝按历史回写");
        }
        if(!result.certified()) legacy.writeBackReceivedQty(receiveId,result.orderId());
    }

    /** 独立 Spring 代理：将 @Transactional 移至此处，既保留历史写入路径，又避免
     * 开启会阻碍认证来源的ERP独占可重复读事务执行器运行的环境事务。 */
    @Service
    @DS("erp")
    @RequiredArgsConstructor
    public static class LegacyWriteback {
        /** 旧路径只允许无认证来源的历史回写。 */
        private final PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
        /** 采购订单Mapper */
        private final PuPurchaseOrderMapper purchaseOrderMapper;
        /** 采购订单行Mapper */
        private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
        /** 物料收货行Mapper */
        private final WmMaterialReceiveLineMapper receiveLineMapper;

        /**
         * 超收容差比例：0 表示严格不允许超收（本仓库既有业务约定为「不得超收」）。
         * 若业务后续允许一定比例超收，改为如 {@code new BigDecimal("0.05")} 表示允许 5%。
         */
        private static final BigDecimal OVER_RECEIVE_TOLERANCE = BigDecimal.ZERO;

        /**
         * 回写采购订单行已收货数量，按物料匹配收货行与采购行，累加已收数量并更新行级收货状态
         *
         * @param receiveId 收货单ID
         * @param purId     采购订单ID
         */
        @Transactional(rollbackFor = Exception.class)
        public void writeBackReceivedQty(Long receiveId, Long purId) {
            log.info("开始回写采购订单已收货数量: receiveId={}, purId={}", receiveId, purId);
            if (purId == null) {
                // 不静默 return：静默返回会让 MQ 认为处理成功、且占位键已写，导致 24h 内永久跳过回写。
                // 抛异常交给 MQ 重投（补齐 pur_id 后重试）。
                throw new ServiceException("采购订单ID为空，无法回写已收货数量: receiveId=" + receiveId);
            }
            // Redis 不是永久生命周期事件，认证来源必须在任何幂等捷径前识别。
            certifiedDocumentGuard.requireLegacyOrderMutation(purId);
            // 幂等防重：同一收货单只回写一次，防止 MQ 重投/重复发送导致已收数量重复累加。
            // 幂等键只在【事务提交成功后】才真正写入（见 registerIdempotentKeyAfterCommit），
            // 因此回写失败/回滚不会留下占位键，重新投递仍可正常回写（修复 ERP-PU-02）。
            String idempotentKey = "erp:writeback:receive:" + receiveId;
            if (RedisUtils.getCacheObject(idempotentKey) != null) {
                log.info("收货单已回写过，跳过: receiveId={}", receiveId);
                return;
            }
            List<WmMaterialReceiveLine> receiveLines = io(() -> receiveLineMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveLine>().eq(WmMaterialReceiveLine::getReceiveId, receiveId)));
            log.info("查询到收货行数量: {}", receiveLines != null ? receiveLines.size() : 0);
            if (receiveLines == null || receiveLines.isEmpty()) {
                log.warn("收货行为空，跳过回写: receiveId={}", receiveId);
                return;
            }

            List<PuPurchaseOrderLine> orderLines = io(() -> purchaseOrderLineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseOrderLine>().eq(PuPurchaseOrderLine::getOrderId, purId)));
            log.info("查询到采购订单行数量: {}", orderLines != null ? orderLines.size() : 0);
            if (orderLines == null || orderLines.isEmpty()) {
                throw new ServiceException("采购订单没有明细行，无法回写已收货数量: purId=" + purId);
            }

            // 全批来源预检，恶意 purchaseLineId 不得移属，也不能先回写前几行再发现认证行。
            List<PuPurchaseOrderLine> resolved = new ArrayList<>();
            for (WmMaterialReceiveLine receiveLine : receiveLines) {
                if (receiveLine == null) throw new ServiceException("收货明细事实未知，禁止回写");
                if (receiveLine.getPurchaseLineId() != null) {
                    certifiedDocumentGuard.requireLegacyOrderLineMutation(receiveLine.getPurchaseLineId());
                }
                PuPurchaseOrderLine poLine = resolveOrderLine(purId, receiveLine);
                if (poLine == null) {
                    throw new ServiceException("收货行[" + receiveLine.getLineId() + "]未匹配到采购订单行，无法回写");
                }
                if (!Objects.equals(purId, poLine.getOrderId()) || receiveLine.getMaterialId() == null
                    || !Objects.equals(receiveLine.getMaterialId(), poLine.getMaterialId())) {
                    throw new ServiceException("收货行采购订单/物料归属不一致，禁止跨单回写");
                }
                certifiedDocumentGuard.requireLegacyOrderLineMutation(poLine.getLineId());
                resolved.add(poLine);
            }
            // 预检阶段没有写数量；同一订单行被多个收货行引用时必须累加本批已写数量。
            Map<Long, BigDecimal> receivedInBatch = new HashMap<>();
            for (int index = 0; index < receiveLines.size(); index++) {
                WmMaterialReceiveLine receiveLine = receiveLines.get(index);
                PuPurchaseOrderLine poLine = resolved.get(index);
                BigDecimal lineReceivedQty = receiveLine.getQuantityReceived() != null ? receiveLine.getQuantityReceived() : BigDecimal.ZERO;
                if (lineReceivedQty.compareTo(BigDecimal.ZERO) <= 0) {
                    // 实收为 0（草稿未填写）不产生回写
                    continue;
                }
                BigDecimal orderQty = poLine.getOrderQuantity() != null ? poLine.getOrderQuantity() : BigDecimal.ZERO;
                BigDecimal currentReceivedQty = receivedInBatch.getOrDefault(poLine.getLineId(),
                    poLine.getReceivedQuantity() != null ? poLine.getReceivedQuantity() : BigDecimal.ZERO);
                BigDecimal newReceivedQty = currentReceivedQty.add(lineReceivedQty);
                // ★ 超收守卫(ERP-PU-01)：已收 + 本次 <= 订单量 × (1 + 超收容差)
                BigDecimal maxAllowedQty = orderQty.multiply(BigDecimal.ONE.add(OVER_RECEIVE_TOLERANCE));
                if (newReceivedQty.compareTo(maxAllowedQty) > 0) {
                    throw new ServiceException("收货数量超过采购订单允许数量: lineId=" + poLine.getLineId()
                        + ", 订单量=" + orderQty.toPlainString()
                        + ", 已收=" + currentReceivedQty.toPlainString()
                        + ", 本次=" + lineReceivedQty.toPlainString()
                        + ", 超收容差=" + OVER_RECEIVE_TOLERANCE.toPlainString());
                }
                PurchaseOrderLineReceiveStatus lineStatus = calcLineReceiveStatus(orderQty, newReceivedQty);
                // 原子累加已收数量 + SQL 侧再校验一次上界（防并发双回写绕过 Java 侧判断）
                LambdaUpdateWrapper<PuPurchaseOrderLine> updateWrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
                    .eq(PuPurchaseOrderLine::getLineId, poLine.getLineId())
                    .setSql("received_quantity = received_quantity + " + lineReceivedQty.toPlainString())
                    .set(PuPurchaseOrderLine::getReceiveStatus, lineStatus.getCode())
                    .apply("received_quantity + {0} <= order_quantity * (1 + {1})", lineReceivedQty, OVER_RECEIVE_TOLERANCE);
                int rows = io(() -> purchaseOrderLineMapper.update(null, updateWrapper));
                if (rows == 0) {
                    throw new ServiceException("收货数量超过采购订单数量，回写被拒绝: lineId=" + poLine.getLineId());
                }
                receivedInBatch.put(poLine.getLineId(), newReceivedQty);
                log.info("更新采购订单行: lineId={}, receiveStatus={}, 影响行数={}", poLine.getLineId(), lineStatus.getCode(), rows);
            }

            // 重新查询订单行以反映最新已收数量,用于订单头状态汇总
            List<PuPurchaseOrderLine> latestOrderLines = io(() -> purchaseOrderLineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseOrderLine>().eq(PuPurchaseOrderLine::getOrderId, purId)));
            updatePurchaseOrderStatus(purId, latestOrderLines);

            // 业务写库全部成功后，事务提交时才落幂等键
            registerIdempotentKeyAfterCommit(idempotentKey);
        }

        /**
         * 幂等键延迟到事务提交后写入，保证回写失败可重试（修复 ERP-PU-02）。
         *
         * @param idempotentKey 幂等键
         */
        private void registerIdempotentKeyAfterCommit(String idempotentKey) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        RedisUtils.setObjectIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                    }
                });
                return;
            }
            RedisUtils.setObjectIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        }

        /**
         * 将收货行匹配到采购订单行。
         * <p>优先按收货行的 {@code purchaseLineId} 精确匹配；旧数据缺失时按 orderId + materialId 兜底，
         * 同一物料在订单中有多行时给出可读错误（避免 {@code selectOne} 抛 TooManyResultsException，ERP-PU-06）。</p>
         *
         * @param purId       采购订单ID
         * @param receiveLine 收货行
         * @return 匹配到的采购订单行，未匹配返回 null
         */
        private PuPurchaseOrderLine resolveOrderLine(Long purId, WmMaterialReceiveLine receiveLine) {
            if (receiveLine.getPurchaseLineId() != null) {
                return io(() -> purchaseOrderLineMapper.selectOne(Wrappers.<PuPurchaseOrderLine>lambdaQuery()
                    .eq(PuPurchaseOrderLine::getLineId, receiveLine.getPurchaseLineId())
                    .eq(PuPurchaseOrderLine::getOrderId, purId)
                    .eq(PuPurchaseOrderLine::getMaterialId, receiveLine.getMaterialId())
                    .last("FOR UPDATE")));
            }
            List<PuPurchaseOrderLine> matched = io(() -> purchaseOrderLineMapper.selectList(Wrappers.<PuPurchaseOrderLine>lambdaQuery()
                .eq(PuPurchaseOrderLine::getOrderId, purId)
                .eq(PuPurchaseOrderLine::getMaterialId, receiveLine.getMaterialId()).last("FOR UPDATE")));
            if (matched == null || matched.isEmpty()) {
                return null;
            }
            if (matched.size() > 1) {
                throw new ServiceException("收货行[" + receiveLine.getLineId()
                    + "]对应物料在采购订单中存在多行，无法确定回写目标，请补充收货行的采购订单行ID");
            }
            return matched.get(0);
        }

        /**
         * 根据订购数量与已收数量计算行级收货状态
         *
         * @param orderQty    订购数量
         * @param receivedQty 已收数量
         * @return 行级收货状态
         */
        private PurchaseOrderLineReceiveStatus calcLineReceiveStatus(BigDecimal orderQty, BigDecimal receivedQty) {
            BigDecimal safeOrderQty = orderQty != null ? orderQty : BigDecimal.ZERO;
            BigDecimal safeReceivedQty = receivedQty != null ? receivedQty : BigDecimal.ZERO;
            int cmp = safeReceivedQty.compareTo(safeOrderQty);
            if (cmp == 0) {
                return PurchaseOrderLineReceiveStatus.FULL_RECEIVED;
            }
            if (cmp > 0) {
                return PurchaseOrderLineReceiveStatus.OVER_RECEIVED;
            }
            if (safeReceivedQty.compareTo(BigDecimal.ZERO) > 0) {
                return PurchaseOrderLineReceiveStatus.PARTIAL_RECEIVED;
            }
            return PurchaseOrderLineReceiveStatus.PENDING;
        }

        /**
         * 根据所有行的收货状态汇总更新采购订单整体状态
         *
         * @param orderId    采购订单ID
         * @param orderLines 采购订单行列表
         */
        private void updatePurchaseOrderStatus(Long orderId, List<PuPurchaseOrderLine> orderLines) {
            if (orderLines == null || orderLines.isEmpty()) {
                return;
            }
            boolean allReceived = true;
            boolean anyReceived = false;
            for (PuPurchaseOrderLine line : orderLines) {
                BigDecimal orderQty = line.getOrderQuantity() != null ? line.getOrderQuantity() : BigDecimal.ZERO;
                BigDecimal receivedQty = line.getReceivedQuantity() != null ? line.getReceivedQuantity() : BigDecimal.ZERO;
                if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
                    anyReceived = true;
                }
                if (receivedQty.compareTo(orderQty) < 0) {
                    allReceived = false;
                }
            }
            if (!anyReceived) {
                return;
            }
            String newStatus = allReceived ? "RECEIVED" : "PARTIALLY_RECEIVED";
            // ★ 条件更新(ERP-PU-07): 已关闭/已作废订单不允许被收货回写"复活"，
            // 且不再用整实体 updateById 覆盖其它并发修改的字段。
            int rows = io(() -> purchaseOrderMapper.update(null, Wrappers.<PuPurchaseOrder>lambdaUpdate()
                .set(PuPurchaseOrder::getStatus, newStatus)
                .eq(PuPurchaseOrder::getOrderId, orderId)
                .notIn(PuPurchaseOrder::getStatus, "CLOSED", "CANCELLED")));
            if (rows == 0) {
                log.warn("采购订单状态未更新(可能已关闭/作废或已被并发修改): orderId={}, expectStatus={}", orderId, newStatus);
            }
        }
        /** 仅替换基础设施诊断信息；有意不捕获 Java Error。 */
        private <T> T io(Supplier<T> query) {
            try { return query.get(); }
            catch (RuntimeException failure) {
                // 不保留原始消息、原因或受抑制异常，也不记录错误日志：MQ 日志不得泄露 SQL/URL/凭据。
                throw new ServiceException("采购收货回写数据访问失败，操作未完成");
            }
        }
    }
}

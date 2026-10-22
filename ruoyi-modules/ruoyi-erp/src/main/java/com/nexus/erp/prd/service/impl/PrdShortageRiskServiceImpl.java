package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.vo.PrdShortageRiskVo;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.schedule.PrdShortageInput;
import com.nexus.erp.prd.schedule.PrdShortageResult;
import com.nexus.erp.prd.schedule.PrdShortageScenarioService;
import com.nexus.erp.prd.service.IPrdShortageRiskService;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 同名类型说明：本文件同时使用 java.sql.Date 和 java.util.Date，后者保留全限定名以区分。

/**
 * 供应链缺料风险预测 Service 实现
 *
 * <p>数据聚合(全部 ry-erp 库, @DS("erp")): 毛需求 = MRP 明细未发料余量(requiredQuantity − issuedQuantity, 按
 * requiredDate 聚合); 库存 = Σ(stockQuantity − reservedQuantity)(按物料汇总); 在途 = 采购订单行(orderQuantity −
 * receivedQuantity, 已审批/部分收货状态, 按主表 expectedArrivalDate 聚合)。 结果缓存 Redis 30 分钟(key 含租户), 推演型不落库。
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdShortageRiskServiceImpl implements IPrdShortageRiskService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final PrdMaterialRequirementDetailMapper requirementDetailMapper;
    private final WmMaterialStockMapper stockMapper;
    private final BdMaterialMapper materialMapper;
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final PrdShortageScenarioService scenarioService;

    @Override
    public List<PrdShortageRiskVo> predict(int days) {
        String cacheKey = buildCacheKey(days);
        List<PrdShortageRiskVo> cached = RedisUtils.getCacheObject(cacheKey);
        if (cached != null) {
            log.debug("[ShortageRisk] 命中缓存: {}", cacheKey);
            return cached;
        }
        List<PrdShortageRiskVo> results = doPredict(days);
        RedisUtils.setCacheObject(cacheKey, results, CACHE_TTL);
        return results;
    }

    @Override
    public List<PrdShortageRiskVo> predictRefresh(int days) {
        RedisUtils.deleteObject(buildCacheKey(days));
        return predict(days);
    }

    // 内部辅助方法。

    private String buildCacheKey(int days) {
        // 租户由 MyBatis-Plus 多租户插件在下述查询中自动隔离, 缓存 key 需自行带上租户
        String tenantId = TenantHelper.getTenantId();
        return "erp:shortage:predict:" + tenantId + ":" + days;
    }

    private List<PrdShortageRiskVo> doPredict(int days) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);

        // 1. 毛需求: MRP 明细未发料余量(需求日在推演期内)
        List<PrdMaterialRequirementDetail> details =
                requirementDetailMapper.selectList(
                        Wrappers.lambdaQuery(PrdMaterialRequirementDetail.class)
                                .isNotNull(PrdMaterialRequirementDetail::getRequiredDate)
                                .ge(
                                        PrdMaterialRequirementDetail::getRequiredDate,
                                        Date.valueOf(today))
                                .le(
                                        PrdMaterialRequirementDetail::getRequiredDate,
                                        Date.valueOf(horizon))
                                .gt(
                                        PrdMaterialRequirementDetail::getRequiredQuantity,
                                        BigDecimal.ZERO));

        // 2. 在途: 已审批/部分收货采购订单的未收货行(按预计到货日聚合; 到货日空的按今天即到)
        List<PuPurchaseOrder> orders =
                purchaseOrderMapper.selectList(
                        Wrappers.lambdaQuery(PuPurchaseOrder.class)
                                .in(PuPurchaseOrder::getStatus, "APPROVED", "PARTIALLY_RECEIVED")
                                .isNotNull(PuPurchaseOrder::getExpectedArrivalDate)
                                .le(
                                        PuPurchaseOrder::getExpectedArrivalDate,
                                        Date.valueOf(horizon)));
        Map<Long, PuPurchaseOrder> orderMap = new HashMap<>();
        for (PuPurchaseOrder order : orders) {
            orderMap.put(order.getOrderId(), order);
        }
        List<Long> orderIds = new ArrayList<>(orderMap.keySet());
        List<PuPurchaseOrderLine> lines = orderIds.isEmpty() ? List.of()
            : purchaseOrderLineMapper.selectList(Wrappers.lambdaQuery(PuPurchaseOrderLine.class)
                .in(PuPurchaseOrderLine::getOrderId, orderIds));

        // 3. 按物料聚合输入
        Map<Long, PrdShortageInput> inputs = new HashMap<>();
        for (PrdMaterialRequirementDetail detail : details) {
            if (detail.getMaterialId() == null) {
                continue;
            }
            // 未发料余量 = 毛需求 − 已发料
            BigDecimal required = detail.getRequiredQuantity() != null ? detail.getRequiredQuantity() : BigDecimal.ZERO;
            BigDecimal issued = detail.getIssuedQuantity() != null ? detail.getIssuedQuantity() : BigDecimal.ZERO;
            BigDecimal net = required.subtract(issued);
            if (net.signum() <= 0) {
                continue;
            }
            PrdShortageInput input = inputs.computeIfAbsent(detail.getMaterialId(), this::createInput);
            input.setMaterialId(detail.getMaterialId());
            input.setMaterialCode(detail.getMaterialCode());
            input.setMaterialName(detail.getMaterialName());
            input.addDemand(toLocalDate(detail.getRequiredDate()), net);
        }
        for (PuPurchaseOrderLine line : lines) {
            BigDecimal ordered = line.getOrderQuantity() != null ? line.getOrderQuantity() : BigDecimal.ZERO;
            BigDecimal received = line.getReceivedQuantity() != null ? line.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal inTransit = ordered.subtract(received);
            if (inTransit.signum() <= 0 || line.getMaterialId() == null) {
                continue;
            }
            PuPurchaseOrder order = orderMap.get(line.getOrderId());
            if (order == null) {
                continue;
            }
            PrdShortageInput input = inputs.computeIfAbsent(line.getMaterialId(), this::createInput);
            if (input.getMaterialCode() == null) {
                input.setMaterialId(line.getMaterialId());
                input.setMaterialCode(line.getMaterialCode());
                input.setMaterialName(line.getMaterialName());
            }
            input.addArrival(toLocalDate(order.getExpectedArrivalDate()), inTransit);
        }

        // 4. 补库存与安全库存(有需求或在途的物料)
        for (PrdShortageInput input : inputs.values()) {
            enrichStock(input);
        }

        // 5. 推演(按最早缺料日升序)
        List<PrdShortageResult> results = new ArrayList<>();
        for (PrdShortageInput input : inputs.values()) {
            results.add(scenarioService.simulate(input, List.of(), Map.of(), today));
        }
        results.sort((a, b) -> {
            if (a.getEarliestShortageDate() == null && b.getEarliestShortageDate() == null) {
                return 0;
            }
            if (a.getEarliestShortageDate() == null) {
                return 1;
            }
            if (b.getEarliestShortageDate() == null) {
                return -1;
            }
            return a.getEarliestShortageDate().compareTo(b.getEarliestShortageDate());
        });
        log.info("[ShortageRisk] 推演完成: days={}, 物料数={}, 有风险数={}",
            days, results.size(), results.stream().filter(PrdShortageResult::hasRisk).count());
        return results.stream().map(this::toVo).toList();
    }

    private PrdShortageInput createInput(Long materialId) {
        return new PrdShortageInput();
    }

    /**
     * 补充可用库存(Σ 库存量 − 预留)与安全库存
     */
    private void enrichStock(PrdShortageInput input) {
        List<WmMaterialStock> stocks = stockMapper.selectList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .eq(WmMaterialStock::getMaterialId, input.getMaterialId())
                .gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO));
        BigDecimal available = BigDecimal.ZERO;
        for (WmMaterialStock stock : stocks) {
            BigDecimal qty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
            BigDecimal reserved = stock.getReservedQuantity() != null ? stock.getReservedQuantity() : BigDecimal.ZERO;
            available = available.add(qty).subtract(reserved);
        }
        input.setStockQty(available);

        BdMaterial material = materialMapper.selectById(input.getMaterialId());
        input.setSafetyStock(material != null && material.getSafetyStock() != null
            ? material.getSafetyStock() : BigDecimal.ZERO);
    }

    private PrdShortageRiskVo toVo(PrdShortageResult result) {
        PrdShortageRiskVo vo = new PrdShortageRiskVo();
        vo.setMaterialId(result.getMaterialId());
        vo.setMaterialCode(result.getMaterialCode());
        vo.setMaterialName(result.getMaterialName());
        vo.setStockQty(result.getStockQty());
        vo.setSafetyStock(result.getSafetyStock());
        vo.setTotalDemand(result.getTotalDemand());
        vo.setOnOrderQty(result.getOnOrderQty());
        vo.setEarliestShortageDate(result.getEarliestShortageDate());
        vo.setDaysUntilShortage(result.getEarliestShortageDate() == null ? null
            : result.daysUntilShortage(LocalDate.now()));
        vo.setMaxGap(result.getMaxGap());
        vo.setGapDays(result.getGapDays());
        vo.setSuggestion(result.getSuggestion());
        vo.setSuggestionText(result.getSuggestionText());
        vo.setHasRisk(result.hasRisk());
        return vo;
    }

    private LocalDate toLocalDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}

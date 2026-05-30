package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.bd.mapper.BdVendorMapper;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.PuPurchaseReturn;
import com.nexus.erp.pu.domain.PuPurchaseReturnLine;
import com.nexus.erp.pu.domain.PuVendorEvaluation;
import com.nexus.erp.pu.domain.bo.PuVendorEvaluationBo;
import com.nexus.erp.pu.domain.vo.PuVendorEvaluationVo;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuPurchaseReturnLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseReturnMapper;
import com.nexus.erp.pu.mapper.PuVendorEvaluationMapper;
import com.nexus.erp.pu.service.IPuVendorEvaluationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 供应商评估服务实现类
 * <p>
 * 实现供应商绩效评估的核心业务逻辑，包括：
 * <ul>
 *   <li>CRUD 操作：分页查询、新增、修改、删除</li>
 *   <li>评估计算：按供应商单条计算、批量全量计算</li>
 * </ul>
 * 评估算法基于交货及时率（40%）、质量合格率（35%）、价格竞争力（25%）加权计算综合评分，
 * 并按得分划分为 A/B/C/D 四个等级。
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuVendorEvaluationServiceImpl implements IPuVendorEvaluationService {

    /** 交货及时率权重 40% */
    private static final BigDecimal DELIVERY_WEIGHT = new BigDecimal("0.40");
    /** 质量合格率权重 35% */
    private static final BigDecimal QUALITY_WEIGHT = new BigDecimal("0.35");
    /** 价格竞争力权重 25% */
    private static final BigDecimal PRICE_WEIGHT = new BigDecimal("0.25");
    /** 默认价格得分（无市场对比数据时） */
    private static final BigDecimal DEFAULT_PRICE_SCORE = new BigDecimal("80");
    /** 得分保留小数位 */
    private static final int SCORE_SCALE = 2;

    private final PuVendorEvaluationMapper baseMapper;
    private final PuPurchaseOrderMapper orderMapper;
    private final PuPurchaseOrderLineMapper orderLineMapper;
    private final PuPurchaseReturnMapper returnMapper;
    private final PuPurchaseReturnLineMapper returnLineMapper;
    private final BdVendorMapper vendorMapper;

    /**
     * 分页查询供应商评估列表
     */
    @Override
    public TableDataInfo<PuVendorEvaluationVo> selectPageList(PuVendorEvaluationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuVendorEvaluation> lqw = buildQueryWrapper(bo);
        Page<PuVendorEvaluationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询供应商评估列表（不分页）
     */
    @Override
    public List<PuVendorEvaluationVo> selectList(PuVendorEvaluationBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询供应商评估详情
     */
    @Override
    public PuVendorEvaluationVo selectById(Long evaluationId) {
        return baseMapper.selectVoById(evaluationId);
    }

    /**
     * 新增供应商评估记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuVendorEvaluationBo bo) {
        PuVendorEvaluation entity = MapstructUtils.convert(bo, PuVendorEvaluation.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改供应商评估记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuVendorEvaluationBo bo) {
        PuVendorEvaluation entity = MapstructUtils.convert(bo, PuVendorEvaluation.class);
        PuVendorEvaluation current = baseMapper.selectById(entity.getEvaluationId());
        if (current == null) {
            throw new ServiceException("评估记录不存在");
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除供应商评估记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] evaluationIds) {
        return baseMapper.deleteBatchIds(List.of(evaluationIds));
    }

    /**
     * 按供应商计算绩效评估
     * <p>
     * 校验供应商有效性，取当前月份作为评估周期，调用核心计算逻辑。
     *
     * @param vendorId 供应商ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateByVendor(Long vendorId) {
        if (vendorId == null) {
            throw new ServiceException("供应商ID不能为空");
        }
        BdVendor vendor = vendorMapper.selectById(vendorId);
        if (vendor == null) {
            throw new ServiceException("供应商不存在(ID={})", vendorId);
        }
        String evalPeriod = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        doCalculate(vendor, evalPeriod);
    }

    /**
     * 批量计算所有供应商绩效评估
     * <p>
     * 遍历所有状态为 ACTIVE 的供应商，逐条执行评估计算。
     * 单个供应商计算失败时记录警告日志并继续处理其他供应商。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCalculateAll() {
        List<BdVendor> vendors = vendorMapper.selectList(
            new LambdaQueryWrapper<BdVendor>().eq(BdVendor::getStatus, "ACTIVE"));
        if (vendors == null || vendors.isEmpty()) {
            return;
        }
        String evalPeriod = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        for (BdVendor vendor : vendors) {
            try {
                doCalculate(vendor, evalPeriod);
            } catch (Exception e) {
                log.warn("供应商[{}]评估计算失败: {}", vendor.getVendorName(), e.getMessage());
            }
        }
    }

    /**
     * 执行供应商绩效评估核心计算
     * <p>
     * 计算流程：
     * <ol>
     *   <li>查询评估周期内该供应商的有效采购订单，统计订单总数和按时交货数</li>
     *   <li>查询采购订单明细，统计总收货数量</li>
     *   <li>查询退货单明细，统计退货总数量</li>
     *   <li>计算交货及时率、质量合格率、价格竞争力得分</li>
     *   <li>按权重加权计算综合评分，划分等级</li>
     *   <li>保存或更新评估记录</li>
     * </ol>
     *
     * @param vendor     供应商实体
     * @param evalPeriod 评估周期，格式 yyyy-MM
     */
    private void doCalculate(BdVendor vendor, String evalPeriod) {
        Date[] range = parsePeriod(evalPeriod);
        Date startDate = range[0];
        Date endDate = range[1];

        List<PuPurchaseOrder> orders = orderMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseOrder>()
                .eq(PuPurchaseOrder::getVendorId, vendor.getVendorId())
                .ge(PuPurchaseOrder::getOrderDate, startDate)
                .lt(PuPurchaseOrder::getOrderDate, endDate)
                .notIn(PuPurchaseOrder::getStatus, "DRAFT", "SUBMITTED", "CANCELLED"));

        int totalOrders = orders == null ? 0 : orders.size();
        int onTimeOrders = 0;
        BigDecimal totalReceivedQty = BigDecimal.ZERO;
        BigDecimal returnQty = BigDecimal.ZERO;

        List<Long> orderIds = new ArrayList<>();
        if (orders != null) {
            for (PuPurchaseOrder order : orders) {
                orderIds.add(order.getOrderId());
                if (isOnTime(order)) {
                    onTimeOrders++;
                }
            }
        }

        if (!orderIds.isEmpty()) {
            List<PuPurchaseOrderLine> orderLines = orderLineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseOrderLine>()
                    .in(PuPurchaseOrderLine::getOrderId, orderIds));
            if (orderLines != null) {
                for (PuPurchaseOrderLine line : orderLines) {
                    if (line.getReceivedQuantity() != null) {
                        totalReceivedQty = totalReceivedQty.add(line.getReceivedQuantity());
                    }
                }
            }
        }

        List<PuPurchaseReturn> returns = returnMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseReturn>()
                .eq(PuPurchaseReturn::getVendorId, vendor.getVendorId())
                .ge(PuPurchaseReturn::getReturnDate, startDate)
                .lt(PuPurchaseReturn::getReturnDate, endDate)
                .ne(PuPurchaseReturn::getStatus, "CANCELLED"));

        if (returns != null && !returns.isEmpty()) {
            List<Long> returnIds = new ArrayList<>();
            for (PuPurchaseReturn r : returns) {
                returnIds.add(r.getReturnId());
            }
            List<PuPurchaseReturnLine> returnLines = returnLineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseReturnLine>()
                    .in(PuPurchaseReturnLine::getReturnId, returnIds));
            if (returnLines != null) {
                for (PuPurchaseReturnLine line : returnLines) {
                    if (line.getReturnQuantity() != null) {
                        returnQty = returnQty.add(line.getReturnQuantity());
                    }
                }
            }
        }

        BigDecimal deliveryRate = BigDecimal.ZERO;
        if (totalOrders > 0) {
            deliveryRate = new BigDecimal(onTimeOrders)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(totalOrders), SCORE_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal qualityRate = BigDecimal.ZERO;
        if (totalReceivedQty.compareTo(BigDecimal.ZERO) > 0) {
            qualityRate = totalReceivedQty.subtract(returnQty)
                .multiply(new BigDecimal("100"))
                .divide(totalReceivedQty, SCORE_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal priceScore = calculatePriceScore(orderIds);

        BigDecimal totalScore = deliveryRate.multiply(DELIVERY_WEIGHT)
            .add(qualityRate.multiply(QUALITY_WEIGHT))
            .add(priceScore.multiply(PRICE_WEIGHT))
            .setScale(SCORE_SCALE, RoundingMode.HALF_UP);

        String evalLevel = calcLevel(totalScore);

        PuVendorEvaluation existing = baseMapper.selectOne(
            new LambdaQueryWrapper<PuVendorEvaluation>()
                .eq(PuVendorEvaluation::getVendorId, vendor.getVendorId())
                .eq(PuVendorEvaluation::getEvalPeriod, evalPeriod));

        if (existing != null) {
            existing.setDeliveryRate(deliveryRate);
            existing.setQualityRate(qualityRate);
            existing.setPriceScore(priceScore);
            existing.setTotalScore(totalScore);
            existing.setEvalLevel(evalLevel);
            existing.setTotalOrders(totalOrders);
            existing.setOnTimeOrders(onTimeOrders);
            existing.setTotalReceivedQty(totalReceivedQty);
            existing.setQualifiedQty(totalReceivedQty.subtract(returnQty));
            existing.setReturnQty(returnQty);
            baseMapper.updateById(existing);
        } else {
            PuVendorEvaluation evaluation = new PuVendorEvaluation();
            evaluation.setVendorId(vendor.getVendorId());
            evaluation.setVendorCode(vendor.getVendorCode());
            evaluation.setVendorName(vendor.getVendorName());
            evaluation.setEvalPeriod(evalPeriod);
            evaluation.setDeliveryRate(deliveryRate);
            evaluation.setQualityRate(qualityRate);
            evaluation.setPriceScore(priceScore);
            evaluation.setTotalScore(totalScore);
            evaluation.setEvalLevel(evalLevel);
            evaluation.setTotalOrders(totalOrders);
            evaluation.setOnTimeOrders(onTimeOrders);
            evaluation.setTotalReceivedQty(totalReceivedQty);
            evaluation.setQualifiedQty(totalReceivedQty.subtract(returnQty));
            evaluation.setReturnQty(returnQty);
            baseMapper.insert(evaluation);
        }
    }

    private boolean isOnTime(PuPurchaseOrder order) {
        if (!"RECEIVED".equals(order.getStatus())) {
            return false;
        }
        if (order.getExpectedArrivalDate() == null) {
            return true;
        }
        Date completionTime = order.getUpdateTime() != null ? order.getUpdateTime() : order.getCreateTime();
        return completionTime != null && !completionTime.after(order.getExpectedArrivalDate());
    }

    private String calcLevel(BigDecimal totalScore) {
        if (totalScore.compareTo(new BigDecimal("90")) >= 0) {
            return "A";
        } else if (totalScore.compareTo(new BigDecimal("80")) >= 0) {
            return "B";
        } else if (totalScore.compareTo(new BigDecimal("60")) >= 0) {
            return "C";
        } else {
            return "D";
        }
    }

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private Date[] parsePeriod(String evalPeriod) {
        YearMonth ym = YearMonth.parse(evalPeriod, PERIOD_FORMATTER);
        java.sql.Date startDate = java.sql.Date.valueOf(ym.atDay(1));
        java.sql.Date endDate = java.sql.Date.valueOf(ym.atEndOfMonth());
        return new Date[]{startDate, endDate};
    }

    private BigDecimal calculatePriceScore(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return DEFAULT_PRICE_SCORE;
        }
        List<PuPurchaseOrderLine> priceLines = orderLineMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseOrderLine>()
                .in(PuPurchaseOrderLine::getOrderId, orderIds)
                .isNotNull(PuPurchaseOrderLine::getUnitPrice)
                .gt(PuPurchaseOrderLine::getUnitPrice, BigDecimal.ZERO));
        if (priceLines == null || priceLines.isEmpty()) {
            return DEFAULT_PRICE_SCORE;
        }
        BigDecimal vendorTotal = BigDecimal.ZERO;
        int vendorCount = 0;
        for (PuPurchaseOrderLine line : priceLines) {
            vendorTotal = vendorTotal.add(line.getUnitPrice());
            vendorCount++;
        }
        BigDecimal vendorAvg = vendorTotal.divide(new BigDecimal(vendorCount), SCORE_SCALE, RoundingMode.HALF_UP);

        List<Long> materialIds = priceLines.stream()
            .map(PuPurchaseOrderLine::getMaterialId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        BigDecimal marketAvg = BigDecimal.ZERO;
        int marketCount = 0;
        if (!materialIds.isEmpty()) {
            List<PuPurchaseOrderLine> marketLines = orderLineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseOrderLine>()
                    .in(PuPurchaseOrderLine::getMaterialId, materialIds)
                    .isNotNull(PuPurchaseOrderLine::getUnitPrice)
                    .gt(PuPurchaseOrderLine::getUnitPrice, BigDecimal.ZERO));
            if (marketLines != null) {
                for (PuPurchaseOrderLine line : marketLines) {
                    marketAvg = marketAvg.add(line.getUnitPrice());
                    marketCount++;
                }
            }
        }

        if (marketCount > 0 && marketAvg.compareTo(BigDecimal.ZERO) > 0) {
            marketAvg = marketAvg.divide(new BigDecimal(marketCount), SCORE_SCALE, RoundingMode.HALF_UP);
            BigDecimal ratio = marketAvg.divide(vendorAvg, 4, RoundingMode.HALF_UP);
            BigDecimal score = ratio.multiply(new BigDecimal("100"));
            score = score.min(new BigDecimal("100")).max(BigDecimal.ZERO);
            return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        }
        return DEFAULT_PRICE_SCORE;
    }

    /**
     * 按供应商查询评估趋势
     *
     * @param vendorId 供应商ID
     * @return 供应商评估趋势列表
     */
    @Override
    public List<PuVendorEvaluationVo> selectTrendByVendorId(Long vendorId) {
        LambdaQueryWrapper<PuVendorEvaluation> lqw = new LambdaQueryWrapper<>();
        lqw.eq(PuVendorEvaluation::getVendorId, vendorId);
        lqw.orderByAsc(PuVendorEvaluation::getEvalPeriod);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 查询供应商评估等级分布
     *
     * @return 等级分布统计，key 为等级，value 为数量
     */
    @Override
    public Map<String, Integer> selectLevelDistribution() {
        List<PuVendorEvaluation> all = baseMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Integer> distribution = new java.util.LinkedHashMap<>();
        distribution.put("A", 0);
        distribution.put("B", 0);
        distribution.put("C", 0);
        distribution.put("D", 0);
        if (all != null) {
            for (PuVendorEvaluation eval : all) {
                String level = eval.getEvalLevel();
                if (level != null && distribution.containsKey(level)) {
                    distribution.put(level, distribution.get(level) + 1);
                }
            }
        }
        return distribution;
    }

    private LambdaQueryWrapper<PuVendorEvaluation> buildQueryWrapper(PuVendorEvaluationBo bo) {
        LambdaQueryWrapper<PuVendorEvaluation> lqw = new LambdaQueryWrapper<>();
        lqw.eq(bo.getVendorId() != null, PuVendorEvaluation::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getEvalPeriod()), PuVendorEvaluation::getEvalPeriod, bo.getEvalPeriod());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuVendorEvaluation::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getEvalLevel()), PuVendorEvaluation::getEvalLevel, bo.getEvalLevel());
        lqw.orderByDesc(PuVendorEvaluation::getCreateTime);
        return lqw;
    }
}

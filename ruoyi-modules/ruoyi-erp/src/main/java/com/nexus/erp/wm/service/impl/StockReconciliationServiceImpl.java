package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmStockReconciliation;
import com.nexus.erp.wm.domain.bo.WmStockReconciliationBo;
import com.nexus.erp.wm.domain.vo.WmStockReconciliationVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmStockReconciliationMapper;
import com.nexus.erp.wm.service.IStockReconciliationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存台账校验Service业务层处理 * 核心逻辑：对比账面库存与事务流水的差异，发现库存异常
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class StockReconciliationServiceImpl implements IStockReconciliationService {

    public static final String STATUS_MATCH = "MATCH";
    public static final String STATUS_DIFF = "DIFF";

    private final WmStockReconciliationMapper baseMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;

    @Override
    public TableDataInfo<WmStockReconciliationVo> selectPageList(WmStockReconciliationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStockReconciliation> lqw = buildQueryWrapper(bo);
        Page<WmStockReconciliationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStockReconciliationVo> selectList(WmStockReconciliationBo bo) {
        LambdaQueryWrapper<WmStockReconciliation> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public WmStockReconciliationVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcile() {
        log.info("开始执行全量库存台账校验");
        Date now = new Date();

        List<WmMaterialStock> allStocks = stockMapper.selectList(
            new LambdaQueryWrapper<WmMaterialStock>()
                .eq(WmMaterialStock::getFrozenFlag, 0L));

        if (allStocks == null || allStocks.isEmpty()) {
            log.info("无库存记录需要校验");
            return;
        }

        Map<Long, BigDecimal> stockByMaterial = allStocks.stream()
            .filter(s -> s.getMaterialId() != null)
            .collect(Collectors.groupingBy(
                WmMaterialStock::getMaterialId,
                Collectors.reducing(BigDecimal.ZERO,
                    s -> s.getStockQuantity() != null ? s.getStockQuantity() : BigDecimal.ZERO,
                    BigDecimal::add)));

        List<WmMaterialStockTx> allTx = stockTxMapper.selectList(new LambdaQueryWrapper<>());

        Map<Long, BigDecimal> txInByMaterial = new java.util.HashMap<>();
        Map<Long, BigDecimal> txOutByMaterial = new java.util.HashMap<>();

        if (allTx != null) {
            for (WmMaterialStockTx tx : allTx) {
                if (tx.getMaterialId() == null || tx.getTxQuantity() == null) {
                    continue;
                }
                BigDecimal qty = tx.getTxQuantity().abs();
                if (tx.getTxFlag() != null && tx.getTxFlag() > 0) {
                    txInByMaterial.merge(tx.getMaterialId(), qty, BigDecimal::add);
                } else if (tx.getTxFlag() != null && tx.getTxFlag() < 0) {
                    txOutByMaterial.merge(tx.getMaterialId(), qty, BigDecimal::add);
                }
            }
        }

        List<WmStockReconciliation> results = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : stockByMaterial.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal bookQty = entry.getValue();

            BigDecimal txIn = txInByMaterial.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal txOut = txOutByMaterial.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal txBalance = txIn.subtract(txOut);

            BigDecimal diffQty = bookQty.subtract(txBalance);

            if (diffQty.compareTo(new BigDecimal("0.001")) > 0 || diffQty.compareTo(new BigDecimal("-0.001")) < 0) {
                WmStockReconciliation recon = new WmStockReconciliation();
                recon.setMaterialId(materialId);
                recon.setBookQty(bookQty);
                recon.setTxBalance(txBalance);
                recon.setDiffQty(diffQty);
                recon.setStatus(STATUS_DIFF);
                recon.setReconTime(now);
                results.add(recon);
            }
        }

        if (!results.isEmpty()) {
            baseMapper.insertBatch(results);
            log.warn("库存台账校验发现{}条差异", results.size());
        } else {
            log.info("库存台账校验通过，无差异");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileByMaterial(Long materialId) {
        if (materialId == null) {
            throw new IllegalArgumentException("物料ID不能为空");
        }
        Date now = new Date();

        List<WmMaterialStock> stocks = stockMapper.selectList(
            new LambdaQueryWrapper<WmMaterialStock>()
                .eq(WmMaterialStock::getMaterialId, materialId)
                .eq(WmMaterialStock::getFrozenFlag, 0L));

        BigDecimal bookQty = BigDecimal.ZERO;
        if (stocks != null) {
            for (WmMaterialStock stock : stocks) {
                if (stock.getStockQuantity() != null) {
                    bookQty = bookQty.add(stock.getStockQuantity());
                }
            }
        }

        List<WmMaterialStockTx> txList = stockTxMapper.selectList(
            new LambdaQueryWrapper<WmMaterialStockTx>()
                .eq(WmMaterialStockTx::getMaterialId, materialId));

        BigDecimal txIn = BigDecimal.ZERO;
        BigDecimal txOut = BigDecimal.ZERO;
        if (txList != null) {
            for (WmMaterialStockTx tx : txList) {
                if (tx.getTxQuantity() == null) continue;
                BigDecimal qty = tx.getTxQuantity().abs();
                if (tx.getTxFlag() != null && tx.getTxFlag() > 0) {
                    txIn = txIn.add(qty);
                } else if (tx.getTxFlag() != null && tx.getTxFlag() < 0) {
                    txOut = txOut.add(qty);
                }
            }
        }

        BigDecimal txBalance = txIn.subtract(txOut);
        BigDecimal diffQty = bookQty.subtract(txBalance);

        WmStockReconciliation recon = new WmStockReconciliation();
        recon.setMaterialId(materialId);
        recon.setBookQty(bookQty);
        recon.setTxBalance(txBalance);
        recon.setDiffQty(diffQty);
        recon.setStatus(Math.abs(diffQty.doubleValue()) < 0.001 ? STATUS_MATCH : STATUS_DIFF);
        recon.setReconTime(now);
        baseMapper.insert(recon);
    }

    private LambdaQueryWrapper<WmStockReconciliation> buildQueryWrapper(WmStockReconciliationBo bo) {
        LambdaQueryWrapper<WmStockReconciliation> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), WmStockReconciliation::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmStockReconciliation::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmStockReconciliation::getStatus, bo.getStatus());
        lqw.orderByDesc(WmStockReconciliation::getReconTime);
        return lqw;
    }
}

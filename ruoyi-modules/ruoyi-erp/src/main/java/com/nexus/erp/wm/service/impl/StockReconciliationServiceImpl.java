package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmStockReconciliation;
import com.nexus.erp.wm.domain.bo.WmStockReconciliationBo;
import com.nexus.erp.wm.domain.vo.WmStockReconciliationVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmStockReconciliationMapper;
import com.nexus.erp.wm.service.IStockReconciliationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 常量：STATUSMATCH */
    public static final String STATUS_MATCH = "MATCH";
    /** 常量：STATUSDIFF */
    public static final String STATUS_DIFF = "DIFF";

    /** 基础数据访问映射器（Mapper） */
    private final WmStockReconciliationMapper baseMapper;
    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 库存流水数据访问映射器（Mapper） */
    private final WmMaterialStockTxMapper stockTxMapper;
    /** 物料数据访问映射器（Mapper） */
    private final BdMaterialMapper materialMapper;
    /** 库存对账数据访问映射器（Mapper） */
    private final BdUnitOfMeasureMapper uomMapper;

    /**
     * 分页查询库存对账列表
     *
     * @param bo 库存对账业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmStockReconciliationVo> selectPageList(WmStockReconciliationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStockReconciliation> lqw = buildQueryWrapper(bo);
        Page<WmStockReconciliationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询库存对账列表
     *
     * @param bo 库存对账业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmStockReconciliationVo> selectList(WmStockReconciliationBo bo) {
        LambdaQueryWrapper<WmStockReconciliation> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 查询库存对账
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmStockReconciliationVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 删除库存对账
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 库存对账处理（reconcile）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcile() {
        log.info("开始执行全量库存台账校验");
        Date now = new Date();

        // 使用SQL聚合查询账面库存（按物料汇总），避免全量加载
        List<Map<String, Object>> bookQtyList = stockMapper.selectMaps(
            Wrappers.<WmMaterialStock>query()
                .select("material_id", "SUM(stock_quantity) as total_qty")
                .groupBy("material_id"));

        if (bookQtyList == null) bookQtyList = List.of();

        // 使用SQL聚合查询事务流水（按物料汇总入库和出库）
        List<Map<String, Object>> txList = stockTxMapper.selectMaps(
            Wrappers.<WmMaterialStockTx>query()
                .select("material_id",
                        "SUM(CASE WHEN tx_flag > 0 THEN tx_quantity ELSE 0 END) as total_in",
                        "SUM(CASE WHEN tx_flag < 0 THEN tx_quantity ELSE 0 END) as total_out")
                .groupBy("material_id"));

        Map<Long, BigDecimal> txInMap = new HashMap<>();
        Map<Long, BigDecimal> txOutMap = new HashMap<>();
        if (txList != null) {
            for (Map<String, Object> row : txList) {
                Long materialId = ((Number) row.get("material_id")).longValue();
                BigDecimal totalIn = toBigDecimal(row.get("total_in"));
                BigDecimal totalOut = toBigDecimal(row.get("total_out"));
                txInMap.put(materialId, totalIn);
                txOutMap.put(materialId, totalOut);
            }
        }

        Map<Long, BigDecimal> bookByMaterial = new HashMap<>();
        for (Map<String, Object> row : bookQtyList) {
            bookByMaterial.put(((Number) row.get("material_id")).longValue(), toBigDecimal(row.get("total_qty")));
        }
        Set<Long> allMaterials = new LinkedHashSet<>(bookByMaterial.keySet());
        allMaterials.addAll(txInMap.keySet());
        allMaterials.addAll(txOutMap.keySet());
        List<Long> materialIds = new ArrayList<>(allMaterials);
        Map<Long, BdMaterial> materialMap = batchLoadMaterials(materialIds);

        List<WmStockReconciliation> results = new ArrayList<>();
        for (Long materialId : materialIds) {
            BigDecimal bookQty = bookByMaterial.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal txIn = txInMap.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal txOut = txOutMap.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal txBalance = txIn.subtract(txOut);
            BigDecimal diffQty = bookQty.subtract(txBalance);

            if (diffQty.abs().compareTo(BigDecimal.ZERO) > 0) {
                WmStockReconciliation recon = new WmStockReconciliation();
                recon.setMaterialId(materialId);
                fillMaterialInfo(recon, materialMap.get(materialId));
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

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    /**
     * 库存对账处理（reconcileByMaterial）
     *
     * @param materialId 物料 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileByMaterial(Long materialId) {
        if (materialId == null) {
            throw new IllegalArgumentException("物料ID不能为空");
        }
        Date now = new Date();

        List<WmMaterialStock> stocks = stockMapper.selectList(
            new LambdaQueryWrapper<WmMaterialStock>()
                .eq(WmMaterialStock::getMaterialId, materialId));

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
        fillMaterialInfo(recon, materialMapper.selectById(materialId));
        recon.setBookQty(bookQty);
        recon.setTxBalance(txBalance);
        recon.setDiffQty(diffQty);
        recon.setStatus(diffQty.abs().compareTo(BigDecimal.ZERO) <= 0 ? STATUS_MATCH : STATUS_DIFF);
        recon.setReconTime(now);
        baseMapper.insert(recon);
    }

    private Map<Long, BdMaterial> batchLoadMaterials(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }
        List<BdMaterial> materials = materialMapper.selectBatchIds(materialIds);
        Map<Long, BdMaterial> materialMap = materials.stream()
            .collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m, (v1, v2) -> v1));
        return materialMap;
    }

    private void fillMaterialInfo(WmStockReconciliation recon, BdMaterial material) {
        if (material == null) {
            return;
        }
        recon.setMaterialCode(material.getMaterialCode());
        recon.setMaterialName(material.getMaterialName());
        recon.setMaterialSpec(material.getMaterialSpec());
        if (material.getBaseUomId() != null) {
            BdUnitOfMeasure uom = uomMapper.selectById(material.getBaseUomId());
            if (uom != null) {
                recon.setBaseUomName(uom.getUomName());
            }
        }
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

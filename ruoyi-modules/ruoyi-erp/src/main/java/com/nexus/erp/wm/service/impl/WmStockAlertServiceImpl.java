package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStockAlert;
import com.nexus.erp.wm.domain.bo.WmStockAlertBo;
import com.nexus.erp.wm.domain.vo.WmStockAlertVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStockAlertMapper;
import com.nexus.erp.wm.service.IWmStockAlertService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 安全库存预警Service业务层处理 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStockAlertServiceImpl implements IWmStockAlertService {

    public static final String ALERT_TYPE_BELOW_MIN = "BELOW_MIN";
    public static final String ALERT_TYPE_BELOW_SAFETY = "BELOW_SAFETY";
    public static final String ALERT_TYPE_ABOVE_MAX = "ABOVE_MAX";
    public static final String ALERT_LEVEL_CRITICAL = "CRITICAL";
    public static final String ALERT_LEVEL_WARNING = "WARNING";
    public static final String ALERT_LEVEL_INFO = "INFO";

    private final WmStockAlertMapper baseMapper;
    private final BdMaterialMapper materialMapper;
    private final BdUnitOfMeasureMapper uomMapper;
    private final WmMaterialStockMapper stockMapper;

    @Override
    public TableDataInfo<WmStockAlertVo> selectPageList(WmStockAlertBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStockAlert> lqw = buildQueryWrapper(bo);
        Page<WmStockAlertVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStockAlertVo> selectList(WmStockAlertBo bo) {
        LambdaQueryWrapper<WmStockAlert> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public WmStockAlertVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStockAlertBo bo) {
        WmStockAlert alert = MapstructUtils.convert(bo, WmStockAlert.class);
        return baseMapper.insert(alert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStockAlertBo bo) {
        WmStockAlert alert = MapstructUtils.convert(bo, WmStockAlert.class);
        return baseMapper.updateById(alert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkStockAlerts() {
        log.info("开始执行安全库存预警检查");
        List<BdMaterial> materials = materialMapper.selectList(
            new LambdaQueryWrapper<BdMaterial>()
                .eq(BdMaterial::getStatus, 1L)
                .and(w -> w.gt(BdMaterial::getSafetyStock, BigDecimal.ZERO)
                    .or().gt(BdMaterial::getMinStock, BigDecimal.ZERO)));

        if (materials == null || materials.isEmpty()) {
            log.info("无需要检查的物料");
            return;
        }

        Date now = new Date();
        int alertCount = 0;

        for (BdMaterial material : materials) {
            try {
                alertCount += checkMaterialStock(material, now);
            } catch (Exception e) {
                log.warn("物料[{}]库存预警检查失败 {}", material.getMaterialCode(), e.getMessage());
            }
        }

        log.info("安全库存预警检查完成，生成{}条预警", alertCount);
    }

    private int checkMaterialStock(BdMaterial material, Date now) {
        List<WmMaterialStock> stocks = stockMapper.selectList(
            new LambdaQueryWrapper<WmMaterialStock>()
                .eq(WmMaterialStock::getMaterialId, material.getMaterialId())
                .eq(WmMaterialStock::getFrozenFlag, 0L));

        BigDecimal totalQty = BigDecimal.ZERO;
        if (stocks != null) {
            for (WmMaterialStock stock : stocks) {
                if (stock.getStockQuantity() != null) {
                    totalQty = totalQty.add(stock.getStockQuantity());
                }
            }
        }

        BigDecimal safetyStock = material.getSafetyStock() != null ? material.getSafetyStock() : BigDecimal.ZERO;
        BigDecimal minStock = material.getMinStock() != null ? material.getMinStock() : BigDecimal.ZERO;
        BigDecimal maxStock = material.getMaxStock() != null ? material.getMaxStock() : BigDecimal.ZERO;

        int count = 0;

        if (minStock.compareTo(BigDecimal.ZERO) > 0 && totalQty.compareTo(minStock) < 0) {
            createOrUpdateAlert(material, null, totalQty, safetyStock, minStock, maxStock,
                ALERT_TYPE_BELOW_MIN, ALERT_LEVEL_CRITICAL, now);
            count++;
        } else if (safetyStock.compareTo(BigDecimal.ZERO) > 0 && totalQty.compareTo(safetyStock) < 0) {
            createOrUpdateAlert(material, null, totalQty, safetyStock, minStock, maxStock,
                ALERT_TYPE_BELOW_SAFETY, ALERT_LEVEL_WARNING, now);
            count++;
        }

        if (maxStock.compareTo(BigDecimal.ZERO) > 0 && totalQty.compareTo(maxStock) > 0) {
            createOrUpdateAlert(material, null, totalQty, safetyStock, minStock, maxStock,
                ALERT_TYPE_ABOVE_MAX, ALERT_LEVEL_INFO, now);
            count++;
        }

        return count;
    }

    private void createOrUpdateAlert(BdMaterial material, Long warehouseId, BigDecimal currentQty,
                                      BigDecimal safetyStock, BigDecimal minStock, BigDecimal maxStock,
                                      String alertType, String alertLevel, Date now) {
        LambdaQueryWrapper<WmStockAlert> lqw = new LambdaQueryWrapper<>();
        lqw.eq(WmStockAlert::getMaterialId, material.getMaterialId());
        lqw.eq(WmStockAlert::getAlertType, alertType);
        lqw.eq(WmStockAlert::getHandledFlag, 0);

        WmStockAlert existing = baseMapper.selectOne(lqw);
        if (existing != null) {
            existing.setCurrentQty(currentQty);
            existing.setAlertLevel(alertLevel);
            existing.setUpdateTime(now);
            baseMapper.updateById(existing);
        } else {
            WmStockAlert alert = new WmStockAlert();
            alert.setMaterialId(material.getMaterialId());
            alert.setMaterialCode(material.getMaterialCode());
            alert.setMaterialName(material.getMaterialName());
            alert.setMaterialSpec(material.getMaterialSpec());
            if (material.getBaseUomId() != null) {
                BdUnitOfMeasure uom = uomMapper.selectById(material.getBaseUomId());
                if (uom != null) {
                    alert.setBaseUomName(uom.getUomName());
                }
            }
            alert.setWarehouseId(warehouseId);
            alert.setCurrentQty(currentQty);
            alert.setSafetyStock(safetyStock);
            alert.setMinStock(minStock);
            alert.setMaxStock(maxStock);
            alert.setAlertType(alertType);
            alert.setAlertLevel(alertLevel);
            alert.setHandledFlag(0);
            baseMapper.insert(alert);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long alertId, String handledRemark) {
        WmStockAlert alert = baseMapper.selectById(alertId);
        if (alert == null) {
            throw new ServiceException("预警记录不存在");
        }
        alert.setHandledFlag(1);
        alert.setHandledTime(new Date());
        alert.setHandledRemark(handledRemark);
        baseMapper.updateById(alert);
    }

    private LambdaQueryWrapper<WmStockAlert> buildQueryWrapper(WmStockAlertBo bo) {
        LambdaQueryWrapper<WmStockAlert> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), WmStockAlert::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmStockAlert::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getAlertType()), WmStockAlert::getAlertType, bo.getAlertType());
        lqw.eq(StringUtils.isNotBlank(bo.getAlertLevel()), WmStockAlert::getAlertLevel, bo.getAlertLevel());
        lqw.eq(bo.getHandledFlag() != null, WmStockAlert::getHandledFlag, bo.getHandledFlag());
        lqw.orderByDesc(WmStockAlert::getCreateTime);
        return lqw;
    }
}

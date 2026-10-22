package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 安全库存预警Service业务层处理
 * <p>
 * 提供安全库存预警的分页查询、列表查询、新增、修改、删除等业务操作。
 * 支持定时扫描物料库存，自动生成低于安全库存、最低库存或超出最高库存的预警记录。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStockAlertServiceImpl implements IWmStockAlertService {

    /** 预警类型：低于最低库存 */
    public static final String ALERT_TYPE_BELOW_MIN = "BELOW_MIN";
    /** 预警类型：低于安全库存 */
    public static final String ALERT_TYPE_BELOW_SAFETY = "BELOW_SAFETY";
    /** 预警类型：超出最高库存 */
    public static final String ALERT_TYPE_ABOVE_MAX = "ABOVE_MAX";
    /** 预警级别：严重 */
    public static final String ALERT_LEVEL_CRITICAL = "CRITICAL";
    /** 预警级别：警告 */
    public static final String ALERT_LEVEL_WARNING = "WARNING";
    /** 预警级别：提示 */
    public static final String ALERT_LEVEL_INFO = "INFO";

    /** 基础数据访问映射器（Mapper） */
    private final WmStockAlertMapper baseMapper;
    /** 物料数据访问映射器（Mapper） */
    private final BdMaterialMapper materialMapper;
    /** 库存预警数据访问映射器（Mapper） */
    private final BdUnitOfMeasureMapper uomMapper;
    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 分页查询安全库存预警列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 安全库存预警分页列表
     */
    @Override
    public TableDataInfo<WmStockAlertVo> selectPageList(WmStockAlertBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStockAlert> lqw = buildQueryWrapper(bo);
        Page<WmStockAlertVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询安全库存预警列表
     *
     * @param bo 查询参数
     * @return 安全库存预警列表
     */
    @Override
    public List<WmStockAlertVo> selectList(WmStockAlertBo bo) {
        LambdaQueryWrapper<WmStockAlert> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据ID获取安全库存预警详情
     *
     * @param id 预警ID
     * @return 安全库存预警详情
     */
    @Override
    public WmStockAlertVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增安全库存预警
     *
     * @param bo 安全库存预警业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStockAlertBo bo) {
        WmStockAlert alert = MapstructUtils.convert(bo, WmStockAlert.class);
        return baseMapper.insert(alert);
    }

    /**
     * 修改安全库存预警
     *
     * @param bo 安全库存预警业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStockAlertBo bo) {
        WmStockAlert alert = MapstructUtils.convert(bo, WmStockAlert.class);
        return baseMapper.updateById(alert);
    }

    /**
     * 批量删除安全库存预警
     *
     * @param ids 预警ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行安全库存预警检查
     * <p>
     * 扫描所有设置了安全库存或最低库存的物料，根据当前库存生成预警记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkStockAlerts() {
        log.info("开始执行安全库存预警检查");
        List<BdMaterial> materials = materialMapper.selectList(
            new LambdaQueryWrapper<BdMaterial>()
                .eq(BdMaterial::getStatus, 1L)
                .and(w -> w.gt(BdMaterial::getSafetyStock, BigDecimal.ZERO)
                    .or().gt(BdMaterial::getMinStock, BigDecimal.ZERO)
                    .or().gt(BdMaterial::getMaxStock, BigDecimal.ZERO)));

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

        // 判断当前应触发的预警类型
        String currentAlertType = null;
        String currentAlertLevel = null;

        if (minStock.compareTo(BigDecimal.ZERO) > 0 && totalQty.compareTo(minStock) < 0) {
            currentAlertType = ALERT_TYPE_BELOW_MIN;
            currentAlertLevel = ALERT_LEVEL_CRITICAL;
        } else if (safetyStock.compareTo(BigDecimal.ZERO) > 0 && totalQty.compareTo(safetyStock) < 0) {
            currentAlertType = ALERT_TYPE_BELOW_SAFETY;
            currentAlertLevel = ALERT_LEVEL_WARNING;
        } else if (maxStock.compareTo(BigDecimal.ZERO) > 0 && totalQty.compareTo(maxStock) > 0) {
            currentAlertType = ALERT_TYPE_ABOVE_MAX;
            currentAlertLevel = ALERT_LEVEL_INFO;
        }

        // 自动关闭该物料不再适用的未处理预警
        List<WmStockAlert> existingAlerts = baseMapper.selectList(
            new LambdaQueryWrapper<WmStockAlert>()
                .eq(WmStockAlert::getMaterialId, material.getMaterialId())
                .eq(WmStockAlert::getHandledFlag, 0));

        if (existingAlerts != null) {
            for (WmStockAlert alert : existingAlerts) {
                if (!Objects.equals(alert.getAlertType(), currentAlertType)) {
                    alert.setHandledFlag(1);
                    alert.setHandledTime(now);
                    alert.setHandledRemark("库存状态变更，自动关闭");
                    alert.setUpdateTime(now);
                    baseMapper.updateById(alert);
                }
            }
        }

        int count = 0;
        if (currentAlertType != null) {
            createOrUpdateAlert(material, null, totalQty, safetyStock, minStock, maxStock,
                currentAlertType, currentAlertLevel, now);
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
            existing.setSafetyStock(safetyStock);
            existing.setMinStock(minStock);
            existing.setMaxStock(maxStock);
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

    /**
     * 处理安全库存预警
     *
     * @param alertId       预警ID
     * @param handledRemark 处理备注
     */
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

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return MyBatis查询包装器
     */
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

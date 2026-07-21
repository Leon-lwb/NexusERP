package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.service.IWmMaterialStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存清单Service业务层处理
 *
 * @author nexus
 * @date 2025-12-03
 */
@DS("erp")
@Service
public class WmMaterialStockServiceImpl extends ServiceImpl<WmMaterialStockMapper, WmMaterialStock> implements IWmMaterialStockService {

    /** 物料库存数据访问映射器（Mapper） */
    private final BdMaterialMapper bdMaterialMapper;

    /**
     * 物料库存处理（WmMaterialStockServiceImpl）
     *
     * @param bdMaterialMapper 参数 bdMaterialMapper
     * @return 结果
     */
    public WmMaterialStockServiceImpl(BdMaterialMapper bdMaterialMapper) {
        this.bdMaterialMapper = bdMaterialMapper;
    }

    /**
     * 分页查询库存清单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 库存清单分页列表
     */
    @Override
    public TableDataInfo<WmMaterialStockVo> selectPageList(WmMaterialStockBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialStock> lqw = buildQueryWrapper(bo);
        Page<WmMaterialStockVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询库存清单列表
     *
     * @param bo 查询参数
     * @return 库存清单列表
     */
    @Override
    public List<WmMaterialStockVo> selectList(WmMaterialStockBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取库存清单详情
     *
     * @param id 库存ID
     * @return 库存清单详情
     */
    @Override
    public WmMaterialStockVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增库存清单
     * <p>
     * 虚拟件物料不入库（虚拟件跳层，不产生实际库存）。
     *
     * @param bo 库存清单业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialStockBo bo) {
        // 虚拟件校验：虚拟件不入库
        if (bo.getMaterialId() != null) {
            BdMaterial material = bdMaterialMapper.selectById(bo.getMaterialId());
            if (material != null && Integer.valueOf(1).equals(material.getIsPhantom())) {
                throw new RuntimeException("物料[" + material.getMaterialCode() + "]为虚拟件，不入库");
            }
        }
        WmMaterialStock entity = MapstructUtils.convert(bo, WmMaterialStock.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改库存清单
     *
     * @param bo 库存清单业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialStockBo bo) {
        WmMaterialStock entity = MapstructUtils.convert(bo, WmMaterialStock.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除库存清单
     *
     * @param ids 库存ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据物料分类ID查询库存清单
     *
     * @param categoryIds 物料分类ID列表
     * @return 库存清单列表
     */
    @Override
    public List<WmMaterialStockVo> selectByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .in(WmMaterialStock::getCategoryId, categoryIds)
        );
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return MyBatis查询包装器
     */
    private LambdaQueryWrapper<WmMaterialStock> buildQueryWrapper(WmMaterialStockBo bo) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getCategoryId() != null, WmMaterialStock::getCategoryId, bo.getCategoryId());
        lqw.eq(bo.getMaterialId() != null, WmMaterialStock::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmMaterialStock::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmMaterialStock::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmMaterialStock::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(bo.getBaseUomId()), WmMaterialStock::getBaseUomId, bo.getBaseUomId());
        lqw.like(StringUtils.isNotBlank(bo.getBaseUomName()), WmMaterialStock::getBaseUomName, bo.getBaseUomName());
        lqw.eq(bo.getBatchId() != null, WmMaterialStock::getBatchId, bo.getBatchId());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmMaterialStock::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialStock::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmMaterialStock::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmMaterialStock::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getAreaId() != null, WmMaterialStock::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmMaterialStock::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmMaterialStock::getAreaName, bo.getAreaName());
        lqw.eq(bo.getLocationId() != null, WmMaterialStock::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmMaterialStock::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmMaterialStock::getLocationName, bo.getLocationName());
        lqw.eq(bo.getVendorId() != null, WmMaterialStock::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), WmMaterialStock::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmMaterialStock::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorNick()), WmMaterialStock::getVendorNick, bo.getVendorNick());
        lqw.eq(bo.getStockQuantity() != null, WmMaterialStock::getStockQuantity, bo.getStockQuantity());
        lqw.eq(bo.getWoId() != null, WmMaterialStock::getWoId, bo.getWoId());
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), WmMaterialStock::getWoNo, bo.getWoNo());
        lqw.eq(bo.getProductionDate() != null, WmMaterialStock::getProductionDate, bo.getProductionDate());
        lqw.eq(bo.getRecptDate() != null, WmMaterialStock::getRecptDate, bo.getRecptDate());
        lqw.eq(bo.getExpireDate() != null, WmMaterialStock::getExpireDate, bo.getExpireDate());
        lqw.eq(bo.getFrozenFlag() != null, WmMaterialStock::getFrozenFlag, bo.getFrozenFlag());
        return lqw;
    }
}

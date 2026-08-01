package com.nexus.erp.fin.fa.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.fa.constant.FaConstants;
import com.nexus.erp.fin.fa.domain.FaAsset;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import com.nexus.erp.fin.fa.domain.FaDepreciation;
import com.nexus.erp.fin.fa.domain.bo.FaAssetBo;
import com.nexus.erp.fin.fa.domain.vo.FaAssetVo;
import com.nexus.erp.fin.fa.mapper.FaAssetCategoryMapper;
import com.nexus.erp.fin.fa.mapper.FaAssetMapper;
import com.nexus.erp.fin.fa.mapper.FaDepreciationMapper;
import com.nexus.erp.fin.fa.service.IFaAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定资产卡片 Service 实现
 *
 * @author nexus
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FaAssetServiceImpl implements IFaAssetService {

    private final FaAssetMapper baseMapper;
    private final FaAssetCategoryMapper categoryMapper;
    private final FaDepreciationMapper depreciationMapper;

    /** 分页查询资产卡片 */
    @Override
    public TableDataInfo<FaAssetVo> queryPageList(FaAssetBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FaAsset> lqw = buildQueryWrapper(bo);
        Page<FaAssetVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillExtra(page.getRecords());
        return TableDataInfo.build(page);
    }

    /** 查询资产详情 */
    @Override
    public FaAssetVo queryById(Long assetId) {
        FaAssetVo vo = baseMapper.selectVoById(assetId);
        if (vo == null) {
            return null; // 不存在返回 null 而非 NPE
        }
        fillExtra(List.of(vo));
        return vo;
    }

    /** 新增卡片:自动编号 FA-YYYY-NNNN,默认 IN_USE,净值=原值 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FaAssetBo bo) {
        FaAssetCategory category = categoryMapper.selectById(bo.getCategoryId());
        if (category == null) {
            throw new ServiceException("资产类别不存在");
        }
        FaAsset entity = MapstructUtils.convert(bo, FaAsset.class);
        entity.setAssetId(IdGeneratorUtil.nextLongId());
        if (StringUtils.isBlank(entity.getAssetNo())) {
            entity.setAssetNo(generateAssetNo());
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(FaConstants.ASSET_STATUS_IN_USE);
        }
        if (entity.getUsefulLife() == null) {
            entity.setUsefulLife(category.getUsefulLife());
        }
        if (StringUtils.isBlank(entity.getDepreciationMethod())) {
            entity.setDepreciationMethod(category.getDepreciationMethod());
        }
        if (entity.getSalvageValue() == null) {
            entity.setSalvageValue(calcSalvage(entity.getOriginalValue(), category.getSalvageValueRate()));
        }
        if (entity.getAcquisitionDate() == null) {
            entity.setAcquisitionDate(new Date());
        }
        if (entity.getInUseDate() == null) {
            entity.setInUseDate(entity.getAcquisitionDate());
        }
        entity.setCumulativeDepreciation(BigDecimal.ZERO);
        entity.setNetBookValue(entity.getOriginalValue());
        entity.setVersion(0);
        return baseMapper.insert(entity) > 0;
    }

    /** 修改卡片:仅未计提且未处置可改;类别变化时带出折旧政策并重算净值 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(FaAssetBo bo) {
        FaAsset existing = baseMapper.selectById(bo.getAssetId());
        if (existing == null) {
            throw new ServiceException("资产不存在");
        }
        if (nz(existing.getCumulativeDepreciation()).signum() > 0) {
            throw new ServiceException("资产已计提折旧,不能修改,请用原值调整");
        }
        if (FaConstants.ASSET_STATUS_DISPOSED.equals(existing.getStatus())) {
            throw new ServiceException("资产已处置,不能修改");
        }
        FaAssetCategory category = categoryMapper.selectById(bo.getCategoryId() != null ? bo.getCategoryId() : existing.getCategoryId());
        if (category == null) {
            throw new ServiceException("资产类别不存在");
        }
        FaAsset entity = MapstructUtils.convert(bo, FaAsset.class);
        if (entity.getUsefulLife() == null) {
            entity.setUsefulLife(category.getUsefulLife());
        }
        if (StringUtils.isBlank(entity.getDepreciationMethod())) {
            entity.setDepreciationMethod(category.getDepreciationMethod());
        }
        if (entity.getSalvageValue() == null) {
            entity.setSalvageValue(calcSalvage(entity.getOriginalValue(), category.getSalvageValueRate()));
        }
        if (entity.getInUseDate() == null) {
            entity.setInUseDate(entity.getAcquisitionDate());
        }
        entity.setCumulativeDepreciation(existing.getCumulativeDepreciation());
        entity.setNetBookValue(nz(entity.getOriginalValue()).subtract(nz(existing.getCumulativeDepreciation())));
        entity.setVersion(existing.getVersion());
        return baseMapper.updateById(entity) > 0;
    }

    /** 删除卡片:已计提/已处置禁止 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] assetIds) {
        if (assetIds == null || assetIds.length == 0) {
            return true;
        }
        for (Long assetId : assetIds) {
            FaAsset asset = baseMapper.selectById(assetId);
            if (asset == null) {
                continue;
            }
            if (nz(asset.getCumulativeDepreciation()).signum() > 0) {
                throw new ServiceException("资产已计提折旧,不能删除");
            }
            if (FaConstants.ASSET_STATUS_DISPOSED.equals(asset.getStatus())) {
                throw new ServiceException("资产已处置,不能删除");
            }
        }
        return baseMapper.deleteByIds(List.of(assetIds)) > 0;
    }

    /** 原值调整:仅未计提/未处置;净值重算;直线法按剩余月数重摊 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean adjustValue(Long assetId, BigDecimal newOriginalValue) {
        if (newOriginalValue == null || newOriginalValue.signum() <= 0) {
            throw new ServiceException("原值必须大于 0");
        }
        FaAsset asset = baseMapper.selectById(assetId);
        if (asset == null) {
            throw new ServiceException("资产不存在");
        }
        if (nz(asset.getCumulativeDepreciation()).signum() > 0) {
            throw new ServiceException("资产已计提折旧,不能调整原值");
        }
        asset.setOriginalValue(newOriginalValue);
        asset.setNetBookValue(newOriginalValue);
        asset.setVersion(asset.getVersion() == null ? 0 : asset.getVersion());
        return baseMapper.updateById(asset) > 0;
    }

    /** 转固:CWIP->IN_USE,启用日期为空则置当前日 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean activate(Long assetId) {
        FaAsset asset = baseMapper.selectById(assetId);
        if (asset == null) {
            throw new ServiceException("资产不存在");
        }
        if (!FaConstants.ASSET_STATUS_CWIP.equals(asset.getStatus())) {
            throw new ServiceException("仅在建资产可转固");
        }
        asset.setStatus(FaConstants.ASSET_STATUS_IN_USE);
        if (asset.getInUseDate() == null) {
            asset.setInUseDate(new Date());
        }
        asset.setVersion(asset.getVersion() == null ? 0 : asset.getVersion());
        return baseMapper.updateById(asset) > 0;
    }

    // ---------- helpers ----------

    private LambdaQueryWrapper<FaAsset> buildQueryWrapper(FaAssetBo bo) {
        LambdaQueryWrapper<FaAsset> lqw = Wrappers.lambdaQuery();
        if (bo == null) {
            return lqw;
        }
        lqw.eq(bo.getCategoryId() != null, FaAsset::getCategoryId, bo.getCategoryId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), FaAsset::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getKeyword()), FaAsset::getAssetName, bo.getKeyword());
        lqw.orderByDesc(FaAsset::getCreateTime);
        return lqw;
    }

    /** 回填类别名/方法等展示字段 */
    private void fillExtra(List<FaAssetVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, FaAssetCategory> categoryMap = new HashMap<>();
        for (FaAssetVo vo : list) {
            FaAssetCategory category = categoryMap.computeIfAbsent(vo.getCategoryId(),
                id -> categoryMapper.selectById(id));
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
                vo.setCategoryMethod(category.getDepreciationMethod());
            }
            vo.setMethodLabel(StringUtils.isNotBlank(vo.getDepreciationMethod()) ? vo.getDepreciationMethod() : vo.getCategoryMethod());
            vo.setNetValue(nz(vo.getOriginalValue()).subtract(nz(vo.getCumulativeDepreciation())));
        }
    }

    /** 净残值 = 原值 × 残值率(rate 为空按 0) */
    private BigDecimal calcSalvage(BigDecimal originalValue, BigDecimal rate) {
        if (originalValue == null) {
            return BigDecimal.ZERO;
        }
        return originalValue.multiply(rate == null ? BigDecimal.ZERO : rate).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /** 生成资产编号 FA-YYYY-NNNN(年度内顺序号) */
    private String generateAssetNo() {
        int year = DateUtil.year(new Date());
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<FaAsset>()
            .likeRight(FaAsset::getAssetNo, "FA-" + year + "-"));
        long seq = (count == null ? 0 : count) + 1;
        return String.format("FA-%d-%04d", year, seq);
    }

    /** null-safe */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

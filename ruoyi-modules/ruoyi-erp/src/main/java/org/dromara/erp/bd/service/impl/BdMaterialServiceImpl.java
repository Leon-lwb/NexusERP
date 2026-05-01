package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.domain.bo.BdMaterialBo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;
import org.dromara.erp.bd.mapper.BdMaterialMapper;
import org.dromara.erp.bd.service.IBdMaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialServiceImpl implements IBdMaterialService {

    private final BdMaterialMapper baseMapper;

    @Override
    public TableDataInfo<BdMaterialVo> selectPageList(BdMaterialBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterial> lqw = buildQueryWrapper(bo);
        Page<BdMaterialVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdMaterialVo> selectList(BdMaterialBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdMaterialVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialBo bo) {
        BdMaterial entity = MapstructUtils.convert(bo, BdMaterial.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialBo bo) {
        BdMaterial entity = MapstructUtils.convert(bo, BdMaterial.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public List<BdMaterialVo> selectByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        return baseMapper.selectByCategoryIds(categoryIds);
    }

    private LambdaQueryWrapper<BdMaterial> buildQueryWrapper(BdMaterialBo bo) {
        LambdaQueryWrapper<BdMaterial> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), BdMaterial::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), BdMaterial::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), BdMaterial::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(bo.getMaterialType() != null, BdMaterial::getMaterialType, bo.getMaterialType());
        lqw.eq(bo.getSourceType() != null, BdMaterial::getSourceType, bo.getSourceType());
        lqw.eq(bo.getCategoryId() != null, BdMaterial::getCategoryId, bo.getCategoryId());
        lqw.eq(bo.getStatus() != null, BdMaterial::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getQualityStatus()), BdMaterial::getQualityStatus, bo.getQualityStatus());
        return lqw;
    }
}

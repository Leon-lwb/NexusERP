package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.bo.WmStorageAreaBo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.service.IWmStorageAreaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 库区Service业务层处理
 *
 * @author nexus
 * @date 2025-12-01
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStorageAreaServiceImpl implements IWmStorageAreaService {

    private final WmStorageAreaMapper baseMapper;

    @Override
    public TableDataInfo<WmStorageAreaVo> selectPageList(WmStorageAreaBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStorageArea> lqw = buildQueryWrapper(bo);
        Page<WmStorageAreaVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStorageAreaVo> selectList(WmStorageAreaBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmStorageAreaVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStorageAreaBo bo) {
        WmStorageArea entity = MapstructUtils.convert(bo, WmStorageArea.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStorageAreaBo bo) {
        WmStorageArea entity = MapstructUtils.convert(bo, WmStorageArea.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmStorageArea> buildQueryWrapper(WmStorageAreaBo bo) {
        LambdaQueryWrapper<WmStorageArea> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmStorageArea::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmStorageArea::getAreaName, bo.getAreaName());
        lqw.eq(bo.getWarehouseId() != null, WmStorageArea::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getEnableFlag() != null, WmStorageArea::getEnableFlag, bo.getEnableFlag());
        lqw.eq(StringUtils.isNotBlank(bo.getLayoutType()), WmStorageArea::getLayoutType, bo.getLayoutType());
        return lqw;
    }
}

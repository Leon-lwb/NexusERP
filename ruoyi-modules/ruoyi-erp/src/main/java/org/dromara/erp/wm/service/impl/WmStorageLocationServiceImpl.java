package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmStorageLocation;
import org.dromara.erp.wm.domain.bo.WmStorageLocationBo;
import org.dromara.erp.wm.domain.vo.WmStorageLocationVo;
import org.dromara.erp.wm.mapper.WmStorageLocationMapper;
import org.dromara.erp.wm.service.IWmStorageLocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 库位Service业务层处理
 *
 * @author ruoyi
 * @date 2025-12-02
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStorageLocationServiceImpl implements IWmStorageLocationService {

    private final WmStorageLocationMapper baseMapper;

    @Override
    public TableDataInfo<WmStorageLocationVo> selectPageList(WmStorageLocationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStorageLocation> lqw = buildQueryWrapper(bo);
        Page<WmStorageLocationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStorageLocationVo> selectList(WmStorageLocationBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmStorageLocationVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStorageLocationBo bo) {
        WmStorageLocation entity = MapstructUtils.convert(bo, WmStorageLocation.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStorageLocationBo bo) {
        WmStorageLocation entity = MapstructUtils.convert(bo, WmStorageLocation.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmStorageLocation> buildQueryWrapper(WmStorageLocationBo bo) {
        LambdaQueryWrapper<WmStorageLocation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmStorageLocation::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmStorageLocation::getLocationName, bo.getLocationName());
        lqw.eq(bo.getAreaId() != null, WmStorageLocation::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationType()), WmStorageLocation::getLocationType, bo.getLocationType());
        lqw.eq(bo.getStatus() != null, WmStorageLocation::getStatus, bo.getStatus());
        lqw.eq(bo.getEnableFlag() != null, WmStorageLocation::getEnableFlag, bo.getEnableFlag());
        return lqw;
    }
}

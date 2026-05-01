package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmMaterialReceiveDetail;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import org.dromara.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import org.dromara.erp.wm.service.IWmMaterialReceiveDetailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 物料入库单明细Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMaterialReceiveDetailServiceImpl implements IWmMaterialReceiveDetailService {

    private final WmMaterialReceiveDetailMapper baseMapper;

    @Override
    public TableDataInfo<WmMaterialReceiveDetailVo> selectPageList(WmMaterialReceiveDetailBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceiveDetail> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveDetailVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMaterialReceiveDetailVo> selectList(WmMaterialReceiveDetailBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMaterialReceiveDetailVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveDetailBo bo) {
        WmMaterialReceiveDetail entity = MapstructUtils.convert(bo, WmMaterialReceiveDetail.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveDetailBo bo) {
        WmMaterialReceiveDetail entity = MapstructUtils.convert(bo, WmMaterialReceiveDetail.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmMaterialReceiveDetail> buildQueryWrapper(WmMaterialReceiveDetailBo bo) {
        LambdaQueryWrapper<WmMaterialReceiveDetail> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getLineId() != null, WmMaterialReceiveDetail::getLineId, bo.getLineId());
        lqw.eq(bo.getReceiveId() != null, WmMaterialReceiveDetail::getReceiveId, bo.getReceiveId());
        lqw.eq(bo.getMaterialId() != null, WmMaterialReceiveDetail::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmMaterialReceiveDetail::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmMaterialReceiveDetail::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialReceiveDetail::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getAreaId() != null, WmMaterialReceiveDetail::getAreaId, bo.getAreaId());
        lqw.eq(bo.getLocationId() != null, WmMaterialReceiveDetail::getLocationId, bo.getLocationId());
        return lqw;
    }
}

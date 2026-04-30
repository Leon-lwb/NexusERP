package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmMaterialReceiveLine;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import org.dromara.erp.wm.mapper.WmMaterialReceiveLineMapper;
import org.dromara.erp.wm.service.IWmMaterialReceiveLineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 物料入库单行Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
public class WmMaterialReceiveLineServiceImpl implements IWmMaterialReceiveLineService {

    private final WmMaterialReceiveLineMapper baseMapper;

    @Override
    public TableDataInfo<WmMaterialReceiveLineVo> selectPageList(WmMaterialReceiveLineBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceiveLine> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveLineVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMaterialReceiveLineVo> selectList(WmMaterialReceiveLineBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMaterialReceiveLineVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveLineBo bo) {
        WmMaterialReceiveLine entity = MapstructUtils.convert(bo, WmMaterialReceiveLine.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveLineBo bo) {
        WmMaterialReceiveLine entity = MapstructUtils.convert(bo, WmMaterialReceiveLine.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmMaterialReceiveLine> buildQueryWrapper(WmMaterialReceiveLineBo bo) {
        LambdaQueryWrapper<WmMaterialReceiveLine> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getReceiveId() != null, WmMaterialReceiveLine::getReceiveId, bo.getReceiveId());
        lqw.eq(bo.getMaterialId() != null, WmMaterialReceiveLine::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmMaterialReceiveLine::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmMaterialReceiveLine::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmMaterialReceiveLine::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmMaterialReceiveLine::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialReceiveLine::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getAreaId() != null, WmMaterialReceiveLine::getAreaId, bo.getAreaId());
        lqw.eq(bo.getLocationId() != null, WmMaterialReceiveLine::getLocationId, bo.getLocationId());
        return lqw;
    }
}

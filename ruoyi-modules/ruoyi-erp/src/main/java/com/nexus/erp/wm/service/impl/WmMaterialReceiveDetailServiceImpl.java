package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.service.IWmMaterialReceiveDetailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 物料入库单明细Service业务层处理
 *
 * @author nexus
 */
@DS("erp")
@Service
public class WmMaterialReceiveDetailServiceImpl extends ServiceImpl<WmMaterialReceiveDetailMapper, WmMaterialReceiveDetail> implements IWmMaterialReceiveDetailService {

    /**
     * 分页查询物料领用明细列表
     *
     * @param bo 物料领用明细业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmMaterialReceiveDetailVo> selectPageList(WmMaterialReceiveDetailBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceiveDetail> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveDetailVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料领用明细列表
     *
     * @param bo 物料领用明细业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmMaterialReceiveDetailVo> selectList(WmMaterialReceiveDetailBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询物料领用明细
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmMaterialReceiveDetailVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增物料领用明细
     *
     * @param bo 物料领用明细业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveDetailBo bo) {
        WmMaterialReceiveDetail entity = MapstructUtils.convert(bo, WmMaterialReceiveDetail.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改物料领用明细
     *
     * @param bo 物料领用明细业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveDetailBo bo) {
        WmMaterialReceiveDetail entity = MapstructUtils.convert(bo, WmMaterialReceiveDetail.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 删除物料领用明细
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
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

package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.service.IWmMaterialReceiveLineService;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 物料入库单行Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMaterialReceiveLineServiceImpl
        extends ServiceImpl<WmMaterialReceiveLineMapper, WmMaterialReceiveLine>
        implements IWmMaterialReceiveLineService {

    private final WmCertifiedReceiptMutationGuard certifiedReceiptGuard;

    /**
     * 分页查询物料领用行列表
     *
     * @param bo 物料领用行业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmMaterialReceiveLineVo> selectPageList(WmMaterialReceiveLineBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceiveLine> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveLineVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料领用行列表
     *
     * @param bo 物料领用行业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmMaterialReceiveLineVo> selectList(WmMaterialReceiveLineBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询物料领用行
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmMaterialReceiveLineVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增物料领用行
     *
     * @param bo 物料领用行业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveLineBo bo) {
        certifiedReceiptGuard.requireMutableLineParent(bo.getReceiveId());
        WmMaterialReceiveLine entity = MapstructUtils.convert(bo, WmMaterialReceiveLine.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改物料领用行
     *
     * @param bo 物料领用行业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveLineBo bo) {
        certifiedReceiptGuard.requireMutableLine(bo.getLineId(),bo.getReceiveId());
        WmMaterialReceiveLine entity = MapstructUtils.convert(bo, WmMaterialReceiveLine.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 删除物料领用行
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for(Long id:ids) { certifiedReceiptGuard.requireMutableLine(id,null); }
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

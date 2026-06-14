package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVendorVo;
import com.nexus.erp.bd.mapper.BdMaterialVendorMapper;
import com.nexus.erp.bd.service.IBdMaterialVendorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 物料-供应商关系Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialVendorServiceImpl implements IBdMaterialVendorService {

    private final BdMaterialVendorMapper baseMapper;

    /**
     * 分页查询物料-供应商关系列表
     */
    @Override
    public TableDataInfo<BdMaterialVendorVo> selectPageList(BdMaterialVendorBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterialVendor> lqw = buildQueryWrapper(bo);
        Page<BdMaterialVendorVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料-供应商关系列表（不分页）
     */
    @Override
    public List<BdMaterialVendorVo> selectList(BdMaterialVendorBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询物料-供应商关系
     */
    @Override
    public BdMaterialVendorVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增物料-供应商关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialVendorBo bo) {
        BdMaterialVendor entity = MapstructUtils.convert(bo, BdMaterialVendor.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改物料-供应商关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialVendorBo bo) {
        BdMaterialVendor entity = MapstructUtils.convert(bo, BdMaterialVendor.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除物料-供应商关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<BdMaterialVendor> buildQueryWrapper(BdMaterialVendorBo bo) {
        LambdaQueryWrapper<BdMaterialVendor> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getMaterialId() != null, BdMaterialVendor::getMaterialId, bo.getMaterialId());
        lqw.eq(bo.getVendorId() != null, BdMaterialVendor::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorCode()), BdMaterialVendor::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), BdMaterialVendor::getVendorName, bo.getVendorName());
        lqw.eq(bo.getStatus() != null, BdMaterialVendor::getStatus, bo.getStatus());
        lqw.orderByAsc(BdMaterialVendor::getPriority);
        return lqw;
    }
}

package com.nexus.iot.device.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotProduct;
import com.nexus.iot.device.domain.bo.IotProductBo;
import com.nexus.iot.device.domain.vo.IotProductVo;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.service.IIotProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IoT产品Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotProductServiceImpl implements IIotProductService {

    private final IotProductMapper baseMapper;

    @Override
    public TableDataInfo<IotProductVo> selectPageList(IotProductBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<IotProduct> lqw = buildQueryWrapper(bo);
        Page<IotProductVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<IotProductVo> selectList(IotProductBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public IotProductVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotProductBo bo) {
        IotProduct entity = MapstructUtils.convert(bo, IotProduct.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotProductBo bo) {
        IotProduct entity = MapstructUtils.convert(bo, IotProduct.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<IotProduct> buildQueryWrapper(IotProductBo bo) {
        LambdaQueryWrapper<IotProduct> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getProductCode()), IotProduct::getProductCode, bo.getProductCode());
        lqw.like(StringUtils.isNotBlank(bo.getProductName()), IotProduct::getProductName, bo.getProductName());
        lqw.eq(StringUtils.isNotBlank(bo.getProductType()), IotProduct::getProductType, bo.getProductType());
        lqw.eq(StringUtils.isNotBlank(bo.getProtocolType()), IotProduct::getProtocolType, bo.getProtocolType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotProduct::getStatus, bo.getStatus());
        return lqw;
    }
}

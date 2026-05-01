package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdWorkshop;
import org.dromara.erp.bd.domain.bo.BdWorkshopBo;
import org.dromara.erp.bd.domain.vo.BdWorkshopVo;
import org.dromara.erp.bd.mapper.BdWorkshopMapper;
import org.dromara.erp.bd.service.IBdWorkshopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

@RequiredArgsConstructor
@DS("erp")
@Service
public class BdWorkshopServiceImpl implements IBdWorkshopService {

    private final BdWorkshopMapper baseMapper;

    @Override
    public TableDataInfo<BdWorkshopVo> selectPageList(BdWorkshopBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdWorkshop> lqw = buildQueryWrapper(bo);
        Page<BdWorkshopVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdWorkshopVo> selectList(BdWorkshopBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdWorkshopVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdWorkshopBo bo) {
        BdWorkshop entity = MapstructUtils.convert(bo, BdWorkshop.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdWorkshopBo bo) {
        BdWorkshop entity = MapstructUtils.convert(bo, BdWorkshop.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdWorkshop> buildQueryWrapper(BdWorkshopBo bo) {
        LambdaQueryWrapper<BdWorkshop> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getWorkshopCode()), BdWorkshop::getWorkshopCode, bo.getWorkshopCode());
        lqw.like(StringUtils.isNotBlank(bo.getWorkshopName()), BdWorkshop::getWorkshopName, bo.getWorkshopName());
        lqw.eq(bo.getStatus() != null, BdWorkshop::getStatus, bo.getStatus());
        lqw.orderByAsc(BdWorkshop::getSortOrder);
        return lqw;
    }
}

package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.domain.bo.BdWorkstationBo;
import com.nexus.erp.bd.domain.vo.BdWorkstationVo;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.bd.service.IBdWorkstationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 工作中心Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdWorkstationServiceImpl implements IBdWorkstationService {

    private final BdWorkstationMapper baseMapper;

    @Override
    public TableDataInfo<BdWorkstationVo> selectPageList(BdWorkstationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdWorkstation> lqw = buildQueryWrapper(bo);
        Page<BdWorkstationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdWorkstationVo> selectList(BdWorkstationBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdWorkstationVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdWorkstationBo bo) {
        BdWorkstation entity = MapstructUtils.convert(bo, BdWorkstation.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdWorkstationBo bo) {
        BdWorkstation entity = MapstructUtils.convert(bo, BdWorkstation.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdWorkstation> buildQueryWrapper(BdWorkstationBo bo) {
        LambdaQueryWrapper<BdWorkstation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getWorkstationCode()), BdWorkstation::getWorkstationCode, bo.getWorkstationCode());
        lqw.like(StringUtils.isNotBlank(bo.getWorkstationName()), BdWorkstation::getWorkstationName, bo.getWorkstationName());
        lqw.eq(bo.getWorkshopId() != null, BdWorkstation::getWorkshopId, bo.getWorkshopId());
        lqw.eq(bo.getStatus() != null, BdWorkstation::getStatus, bo.getStatus());
        return lqw;
    }
}

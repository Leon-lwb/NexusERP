package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdBom;
import org.dromara.erp.bd.domain.bo.BdBomBo;
import org.dromara.erp.bd.domain.vo.BdBomVo;
import org.dromara.erp.bd.mapper.BdBomMapper;
import org.dromara.erp.bd.service.IBdBomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BdBomServiceImpl implements IBdBomService {

    private final BdBomMapper baseMapper;

    @Override
    public TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdBom> lqw = buildQueryWrapper(bo);
        Page<BdBomVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdBomVo> selectList(BdBomBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdBomVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdBomBo bo) {
        BdBom entity = MapstructUtils.convert(bo, BdBom.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdBomBo bo) {
        BdBom entity = MapstructUtils.convert(bo, BdBom.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdBom> buildQueryWrapper(BdBomBo bo) {
        LambdaQueryWrapper<BdBom> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getBomVersion()), BdBom::getBomVersion, bo.getBomVersion());
        lqw.like(StringUtils.isNotBlank(bo.getBomName()), BdBom::getBomName, bo.getBomName());
        lqw.eq(bo.getParentMaterialId() != null, BdBom::getParentMaterialId, bo.getParentMaterialId());
        lqw.eq(bo.getIsCurrent() != null, BdBom::getIsCurrent, bo.getIsCurrent());
        lqw.eq(bo.getStatus() != null, BdBom::getStatus, bo.getStatus());
        return lqw;
    }
}

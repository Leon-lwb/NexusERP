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
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdBomDetailBo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.IBdBomDetailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BOM明细Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdBomDetailServiceImpl implements IBdBomDetailService {

    private final BdBomDetailMapper baseMapper;
    private final BdMaterialMapper materialMapper;

    @Override
    public TableDataInfo<BdBomDetailVo> selectPageList(BdBomDetailBo bo, PageQuery pageQuery) {
        Page<BdBomDetailVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        page.setRecords(enrichMaterialName(page.getRecords()));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdBomDetailVo> selectList(BdBomDetailBo bo) {
        return enrichMaterialName(baseMapper.selectVoList(buildQueryWrapper(bo)));
    }

    @Override
    public BdBomDetailVo selectById(Long id) {
        List<BdBomDetailVo> list = enrichMaterialName(List.of(baseMapper.selectVoById(id)));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdBomDetailBo bo) {
        BdBomDetail entity = MapstructUtils.convert(bo, BdBomDetail.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdBomDetailBo bo) {
        BdBomDetail entity = MapstructUtils.convert(bo, BdBomDetail.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.delete(Wrappers.<BdBomDetail>lambdaQuery().in(BdBomDetail::getDetailId, List.of(ids)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveBatch(List<BdBomDetailBo> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Long bomId = list.get(0).getBomId();
        baseMapper.delete(Wrappers.<BdBomDetail>lambdaQuery().eq(BdBomDetail::getBomId, bomId));
        List<BdBomDetail> entities = list.stream().map(bo -> {
            BdBomDetail entity = MapstructUtils.convert(bo, BdBomDetail.class);
            entity.setDetailId(null);
            return entity;
        }).toList();
        entities.forEach(baseMapper::insert);
        return entities.size();
    }

    private LambdaQueryWrapper<BdBomDetail> buildQueryWrapper(BdBomDetailBo bo) {
        LambdaQueryWrapper<BdBomDetail> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getBomId() != null, BdBomDetail::getBomId, bo.getBomId());
        lqw.eq(bo.getChildMaterialId() != null, BdBomDetail::getChildMaterialId, bo.getChildMaterialId());
        lqw.eq(bo.getOperationStep() != null, BdBomDetail::getOperationStep, bo.getOperationStep());
        lqw.eq(bo.getWorkstationId() != null, BdBomDetail::getWorkstationId, bo.getWorkstationId());
        lqw.eq(bo.getStatus() != null, BdBomDetail::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getRemark()), BdBomDetail::getRemark, bo.getRemark());
        return lqw;
    }

    private List<BdBomDetailVo> enrichMaterialName(List<BdBomDetailVo> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        List<Long> materialIds = list.stream()
            .map(BdBomDetailVo::getChildMaterialId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (materialIds.isEmpty()) {
            return list;
        }
        Map<Long, String> materialNameMap = materialMapper.selectVoList(Wrappers.<BdMaterial>lambdaQuery()
                .select(BdMaterial::getMaterialId, BdMaterial::getMaterialName)
                .in(BdMaterial::getMaterialId, materialIds))
            .stream()
            .collect(Collectors.toMap(BdMaterialVo::getMaterialId, BdMaterialVo::getMaterialName, (a, b) -> a, HashMap::new));
        list.forEach(item -> item.setChildMaterialName(materialNameMap.get(item.getChildMaterialId())));
        return list;
    }
}

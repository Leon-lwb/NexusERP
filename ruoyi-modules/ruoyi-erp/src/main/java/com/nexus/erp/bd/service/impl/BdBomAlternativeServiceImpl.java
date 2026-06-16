package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.bd.domain.BdBomAlternative;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdBomAlternativeBo;
import com.nexus.erp.bd.domain.vo.BdBomAlternativeVo;
import com.nexus.erp.bd.mapper.BdBomAlternativeMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.IBdBomAlternativeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BOM替代料Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdBomAlternativeServiceImpl implements IBdBomAlternativeService {

    private final BdBomAlternativeMapper baseMapper;
    private final BdMaterialMapper materialMapper;

    @Override
    public List<BdBomAlternativeVo> listByDetailId(Long detailId) {
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getDetailId, detailId)
                .orderByAsc(BdBomAlternative::getPriority));
    }

    @Override
    public List<BdBomAlternativeVo> listByBomId(Long bomId) {
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getBomId, bomId)
                .orderByAsc(BdBomAlternative::getPriority));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveBatch(Long detailId, Long bomId, List<BdBomAlternativeBo> list) {
        // 先删除该明细行的现有替代料
        baseMapper.delete(Wrappers.lambdaQuery(BdBomAlternative.class)
            .eq(BdBomAlternative::getDetailId, detailId));
        // 批量插入
        if (list == null || list.isEmpty()) {
            return 0;
        }
        List<BdBomAlternative> entities = list.stream().map(bo -> {
            BdBomAlternative entity = MapstructUtils.convert(bo, BdBomAlternative.class);
            entity.setAlternativeId(null);
            entity.setDetailId(detailId);
            entity.setBomId(bomId);
            return entity;
        }).collect(Collectors.toList());
        entities.forEach(baseMapper::insert);
        return entities.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public List<BdBomAlternativeVo> getAvailableAlternatives(Long detailId) {
        LocalDate today = LocalDate.now();
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getDetailId, detailId)
                .eq(BdBomAlternative::getStatus, 1)
                .and(w -> w.isNull(BdBomAlternative::getEffectiveDate)
                    .or()
                    .le(BdBomAlternative::getEffectiveDate, today))
                .and(w -> w.isNull(BdBomAlternative::getExpiryDate)
                    .or()
                    .ge(BdBomAlternative::getExpiryDate, today))
                .orderByAsc(BdBomAlternative::getPriority));
    }
}
